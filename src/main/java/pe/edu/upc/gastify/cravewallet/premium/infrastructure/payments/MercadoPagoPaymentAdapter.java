package pe.edu.upc.gastify.cravewallet.premium.infrastructure.payments;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class MercadoPagoPaymentAdapter implements PremiumPaymentGateway {
    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoPaymentAdapter.class);
    private static final Set<String> SAFE_ERROR_CODES = Set.of(
            "bad_request", "unauthorized", "forbidden", "unprocessable_entity", "validation_error",
            "invalid_token", "invalid_access_token", "invalid_credentials", "invalid_payer", "invalid_payer_email",
            "invalid_users", "invalid_currency", "invalid_currency_id", "invalid_transaction_amount", "invalid_back_url",
            "invalid_request", "access_denied", "invalid_parameter", "invalid_params");

    private final RestClient client;
    private final String accessToken;
    private final String backUrl;
    private final boolean webhookConfigured;

    @Autowired
    public MercadoPagoPaymentAdapter(RestClient.Builder builder,
                                    @Value("${MERCADOPAGO_ACCESS_TOKEN:}") String accessToken,
                                    @Value("${MERCADOPAGO_BACK_URL:}") String backUrl,
                                    @Value("${MERCADOPAGO_WEBHOOK_SECRET:}") String webhookSecret) {
        this(builder.baseUrl("https://api.mercadopago.com").requestFactory(requestFactory()).build(),
                accessToken, backUrl, webhookSecret);
    }

    MercadoPagoPaymentAdapter(RestClient client, String accessToken, String backUrl, String webhookSecret) {
        this.client = client;
        this.accessToken = accessToken;
        this.backUrl = backUrl;
        this.webhookConfigured = !webhookSecret.isBlank();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(3500);
        return factory;
    }

    @Override
    public void checkCheckoutConfiguration() {
        if (accessToken.isBlank() || !webhookConfigured || !httpsUrl(backUrl)) {
            throw new PremiumFailure(503, "premium.config.checkout_missing",
                    "Mercado Pago access token, webhook, or HTTPS return URL is not configured.");
        }
    }

    @Override
    public Agreement createMonthlyCheckout(UUID reference, String payerEmail) {
        checkCheckoutConfiguration();
        var body = Map.of("reason", "CraveWallet Premium mensual", "external_reference", reference.toString(),
                "payer_email", payerEmail, "back_url", backUrl, "status", "pending",
                "auto_recurring", Map.of("frequency", 1, "frequency_type", "months",
                        "transaction_amount", MONTHLY_PRICE, "currency_id", CURRENCY));
        return agreement(exchange(client.post().uri("/preapproval").body(body), false, CheckoutCall.CREATE)
                .orElseThrow(MercadoPagoPaymentAdapter::invalidResponse));
    }

    @Override
    public Optional<Agreement> findCheckout(UUID reference, String payerEmail) {
        return exchange(client.get().uri("/preapproval/search?payer_email={email}", payerEmail), true,
                CheckoutCall.RECOVER_SEARCH).flatMap(body -> {
            JsonNode results = body.path("results");
            if (!results.isArray()) throw invalidResponse();
            String matchedId = null;
            for (JsonNode result : results) {
                if (!reference.toString().equals(text(result, "external_reference"))) continue;
                if (matchedId != null) throw invalidResponse();
                matchedId = requiredId(result, "id");
            }
            return matchedId == null ? Optional.empty()
                    : exchange(client.get().uri("/preapproval/{id}", matchedId), true, CheckoutCall.RECOVER_AGREEMENT)
                            .map(this::agreement);
        });
    }

    @Override
    public Optional<Agreement> findAgreement(String id) {
        return read(client.get().uri("/preapproval/{id}", id)).map(this::agreement);
    }

    @Override
    public void cancelAgreement(String id) {
        required(client.put().uri("/preapproval/{id}", id).body(Map.of("status", "canceled")));
    }

    @Override
    public Optional<Invoice> findInvoice(String id) {
        return read(client.get().uri("/authorized_payments/{id}", id)).map(this::invoice);
    }

    @Override
    public Optional<Invoice> findInvoiceByPayment(String paymentId) {
        return read(client.get().uri("/authorized_payments/search?payment_id={id}", paymentId)).flatMap(body -> {
            JsonNode results = body.path("results");
            if (!results.isArray()) throw invalidResponse();
            Invoice match = null;
            for (JsonNode result : results) {
                if (!paymentId.equals(result.path("payment").path("id").asText())) continue;
                if (match != null) throw invalidResponse();
                match = invoice(result);
            }
            return Optional.ofNullable(match);
        });
    }

    @Override
    public Optional<Payment> findPayment(String id) {
        return read(client.get().uri("/v1/payments/{id}", id)).map(body -> {
            JsonNode live = body.path("live_mode");
            return new Payment(requiredId(body, "id"), decimal(body, "transaction_amount"),
                    text(body, "currency_id"), text(body, "status"), date(body, "date_approved"),
                    live.isBoolean() ? live.booleanValue() : null, decimal(body, "transaction_amount_refunded"));
        });
    }

    private Agreement agreement(JsonNode body) {
        var recurring = body.path("auto_recurring");
        UUID reference;
        try {
            reference = UUID.fromString(body.path("external_reference").asText());
        } catch (IllegalArgumentException invalid) {
            reference = null;
        }
        Instant modifiedAt = date(body, "last_modified");
        // Preapproval responses can omit version. Use the provider modification time
        // consistently when present so out-of-order notifications cannot regress state.
        long revision = modifiedAt != null ? modifiedAt.toEpochMilli() : body.path("version").asLong(-1);
        return new Agreement(requiredId(body, "id"), reference, text(body, "init_point"), text(body, "status"),
                revision, decimal(recurring, "transaction_amount"),
                text(recurring, "currency_id"), recurring.path("frequency").asInt(),
                text(recurring, "frequency_type"), text(body, "preapproval_plan_id"), modifiedAt);
    }

    private Invoice invoice(JsonNode body) {
        return new Invoice(requiredId(body, "id"), requiredId(body, "preapproval_id"),
                text(body.path("payment"), "id"), decimal(body, "transaction_amount"),
                text(body, "currency_id"), date(body, "debit_date"));
    }

    private JsonNode required(RestClient.RequestHeadersSpec<?> request) {
        return exchange(request, false, null).orElseThrow(MercadoPagoPaymentAdapter::invalidResponse);
    }

    private Optional<JsonNode> read(RestClient.RequestHeadersSpec<?> request) {
        return exchange(request, true, null);
    }

    private Optional<JsonNode> exchange(RestClient.RequestHeadersSpec<?> request, boolean allowMissing, CheckoutCall checkoutCall) {
        if (accessToken.isBlank())
            throw new PremiumFailure(503, "premium.config.access_token_missing",
                    "Mercado Pago access token is not configured.");
        try {
            JsonNode response = request.headers(headers -> headers.setBearerAuth(accessToken)).retrieve()
                    .onStatus(status -> status.is3xxRedirection(), (sent, received) -> { throw invalidResponse(); })
                    .body(JsonNode.class);
            if (response == null || !response.isObject()) throw invalidResponse();
            return Optional.of(response);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            if (allowMissing && status == 404) return Optional.empty();
            if (checkoutCall != null) logCheckoutRejection(failure, status, checkoutCall);
            if (checkoutCall == CheckoutCall.CREATE && (status == 400 || status == 401 || status == 403 || status == 422)) {
                throw new PremiumFailure.CheckoutRejected();
            }
            throw new PremiumFailure(503, "premium.provider.unconfirmed", "Mercado Pago did not confirm the operation.");
        } catch (RestClientException failure) {
            throw new PremiumFailure(503, "premium.provider.unavailable", "Mercado Pago is temporarily unavailable.");
        }
    }

    private enum CheckoutCall {
        CREATE("POST /preapproval"),
        RECOVER_SEARCH("GET /preapproval/search"),
        RECOVER_AGREEMENT("GET /preapproval/{id}");

        private final String route;

        CheckoutCall(String route) {
            this.route = route;
        }
    }

    private static void logCheckoutRejection(RestClientResponseException failure, int status, CheckoutCall checkoutCall) {
        JsonNode body = null;
        try {
            body = failure.getResponseBodyAs(JsonNode.class);
        } catch (RestClientException | IllegalStateException ignored) {
            // El estado HTTP sigue siendo útil si el proveedor devuelve un cuerpo no legible.
        }
        String code = "unavailable";
        String causeCode = "unavailable";
        String reason = "unclassified";
        if (body != null && body.isObject()) {
            code = safeErrorCode(body.path("code"));
            if ("unavailable".equals(code)) code = safeErrorCode(body.path("error"));
            reason = rejectionReason(body.path("message").asText(""));
            if (body.path("cause").isArray()) {
                for (JsonNode cause : body.path("cause")) {
                    if ("unavailable".equals(causeCode)) causeCode = safeErrorCode(cause.path("code"));
                    if ("unclassified".equals(reason)) reason = rejectionReason(cause.path("description").asText(""));
                }
            }
        }
        var headers = failure.getResponseHeaders();
        String requestId = headers == null ? null : headers.getFirst("x-request-id");
        if (requestId == null || !requestId.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) {
            requestId = "unavailable";
        }
        LOG.warn("Mercado Pago rechazó {} upstreamStatus={} providerCode={} providerCause={} reason={} providerRequestId={}",
                checkoutCall.route, status, code, causeCode, reason, requestId);
    }

    private static String safeErrorCode(JsonNode node) {
        String code = node.asText("").toLowerCase(Locale.ROOT);
        return SAFE_ERROR_CODES.contains(code) || code.matches("[0-9]{1,5}") ? code : "unavailable";
    }

    private static String rejectionReason(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("both payer and collector must be real or test users")) return "payer_collector_mode_mismatch";
        if (normalized.contains("cannot operate with your own user")) return "payer_is_collector";
        if (normalized.contains("invalid users involved")) return "invalid_users";
        if (normalized.contains("payer_email")) return "payer_email";
        if (normalized.contains("currency_id")) return "currency_id";
        if (normalized.contains("transaction_amount")) return "transaction_amount";
        if (normalized.contains("back_url")) return "back_url";
        if (normalized.contains("auto_recurring")) return "auto_recurring";
        return "unclassified";
    }

    private static String text(JsonNode body, String field) {
        return body.path(field).asText(null);
    }

    private static String requiredId(JsonNode body, String field) {
        String id = text(body, field);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) throw invalidResponse();
        return id;
    }

    private static BigDecimal decimal(JsonNode body, String field) {
        String value = text(body, field);
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException invalid) {
            throw invalidResponse();
        }
    }

    private static Instant date(JsonNode body, String field) {
        String value = text(body, field);
        if (value == null) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException invalid) {
            throw invalidResponse();
        }
    }

    private static boolean httpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static PremiumFailure invalidResponse() {
        return new PremiumFailure(503, "premium.provider.unverifiable_response",
                "Mercado Pago response could not be verified.");
    }
}
