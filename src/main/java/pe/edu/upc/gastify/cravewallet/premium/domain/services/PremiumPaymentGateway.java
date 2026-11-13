package pe.edu.upc.gastify.cravewallet.premium.domain.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PremiumPaymentGateway {
    BigDecimal MONTHLY_PRICE = new BigDecimal("9.99");
    String CURRENCY = "PEN";

    void checkCheckoutConfiguration();
    Agreement createMonthlyCheckout(UUID reference, String payerEmail);
    Optional<Agreement> findCheckout(UUID reference, String payerEmail);
    Optional<Agreement> findAgreement(String id);
    void cancelAgreement(String id);
    Optional<Invoice> findInvoice(String id);
    Optional<Invoice> findInvoiceByPayment(String paymentId);
    Optional<Payment> findPayment(String id);

    record Agreement(String id, UUID reference, String checkoutUrl, String status, long version,
                     BigDecimal amount, String currency, int frequency, String frequencyType,
                     String planId, Instant modifiedAt) {
        public boolean matchesMonthlyTerms() {
            return (planId == null || planId.isBlank()) && amount != null
                    && MONTHLY_PRICE.compareTo(amount) == 0 && CURRENCY.equals(currency)
                    && frequency == 1 && "months".equals(frequencyType);
        }

        public boolean canceled() {
            return "cancelled".equals(status) || "canceled".equals(status);
        }
    }

    record Invoice(String id, String agreementId, String paymentId, BigDecimal amount,
                   String currency, Instant debitAt) {}

    record Payment(String id, BigDecimal amount, String currency, String status, Instant paidAt,
                   Boolean liveMode, BigDecimal refundedAmount) {}
}
