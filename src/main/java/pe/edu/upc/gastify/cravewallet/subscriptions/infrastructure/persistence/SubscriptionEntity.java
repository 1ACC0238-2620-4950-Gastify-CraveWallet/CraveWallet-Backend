package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private Subscription.Currency currency;
    @Column(nullable = false, length = 60) private String category;
    @Enumerated(EnumType.STRING) @Column(name = "billing_cycle", nullable = false, length = 10) private Subscription.BillingCycle billingCycle;
    @Column(name = "next_billing_date", nullable = false) private LocalDate nextBillingDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Subscription.Status status;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    protected SubscriptionEntity() { }
    public SubscriptionEntity(Subscription value) { update(value); }
    public void update(Subscription value) {
        id = value.id(); ownerId = value.ownerId(); name = value.name(); amount = value.amount();
        currency = value.currency(); category = value.category(); billingCycle = value.billingCycle();
        nextBillingDate = value.nextBillingDate(); status = value.status(); cancelledAt = value.cancelledAt();
    }
    public Subscription toDomain() { return new Subscription(id, ownerId, name, amount, currency, category,
            billingCycle, nextBillingDate, status, cancelledAt); }
}
