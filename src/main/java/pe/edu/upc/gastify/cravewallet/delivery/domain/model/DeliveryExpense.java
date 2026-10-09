package pe.edu.upc.gastify.cravewallet.delivery.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DeliveryExpense(UUID id, UUID ownerId, UUID requestId, String merchant, BigDecimal amount,
                              String category, LocalDate expenseDate) {
    public DeliveryExpense {
        if (id == null || ownerId == null || requestId == null || merchant == null || merchant.isBlank()
                || merchant.length() > 100 || category == null || category.isBlank() || category.length() > 60
                || amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(new BigDecimal("9999999999.99")) > 0 || expenseDate == null)
            throw new IllegalArgumentException("Gasto no válido.");
        merchant = merchant.strip(); category = category.strip();
    }
    public boolean sameRequest(DeliveryExpense other) {
        return requestId.equals(other.requestId) && merchant.equals(other.merchant)
                && amount.compareTo(other.amount) == 0 && category.equals(other.category)
                && expenseDate.equals(other.expenseDate);
    }
}
