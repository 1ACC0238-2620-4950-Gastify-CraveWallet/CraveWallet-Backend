package pe.edu.upc.gastify.cravewallet.delivery.domain.model;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

public record MonthlyBudget(UUID id, UUID ownerId, YearMonth period, BigDecimal spendingLimit, BigDecimal accumulated) {
    public MonthlyBudget {
        if (id == null || ownerId == null || period == null || accumulated == null || accumulated.signum() < 0
                || (spendingLimit != null && (spendingLimit.signum() <= 0 || spendingLimit.scale() > 2
                || spendingLimit.compareTo(new BigDecimal("9999999999.99")) > 0)))
            throw new IllegalArgumentException("Presupuesto no válido.");
    }
    public MonthlyBudget add(BigDecimal amount) { return new MonthlyBudget(id, ownerId, period, spendingLimit, accumulated.add(amount)); }
    public MonthlyBudget withLimit(BigDecimal limit) { return new MonthlyBudget(id, ownerId, period, limit, accumulated); }
    public boolean exceeded() { return spendingLimit != null && accumulated.compareTo(spendingLimit) > 0; }
}
