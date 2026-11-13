package pe.edu.upc.gastify.cravewallet.premium.domain.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PremiumBillingRepository {
    void lockOwner(UUID owner);
    Optional<Checkout> currentCheckout(UUID owner);
    Optional<Checkout> findCheckout(UUID reference);
    void releaseCheckout(UUID reference);
    void reserveCheckout(UUID reference, UUID owner);
    void updateCheckout(Checkout checkout);

    boolean processed(String deliveryId, String topic, String resourceId);
    void markProcessed(String deliveryId, String topic, String resourceId, Instant processedAt);
    Optional<PaymentEntry> findPayment(String externalId);
    boolean invoicePaid(String invoiceId);
    void insertPayment(PaymentEntry payment, UUID reference);
    void approvePayment(PaymentEntry payment);
    List<PaymentEntry> payments(UUID owner);

    record Checkout(UUID reference, UUID owner, boolean current, String agreementId, String checkoutUrl,
                    String status, long version, Instant canceledAt) {}

    record PaymentEntry(String externalId, String invoiceId, BigDecimal amount, String currency,
                        String status, Instant paidAt, Instant recordedAt) {}
}
