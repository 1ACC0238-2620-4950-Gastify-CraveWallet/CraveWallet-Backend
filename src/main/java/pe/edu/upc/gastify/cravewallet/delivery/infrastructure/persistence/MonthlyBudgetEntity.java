package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MonthlyBudget;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

@Entity @Table(name = "monthly_budgets")
public class MonthlyBudgetEntity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "budget_year", nullable = false) private int year;
    @Column(name = "budget_month", nullable = false) private int month;
    @Column(name = "spending_limit", precision = 12, scale = 2) private BigDecimal spendingLimit;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal accumulated;
    protected MonthlyBudgetEntity() { }
    public MonthlyBudgetEntity(MonthlyBudget b) { update(b); }
    public void update(MonthlyBudget b) {
        id = b.id(); ownerId = b.ownerId(); year = b.period().getYear(); month = b.period().getMonthValue();
        spendingLimit = b.spendingLimit(); accumulated = b.accumulated();
    }
    public MonthlyBudget toDomain() { return new MonthlyBudget(id, ownerId, YearMonth.of(year, month), spendingLimit, accumulated); }
}
