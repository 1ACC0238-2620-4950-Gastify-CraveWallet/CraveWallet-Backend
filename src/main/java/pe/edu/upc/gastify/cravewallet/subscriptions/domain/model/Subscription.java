package pe.edu.upc.gastify.cravewallet.subscriptions.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Subscription(UUID id, UUID ownerId, String name, BigDecimal amount,
                           Currency currency, String category, BillingCycle billingCycle,
                           LocalDate nextBillingDate, Status status, Instant cancelledAt) {
    public enum Currency { PEN, USD }
    public enum BillingCycle { MONTHLY, ANNUAL }
    public enum Status { ACTIVE, CANCELLED }

    public Subscription {
        if (id == null || ownerId == null || name == null || name.isBlank() || name.length() > 100
                || amount == null || amount.signum() < 0 || amount.scale() > 2
                || amount.compareTo(new BigDecimal("9999999999.99")) > 0
                || currency == null || category == null || category.isBlank() || category.length() > 60
                || billingCycle == null || nextBillingDate == null || status == null
                || (status == Status.CANCELLED) != (cancelledAt != null)) {
            throw new IllegalArgumentException("Datos de suscripción no válidos.");
        }
        name = name.strip();
        category = category.strip();
    }

    public Subscription edit(BigDecimal newAmount, String newCategory, LocalDate newDate) {
        return new Subscription(id, ownerId, name, newAmount == null ? amount : newAmount,
                currency, newCategory == null ? category : newCategory, billingCycle,
                newDate == null ? nextBillingDate : newDate, status, cancelledAt);
    }

    public Subscription cancel(Instant now) {
        return status == Status.CANCELLED ? this : new Subscription(id, ownerId, name, amount,
                currency, category, billingCycle, nextBillingDate, Status.CANCELLED, now);
    }
}
