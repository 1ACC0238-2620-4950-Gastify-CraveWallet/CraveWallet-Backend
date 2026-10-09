package pe.edu.upc.gastify.cravewallet.delivery.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.*;
import pe.edu.upc.gastify.cravewallet.delivery.domain.repositories.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class DeliveryApplicationService {
    private final DeliveryExpenseRepository expenses;
    private final MonthlyBudgetRepository budgets;
    private final DeliveryOwnerLock ownerLock;
    private final ApplicationEventPublisher events;
    public DeliveryApplicationService(DeliveryExpenseRepository expenses, MonthlyBudgetRepository budgets,
                                      DeliveryOwnerLock ownerLock, ApplicationEventPublisher events) {
        this.expenses = expenses; this.budgets = budgets; this.ownerLock = ownerLock; this.events = events;
    }
    public Registration register(UUID owner, UUID requestId, String merchant, BigDecimal amount, String category, LocalDate date) {
        if (date.isAfter(LocalDate.now(ZoneId.of("America/Lima")))) throw new DeliveryFailure(400, "La fecha del gasto no puede ser futura.");
        DeliveryExpense requested = new DeliveryExpense(UUID.randomUUID(), owner, requestId, merchant, amount, category, date);
        ownerLock.acquire(owner);
        var prior = expenses.findByRequest(owner, requestId);
        if (prior.isPresent()) {
            if (!prior.get().sameRequest(requested)) throw new DeliveryFailure(409, "El identificador de solicitud ya tiene otros datos.");
            return new Registration(prior.get(), true);
        }
        YearMonth period = YearMonth.from(date);
        MonthlyBudget current = budgets.find(owner, period).orElseGet(() -> emptyBudget(owner, period));
        MonthlyBudget updated = current.add(amount);
        expenses.save(requested);
        budgets.save(updated);
        // Los consumidores de efectos externos deben usar @TransactionalEventListener(AFTER_COMMIT).
        events.publishEvent(new ExpenseRegistered(requested.id(), owner, amount, date));
        if (!current.exceeded() && updated.exceeded())
            events.publishEvent(new LimitExceeded(owner, period, updated.spendingLimit(), updated.accumulated()));
        return new Registration(requested, false);
    }
    public MonthlyBudget setBudget(UUID owner, YearMonth period, BigDecimal limit) {
        ownerLock.acquire(owner);
        MonthlyBudget current = budgets.find(owner, period).orElseGet(() -> emptyBudget(owner, period));
        MonthlyBudget updated = current.withLimit(limit);
        budgets.save(updated);
        return updated;
    }
    @Transactional(readOnly = true)
    public Summary summary(UUID owner, YearMonth period) {
        // Una sola consulta de gastos evita mezclar snapshots de acumulado y desglose.
        List<DeliveryExpense> records = expenses.findPeriod(owner, period);
        BigDecimal total = records.stream().map(DeliveryExpense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal limit = budgets.find(owner, period).map(MonthlyBudget::spendingLimit).orElse(null);
        Map<String, BigDecimal> categories = new TreeMap<>();
        Map<Integer, BigDecimal> weeks = new TreeMap<>();
        for (DeliveryExpense expense : records) {
            categories.merge(expense.category(), expense.amount(), BigDecimal::add);
            weeks.merge((expense.expenseDate().getDayOfMonth() - 1) / 7 + 1, expense.amount(), BigDecimal::add);
        }
        return new Summary(period.getYear(), period.getMonthValue(), "PEN", total, limit,
                limit == null ? null : limit.subtract(total), limit != null && total.compareTo(limit) > 0,
                categories, weeks);
    }
    private MonthlyBudget emptyBudget(UUID owner, YearMonth period) { return new MonthlyBudget(UUID.randomUUID(), owner, period, null, BigDecimal.ZERO); }
    public record Registration(DeliveryExpense expense, boolean replayed) { }
    public record Summary(int year, int month, String currency, BigDecimal total, BigDecimal spendingLimit,
                          BigDecimal remaining, boolean exceeded, Map<String, BigDecimal> totalsByCategory,
                          Map<Integer, BigDecimal> totalsByWeek) { }
    public record ExpenseRegistered(UUID expenseId, UUID ownerId, BigDecimal amount, LocalDate date) { }
    public record LimitExceeded(UUID ownerId, YearMonth period, BigDecimal limit, BigDecimal accumulated) { }
}
