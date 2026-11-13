package pe.edu.upc.gastify.cravewallet.premium.infrastructure.payments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
class MercadoPagoPaymentAdapterTest {
    private static final String BASE = "https://api.mercadopago.com";
    private MockRestServiceServer server;
    private MercadoPagoPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MercadoPagoPaymentAdapter(builder.build(), "fixture-token", "https://cravewallet.example/return",
                "fixture-secret");
    }

    @Test
    void hostedCheckoutSendsFixedRecurringTermsAndUsesReturnedUrl() {
        UUID reference = UUID.randomUUID();
        String hostedUrl = "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=abc";
        server.expect(requestTo(BASE + "/preapproval")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fixture-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.payer_email").value("buyer@example.com"))
                .andExpect(jsonPath("$.reason").value("CraveWallet Premium mensual"))
                .andExpect(jsonPath("$.back_url").value("https://cravewallet.example/return"))
                .andExpect(jsonPath("$.external_reference").value(reference.toString()))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.auto_recurring.frequency").value(1))
                .andExpect(jsonPath("$.auto_recurring.frequency_type").value("months"))
                .andExpect(jsonPath("$.auto_recurring.currency_id").value("PEN"))
                .andExpect(jsonPath("$.auto_recurring.transaction_amount").value(9.99))
                .andExpect(jsonPath("$.card_token_id").doesNotExist())
                .andExpect(jsonPath("$.preapproval_plan_id").doesNotExist())
                .andRespond(withSuccess("""
                        {"id":"abc","external_reference":"%s","init_point":"%s","status":"pending",
                         "auto_recurring":{"frequency":1,"frequency_type":"months","transaction_amount":"9.99","currency_id":"PEN"}}
                        """.formatted(reference, hostedUrl), MediaType.APPLICATION_JSON));
        var agreement = adapter.createMonthlyCheckout(reference, "buyer@example.com");
        assertThat(agreement.matchesMonthlyTerms()).isTrue();
        assertThat(agreement.checkoutUrl()).isEqualTo(hostedUrl);
        assertThat(agreement.version()).isEqualTo(-1);
        server.verify();
    }

    @Test
    void authorizedSubscriptionWithoutVersionUsesProviderModificationTime() {
        UUID reference = UUID.randomUUID();
        server.expect(requestTo(BASE + "/preapproval/abc"))
                .andRespond(withSuccess("""
                        {"id":"abc","status":"authorized","external_reference":"%s",
                         "last_modified":"2026-11-12T21:48:37.768-04:00",
                         "auto_recurring":{"frequency":1,"frequency_type":"months",
                         "transaction_amount":9.99,"currency_id":"PEN"}}
                        """.formatted(reference), MediaType.APPLICATION_JSON));
        var agreement = adapter.findAgreement("abc").orElseThrow();
        assertThat(agreement.status()).isEqualTo("authorized");
        assertThat(agreement.matchesMonthlyTerms()).isTrue();
        assertThat(agreement.reference()).isEqualTo(reference);
        assertThat(agreement.version()).isEqualTo(Instant.parse("2026-11-13T01:48:37.768Z").toEpochMilli());
        server.verify();
    }

    @Test
    void modificationTimeOrdersResponsesEvenWhenVersionAppearsOrDisappears() {
        server.expect(requestTo(BASE + "/preapproval/abc"))
                .andRespond(withSuccess("""
                        {"id":"abc","status":"authorized","version":99,
                         "last_modified":"2026-11-13T01:48:37.768Z"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/preapproval/abc"))
                .andRespond(withSuccess("""
                        {"id":"abc","status":"pending",
                         "last_modified":"2026-11-13T01:48:14.000Z"}
                        """, MediaType.APPLICATION_JSON));
        long authorizedRevision = adapter.findAgreement("abc").orElseThrow().version();
        assertThat(adapter.findAgreement("abc").orElseThrow().version()).isLessThan(authorizedRevision);
        server.verify();
    }

    @Test
    void checkoutRejectionLogsOnlyAllowlistedDiagnostics(CapturedOutput output) {
        String requestId = UUID.randomUUID().toString();
        server.expect(requestTo(BASE + "/preapproval")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .header("x-request-id", requestId).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":"bad_request","message":"Both payer and collector must be real or test users: buyer@example.com",
                         "cause":[{"code":"invalid_payer_email","description":"fixture-token"}]}
                        """));

        assertThatThrownBy(() -> adapter.createMonthlyCheckout(UUID.randomUUID(), "buyer@example.com"))
                .isInstanceOf(PremiumFailure.CheckoutRejected.class)
                .hasMessage("Mercado Pago rechazó la creación del checkout.");
        assertThat(output.getAll()).contains("upstreamStatus=400", "providerCode=bad_request",
                        "providerCause=invalid_payer_email", "reason=payer_collector_mode_mismatch",
                        "providerRequestId=" + requestId)
                .doesNotContain("buyer@example.com", "fixture-token", "Both payer and collector");
        server.verify();
    }

    @Test
    void nonJsonCheckoutRejectionStillLogsOriginalHttpStatus(CapturedOutput output) {
        server.expect(requestTo(BASE + "/preapproval")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .header("x-request-id", "fixture-secret").contentType(MediaType.TEXT_PLAIN).body("fixture-token"));

        assertThatThrownBy(() -> adapter.createMonthlyCheckout(UUID.randomUUID(), "buyer@example.com"))
                .isInstanceOf(PremiumFailure.CheckoutRejected.class);
        assertThat(output.getAll()).contains("upstreamStatus=401", "providerCode=unavailable",
                        "reason=unclassified", "providerRequestId=unavailable")
                .doesNotContain("fixture-token", "fixture-secret", "buyer@example.com");
        server.verify();
    }

    @Test
    void uncertainCreateAndRecoveryErrorsIdentifyTheirRouteWithoutLoggingRequestData(CapturedOutput output) {
        server.expect(requestTo(BASE + "/preapproval")).andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"fixture-token\"}"));
        server.expect(requestTo(BASE + "/preapproval/search?payer_email=buyer%40example.com"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        UUID reference = UUID.randomUUID();
        assertThatThrownBy(() -> adapter.createMonthlyCheckout(reference, "buyer@example.com"))
                .isExactlyInstanceOf(PremiumFailure.class).hasMessage("Mercado Pago no confirmó la operación.");
        assertThatThrownBy(() -> adapter.findCheckout(reference, "buyer@example.com"))
                .isExactlyInstanceOf(PremiumFailure.class).hasMessage("Mercado Pago no confirmó la operación.");
        assertThat(output.getAll()).contains("POST /preapproval upstreamStatus=500",
                        "GET /preapproval/search upstreamStatus=429")
                .doesNotContain("buyer@example.com", "buyer%40example.com", "fixture-token", reference.toString());
        server.verify();
    }

    @Test
    void invoiceAndPaymentLookupsParseSeparateProviderResources() {
        String invoice = """
                {"id":651,"preapproval_id":"abc","currency_id":"PEN","transaction_amount":"9.99",
                 "debit_date":"2026-11-12T10:00:00-05:00","payment":{"id":123,"status":"approved"}}
                """;
        server.expect(requestTo(BASE + "/authorized_payments/651")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(invoice, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/authorized_payments/search?payment_id=123"))
                .andRespond(withSuccess("{\"results\":[" + invoice + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v1/payments/123")).andRespond(withSuccess("""
                {"id":123,"currency_id":"PEN","transaction_amount":9.99,"transaction_amount_refunded":0,
                 "date_approved":"2026-11-12T15:01:00Z","status":"approved","live_mode":false}
                """, MediaType.APPLICATION_JSON));
        var byId = adapter.findInvoice("651").orElseThrow();
        assertThat(adapter.findInvoiceByPayment("123")).contains(byId);
        assertThat(byId.debitAt()).isEqualTo(Instant.parse("2026-11-12T15:00:00Z"));
        var payment = adapter.findPayment(byId.paymentId()).orElseThrow();
        assertThat(payment.liveMode()).isFalse();
        assertThat(payment.paidAt()).isEqualTo(Instant.parse("2026-11-12T15:01:00Z"));
        server.verify();
    }

    @Test
    void recoveryMatchesExactReferenceBeforeReadingProviderAgreement() {
        UUID reference = UUID.randomUUID();
        server.expect(requestTo(BASE + "/preapproval/search?payer_email=buyer%40example.com"))
                .andRespond(withSuccess("""
                        {"results":[{"id":"unrelated","external_reference":"other"},
                                    {"id":"mine","external_reference":"%s"}]}
                        """.formatted(reference), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/preapproval/mine"))
                .andRespond(withSuccess("""
                        {"id":"mine","version":0,"status":"pending","external_reference":"%s"}
                        """.formatted(reference), MediaType.APPLICATION_JSON));
        assertThat(adapter.findCheckout(reference, "buyer@example.com").orElseThrow().id()).isEqualTo("mine");
        server.verify();
    }

    @Test
    void cancellationSendsDocumentedStatusAndReadErrorsAreControlled() {
        server.expect(requestTo(BASE + "/preapproval/abc")).andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.status").value("canceled"))
                .andRespond(withSuccess("{\"id\":\"abc\",\"status\":\"cancelled\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/preapproval/abc")).andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        adapter.cancelAgreement("abc");
        assertThatThrownBy(() -> adapter.findAgreement("abc")).isInstanceOf(PremiumFailure.class)
                .hasMessage("Mercado Pago no confirmó la operación.");
        server.verify();
    }

    @Test
    void absentCredentialsNeverCreateAFakeSession() {
        var unconfigured = new MercadoPagoPaymentAdapter(RestClient.create(), "", "", "");
        assertThatThrownBy(() -> unconfigured.createMonthlyCheckout(UUID.randomUUID(), "buyer@example.com"))
                .isInstanceOf(PremiumFailure.class).hasMessageContaining("Falta configurar");
    }
}
