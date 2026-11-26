package pe.edu.upc.gastify.cravewallet.subscriptions.domain.services;

import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Versions of the amount and schedule of a subscription, ordered by the moment they took effect. */
public interface SubscriptionTermsHistoryPort {
    void record(UUID subscriptionId, Terms terms);
    List<Terms> find(UUID subscriptionId);

    record Terms(Instant effectiveFrom, BigDecimal amount, LocalDate nextBillingDate, Subscription.BillingCycle billingCycle) {
        public static Terms of(Subscription subscription, Instant effectiveFrom) {
            return new Terms(effectiveFrom, subscription.amount(), subscription.nextBillingDate(), subscription.billingCycle());
        }
    }
}
