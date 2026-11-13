package pe.edu.upc.gastify.cravewallet.premium.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.UserRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PremiumPaymentModeTest {
    private final PremiumBillingRepository billing = mock(PremiumBillingRepository.class);
    private final PremiumMembershipRepository memberships = mock(PremiumMembershipRepository.class);
    private final PremiumPaymentGateway gateway = mock(PremiumPaymentGateway.class);
    private final Instant now = Instant.parse("2026-11-13T02:15:31Z");

    private PremiumBillingService service(boolean expected, Boolean received) {
        UUID reference = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var price = new BigDecimal("9.99");
        when(gateway.findInvoice("invoice")).thenReturn(Optional.of(new PremiumPaymentGateway.Invoice(
                "invoice", "agreement", "payment", price, "PEN", now)));
        when(gateway.findPayment("payment")).thenReturn(Optional.of(new PremiumPaymentGateway.Payment(
                "payment", price, "PEN", "approved", now, received, BigDecimal.ZERO)));
        when(gateway.findAgreement("agreement")).thenReturn(Optional.of(new PremiumPaymentGateway.Agreement(
                "agreement", reference, null, "authorized", now.toEpochMilli(), price, "PEN", 1, "months", null, now)));
        when(billing.findCheckout(reference)).thenReturn(Optional.of(new PremiumBillingRepository.Checkout(
                reference, owner, true, "agreement", null, "pending", 0, null)));
        return new PremiumBillingService(billing, memberships, mock(UserRepository.class), gateway,
                Clock.fixed(now, ZoneOffset.UTC), mock(PlatformTransactionManager.class), expected);
    }

    @Test
    void configuredLiveModeRecordsPaymentAndActivatesMembership() {
        service(true, true).receiveNotification("delivery", "subscription_authorized_payment", "invoice");
        verify(billing).insertPayment(argThat(p -> p.status().equals("approved")), any());
        verify(memberships).save(argThat(m -> m.hasAccessAt(now)
                && m.periodEndsAt().equals(Instant.parse("2026-12-13T02:15:31Z"))));
        verify(billing).markProcessed(eq("delivery"), anyString(), anyString(), eq(now));
    }

    @Test
    void legacyAcknowledgedDeliveryWithoutPaymentCanBeReplayedWithoutDuplicatingDelivery() {
        var service = service(true, true);
        when(billing.processed("delivery", "subscription_authorized_payment", "invoice")).thenReturn(true);
        service.receiveNotification("delivery", "subscription_authorized_payment", "invoice");
        verify(billing).insertPayment(any(), any());
        verify(memberships).save(any());
        verify(billing, never()).markProcessed(anyString(), anyString(), anyString(), any());
        when(billing.invoicePaid("invoice")).thenReturn(true);
        service.receiveNotification("delivery", "subscription_authorized_payment", "invoice");
        verify(gateway, times(1)).findInvoice("invoice");
        verify(memberships, times(1)).save(any());
    }

    @Test
    void mismatchedOrMissingModeNeverGrantsAccessOrAcknowledgesDelivery() {
        for (Boolean received : new Boolean[]{false, null}) {
            assertThatThrownBy(() -> service(true, received)
                    .receiveNotification("delivery", "subscription_authorized_payment", "invoice"))
                    .isInstanceOf(PremiumFailure.class).hasMessageContaining("MERCADOPAGO_EXPECTED_LIVE_MODE");
        }
        verify(billing, never()).insertPayment(any(), any());
        verify(memberships, never()).save(any());
        verify(billing, never()).markProcessed(anyString(), anyString(), anyString(), any());
    }
}
