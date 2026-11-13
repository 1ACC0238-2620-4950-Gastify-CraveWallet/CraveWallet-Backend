package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Agreement;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Invoice;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"MERCADOPAGO_WEBHOOK_SECRET=fixture-webhook-key", "MERCADOPAGO_EXPECTED_LIVE_MODE=false"})
@AutoConfigureMockMvc
class PremiumBillingIntegrationTest {
    private static final String ROOT = "/api/v1/premium";
    private static final String SECRET = "fixture-webhook-key";
    private static final String INVOICE_TOPIC = "subscription_authorized_payment";
    private static final String AGREEMENT_TOPIC = "subscription_preapproval";
    private static final Instant START = Instant.parse("2026-10-15T15:00:00Z");
    private static final Instant NOVEMBER = Instant.parse("2026-11-15T15:00:00Z");
    private static final Instant DECEMBER = Instant.parse("2026-12-15T15:00:00Z");
    private static final BigDecimal PRICE = new BigDecimal("9.99");
    private static final AtomicLong IDS = new AtomicLong(100_000);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PremiumPaymentGateway gateway;
    @MockitoBean Clock clock;
    private Instant now;

    @BeforeEach
    void setTime() {
        now = START;
        when(clock.instant()).thenAnswer(invocation -> now);
    }

    @Test
    void checkoutRequiresJwtAndReusesPendingCheckoutWithIamEmail() throws Exception {
        mvc.perform(post(ROOT + "/checkout")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/cancel")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "/payments")).andExpect(status().isUnauthorized());
        Account user = account();
        Agreement pending = checkout(user);
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value(pending.id()));
        verify(gateway, times(1)).createMonthlyCheckout(any(), eq(user.email()));
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.membershipStatus").value("PENDING"))
                .andExpect(jsonPath("$.monthlyPrice").value(9.99));
    }

    @Test
    void unknownCheckoutOutcomeDoesNotCreateAnotherRemoteSubscription() throws Exception {
        Account user = account();
        when(gateway.createMonthlyCheckout(any(), eq(user.email())))
                .thenThrow(new PremiumFailure(503, "Tiempo de espera agotado."));
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token())).andExpect(status().isServiceUnavailable());
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token())).andExpect(status().isConflict());
        verify(gateway, times(1)).createMonthlyCheckout(any(), eq(user.email()));
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void concurrentCheckoutReservesBeforeCallingProvider() throws Exception {
        Account user = account();
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(gateway.createMonthlyCheckout(any(), eq(user.email()))).thenAnswer(call -> {
            entered.countDown();
            if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Faltó liberar el checkout.");
            return agreement(id(), call.getArgument(0), "pending", 0, START);
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> mvc.perform(post(ROOT + "/checkout")
                    .header("Authorization", user.token())).andReturn().getResponse().getStatus());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token())).andExpect(status().isConflict());
            } finally {
                finish.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        verify(gateway, times(1)).createMonthlyCheckout(any(), eq(user.email()));
    }

    @Test
    void retryRecoversProviderCheckoutAfterLostCreationResponse() throws Exception {
        Account user = account();
        when(gateway.createMonthlyCheckout(any(), eq(user.email()))).thenAnswer(call -> {
            UUID reference = call.getArgument(0);
            when(gateway.findCheckout(reference, user.email()))
                    .thenReturn(Optional.of(agreement("recovered", reference, "pending", 0, now)));
            throw new PremiumFailure(503, "Se perdió la respuesta.");
        });
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token())).andExpect(status().isServiceUnavailable());
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionId").value("recovered"));
        verify(gateway, times(1)).createMonthlyCheckout(any(), eq(user.email()));
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void recoversPendingCheckoutWithoutProviderVersionAndKeepsUserFree() throws Exception {
        Account user = account();
        when(gateway.createMonthlyCheckout(any(), eq(user.email()))).thenAnswer(call -> {
            UUID reference = call.getArgument(0);
            when(gateway.findCheckout(reference, user.email()))
                    .thenReturn(Optional.of(agreement("recovered-unversioned", reference, "pending", -1, now)));
            throw new PremiumFailure(503, "Se perdió la respuesta.");
        });
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token()))
                .andExpect(status().isServiceUnavailable());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionId").value("recovered-unversioned"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }
        verify(gateway, times(1)).createMonthlyCheckout(any(), eq(user.email()));
        verify(gateway, times(1)).findCheckout(any(), eq(user.email()));
        premiumStatus(user).andExpect(jsonPath("$.plan").value("FREE"));
        payments(user).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsUnrecognizedAgreementStatusEvenWithVersion() throws Exception {
        Account user = account();
        when(gateway.createMonthlyCheckout(any(), eq(user.email()))).thenAnswer(call ->
                agreement(id(), call.getArgument(0), "unexpected", 0, now));
        mvc.perform(post(ROOT + "/checkout").header("Authorization", user.token()))
                .andExpect(status().isServiceUnavailable());
        premiumStatus(user).andExpect(jsonPath("$.plan").value("FREE"));
    }

    @Test
    void rejectsInvalidSignatureAndMismatchedBodyWithoutCallingProvider() throws Exception {
        var requestId = UUID.randomUUID().toString();
        mvc.perform(post(ROOT + "/webhook").param("data.id", "123").header("x-request-id", requestId)
                        .header("x-signature", "ts=" + now.getEpochSecond() + ",v1=" + "a".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON).content(event(INVOICE_TOPIC, "123")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT + "/webhook").param("data.id", "123").header("x-request-id", requestId)
                        .header("x-signature", signature("123", requestId))
                        .contentType(MediaType.APPLICATION_JSON).content(event(INVOICE_TOPIC, "999")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsStaleSignaturesForNumericQueryId() throws Exception {
        var requestId = UUID.randomUUID().toString();
        String signed = signature("123456", requestId);
        mvc.perform(post(ROOT + "/webhook").queryParam("data.id", "123456").header("x-request-id", requestId)
                        .header("x-signature", signed).contentType(MediaType.APPLICATION_JSON)
                        .content(event(AGREEMENT_TOPIC, "123456")))
                .andExpect(status().isOk());
        verify(gateway).findAgreement("123456");
        now = now.plusSeconds(86_401);
        mvc.perform(post(ROOT + "/webhook").queryParam("data.id", "123456").header("x-request-id", requestId)
                        .header("x-signature", signed).contentType(MediaType.APPLICATION_JSON)
                        .content(event(AGREEMENT_TOPIC, "123456")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesExactQueryIdCaseAndPreservesMillisecondTimestampInManifest() throws Exception {
        var requestId = UUID.randomUUID().toString();
        String resourceId = "AbC123";
        long timestamp = now.toEpochMilli();
        String body = event(AGREEMENT_TOPIC, resourceId);
        mvc.perform(post(ROOT + "/webhook").queryParam("data.id", resourceId).header("x-request-id", requestId)
                        .header("x-signature", signature("abc123", requestId, timestamp))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(gateway);

        mvc.perform(post(ROOT + "/webhook").queryParam("data.id", resourceId).header("x-request-id", requestId)
                        .header("x-signature", signature(resourceId, requestId, timestamp))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(gateway).findAgreement(resourceId);
    }

    @Test
    void approvedPaymentActivatesAndKeepsHistoryIsolatedAcrossTopicsAndDeliveries() throws Exception {
        Account user = account();
        Account other = account();
        for (int index = 0; index < 5; index++) registerSubscription(user).andExpect(status().isCreated());
        registerSubscription(user).andExpect(status().isConflict());

        Agreement agreement = authorize(checkout(user));
        Invoice invoice = charge(agreement, START, PRICE, "PEN", "approved", false);
        String delivery = UUID.randomUUID().toString();
        notify(INVOICE_TOPIC, invoice.id(), delivery).andExpect(status().isOk());
        notify(INVOICE_TOPIC, invoice.id(), delivery).andExpect(status().isOk());
        verify(gateway, times(1)).findInvoice(invoice.id());
        when(gateway.findInvoiceByPayment(invoice.paymentId())).thenReturn(Optional.of(invoice));
        notify("payment", invoice.paymentId()).andExpect(status().isOk());

        premiumStatus(user).andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.periodEndsAt").value(NOVEMBER.toString()));
        registerSubscription(user).andExpect(status().isCreated());
        payments(user).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].amount").value(9.99))
                .andExpect(jsonPath("$[0].currency").value("PEN"))
                .andExpect(jsonPath("$[0].status").value("approved"))
                .andExpect(jsonPath("$[0].paidAt").value(START.toString()));
        payments(other).andExpect(jsonPath("$").isEmpty());
        premiumStatus(other).andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void rejectedPendingWrongAmountWrongCurrencyAndLivePaymentsNeverActivate() throws Exception {
        Account user = account();
        Agreement agreement = authorize(checkout(user));
        for (Invoice invoice : new Invoice[]{
                charge(agreement, START, PRICE, "PEN", "rejected", false),
                charge(agreement, START, PRICE, "PEN", "pending", false),
                charge(agreement, START, new BigDecimal("9.98"), "PEN", "approved", false),
                charge(agreement, START, PRICE, "USD", "approved", false)}) {
            notify(INVOICE_TOPIC, invoice.id()).andExpect(status().isOk());
        }
        Invoice live = charge(agreement, START, PRICE, "PEN", "approved", true);
        notify(INVOICE_TOPIC, live.id()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("El live_mode del pago no coincide con MERCADOPAGO_EXPECTED_LIVE_MODE del backend."));
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false));
        payments(user).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].status").value("rejected"));
    }

    @Test
    void approvedPaymentWaitsUntilAgreementIsAuthorizedAndRetriesTemporaryErrors() throws Exception {
        Account user = account();
        Agreement pending = checkout(user);
        Invoice invoice = charge(pending, START, PRICE, "PEN", "approved", false);
        String delivery = UUID.randomUUID().toString();
        notify(INVOICE_TOPIC, invoice.id(), delivery).andExpect(status().isServiceUnavailable());
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false));
        authorize(pending);
        notify(INVOICE_TOPIC, invoice.id(), delivery).andExpect(status().isOk());
        premiumStatus(user).andExpect(jsonPath("$.premium").value(true));
    }

    @Test
    void mismatchedSubscriptionCannotTakeOverAnotherUsersCheckout() throws Exception {
        Account user = account();
        Account other = account();
        checkout(user);
        Agreement foreign = checkout(other);
        Agreement mismatch = agreement(id(), foreign.reference(), "authorized", 1, START);
        when(gateway.findAgreement(mismatch.id())).thenReturn(Optional.of(mismatch));
        var invoice = charge(mismatch, START, PRICE, "PEN", "approved", false);
        notify(INVOICE_TOPIC, invoice.id()).andExpect(status().isOk());
        notify(AGREEMENT_TOPIC, id()).andExpect(status().isOk());
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false));
        premiumStatus(other).andExpect(jsonPath("$.premium").value(false));
        payments(other).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void concurrentAndOutOfOrderPaymentsUseTheLatestPaidPeriodOnlyOnce() throws Exception {
        Account user = account();
        Agreement agreement = authorize(checkout(user));
        now = NOVEMBER;
        Invoice later = charge(agreement, NOVEMBER, PRICE, "PEN", "approved", false);
        Invoice earlier = charge(agreement, START, PRICE, "PEN", "approved", false);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> deliver = () -> notify(INVOICE_TOPIC, later.id()).andReturn().getResponse().getStatus();
            var first = executor.submit(deliver);
            var second = executor.submit(deliver);
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        notify(INVOICE_TOPIC, earlier.id()).andExpect(status().isOk());
        premiumStatus(user).andExpect(jsonPath("$.periodEndsAt").value(DECEMBER.toString()));
        payments(user).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void cancellationKeepsPaidAccessAndIgnoresSubsequentRenewalForEntitlement() throws Exception {
        Account user = account();
        Agreement agreement = authorize(checkout(user));
        Invoice first = charge(agreement, START, PRICE, "PEN", "approved", false);
        notify(INVOICE_TOPIC, first.id()).andExpect(status().isOk());
        now = START.plusSeconds(86_400);
        Agreement canceled = agreement(agreement.id(), agreement.reference(), "canceled", 2, now);
        doAnswer(call -> {
            when(gateway.findAgreement(agreement.id())).thenReturn(Optional.of(canceled));
            return null;
        }).when(gateway).cancelAgreement(agreement.id());

        mvc.perform(post(ROOT + "/cancel").header("Authorization", user.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.premium").value(true)).andExpect(jsonPath("$.periodEndsAt").value(NOVEMBER.toString()));
        mvc.perform(post(ROOT + "/cancel").header("Authorization", user.token())).andExpect(status().isOk());
        verify(gateway, times(1)).cancelAgreement(agreement.id());

        now = NOVEMBER;
        when(gateway.findAgreement(agreement.id())).thenReturn(Optional.of(agreement));
        Invoice late = charge(agreement, NOVEMBER, PRICE, "PEN", "approved", false);
        notify(INVOICE_TOPIC, late.id()).andExpect(status().isOk());
        premiumStatus(user).andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.cancelAtPeriodEnd").value(true))
                .andExpect(jsonPath("$.periodEndsAt").value(NOVEMBER.toString()));
    }

    @Test
    void unconfirmedRemoteCancellationIsNotReportedAsSuccessful() throws Exception {
        Account user = account();
        authorize(checkout(user));
        mvc.perform(post(ROOT + "/cancel").header("Authorization", user.token())).andExpect(status().isServiceUnavailable());
        premiumStatus(user).andExpect(jsonPath("$.cancelAtPeriodEnd").value(false));
    }

    private Account account() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        JsonNode result = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return new Account(email, "Bearer " + result.path("accessToken").asText());
    }

    private Agreement checkout(Account account) throws Exception {
        var agreements = new java.util.ArrayList<Agreement>();
        when(gateway.createMonthlyCheckout(any(), eq(account.email()))).thenAnswer(call -> {
            var agreement = agreement(id(), call.getArgument(0), "pending", 0, now);
            agreements.add(agreement);
            return agreement;
        });
        mvc.perform(post(ROOT + "/checkout").header("Authorization", account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0,\"payer_email\":\"ignored@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(9.99));
        Agreement pending = agreements.getFirst();
        when(gateway.findAgreement(pending.id())).thenReturn(Optional.of(pending));
        return pending;
    }

    private Agreement authorize(Agreement pending) {
        Agreement authorized = agreement(pending.id(), pending.reference(), "authorized", 1, now);
        when(gateway.findAgreement(pending.id())).thenReturn(Optional.of(authorized));
        return authorized;
    }

    private static Agreement agreement(String id, UUID reference, String status, long version, Instant modifiedAt) {
        return new Agreement(id, reference, "https://www.mercadopago.com.pe/subscriptions/checkout?preapproval_id=" + id,
                status, version, PRICE, "PEN", 1, "months", null, modifiedAt);
    }

    private Invoice charge(Agreement agreement, Instant debitAt, BigDecimal amount, String currency, String status, boolean live) {
        var invoice = new Invoice(id(), agreement.id(), id(), PRICE, "PEN", debitAt);
        var payment = new Payment(invoice.paymentId(), amount, currency, status, debitAt, live, BigDecimal.ZERO);
        when(gateway.findInvoice(invoice.id())).thenReturn(Optional.of(invoice));
        when(gateway.findPayment(payment.id())).thenReturn(Optional.of(payment));
        return invoice;
    }

    private ResultActions registerSubscription(Account account) throws Exception {
        return mvc.perform(post("/api/v1/subscriptions").header("Authorization", account.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Streaming", "amount", 12, "currency", "PEN", "category", "Test",
                        "billingCycle", "MONTHLY", "nextBillingDate", LocalDate.now().plusDays(30).toString()))));
    }

    private ResultActions premiumStatus(Account account) throws Exception {
        return mvc.perform(get(ROOT + "/status").header("Authorization", account.token())).andExpect(status().isOk());
    }

    private ResultActions payments(Account account) throws Exception {
        return mvc.perform(get(ROOT + "/payments").header("Authorization", account.token())).andExpect(status().isOk());
    }

    private ResultActions notify(String topic, String id) throws Exception {
        return notify(topic, id, UUID.randomUUID().toString());
    }

    private ResultActions notify(String topic, String id, String requestId) throws Exception {
        return mvc.perform(post(ROOT + "/webhook").param("data.id", id).header("x-request-id", requestId)
                .header("x-signature", signature(id, requestId)).contentType(MediaType.APPLICATION_JSON)
                .content(event(topic, id)));
    }

    private String signature(String id, String requestId) throws Exception {
        return signature(id, requestId, now.getEpochSecond());
    }

    private String signature(String id, String requestId, long ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifest = "id:" + id + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    private String event(String topic, String id) throws Exception {
        return json.writeValueAsString(Map.of("type", topic, "data", Map.of("id", id)));
    }

    private static String id() {
        return Long.toString(IDS.incrementAndGet());
    }

    private record Account(String email, String token) {}
}
