package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.DeliveryExpense;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "delivery_expenses")
public class DeliveryExpenseEntity {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(nullable = false, length = 100) private String merchant;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 60) private String category;
    @Column(name = "expense_date", nullable = false) private LocalDate expenseDate;
    protected DeliveryExpenseEntity() { }
    public DeliveryExpenseEntity(DeliveryExpense e) {
        id = e.id(); ownerId = e.ownerId(); requestId = e.requestId(); merchant = e.merchant();
        amount = e.amount(); category = e.category(); expenseDate = e.expenseDate();
    }
    public DeliveryExpense toDomain() { return new DeliveryExpense(id, ownerId, requestId, merchant, amount, category, expenseDate); }
}
