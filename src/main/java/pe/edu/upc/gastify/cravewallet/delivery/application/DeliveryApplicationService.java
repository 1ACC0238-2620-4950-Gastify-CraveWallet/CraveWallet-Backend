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
        if (date.isAfter(LocalDate.now(ZoneId.of("America/Lima"))))
            throw new DeliveryFailure(400, "delivery.expense.future_date", "Expense date cannot be in the future.");
        DeliveryExpense requested = new DeliveryExpense(UUID.randomUUID(), owner, requestId, merchant, amount, category, date);
        ownerLock.acquire(owner);
        var prior = expenses.findByRequest(owner, requestId);
        if (prior.isPresent()) {
            if (!prior.get().sameRequest(requested))
                throw new DeliveryFailure(409, "delivery.expense.request_id_conflict", "Request identifier already exists with different data.");
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
    @Transactional(readOnly = true)
    public List<DeliveryExpense> list(UUID owner, YearMonth period) { return expenses.findPeriod(owner, period); }

    public DeliveryExpense correct(UUID owner, UUID expenseId, String merchant, BigDecimal amount, String category, LocalDate date) {
        if (date.isAfter(LocalDate.now(ZoneId.of("America/Lima"))))
            throw new DeliveryFailure(400, "delivery.expense.future_date", "Expense date cannot be in the future.");
        ownerLock.acquire(owner);
        DeliveryExpense previous = owned(owner, expenseId);
        DeliveryExpense corrected = previous.correct(merchant, amount, category, date);
        YearMonth oldPeriod = YearMonth.from(previous.expenseDate());
        YearMonth newPeriod = YearMonth.from(corrected.expenseDate());
        MonthlyBudget oldBudget = budgets.find(owner, oldPeriod).orElseGet(() -> emptyBudget(owner, oldPeriod));
        budgets.save(oldBudget.subtract(previous.amount()));
        MonthlyBudget target = budgets.find(owner, newPeriod).orElseGet(() -> emptyBudget(owner, newPeriod));
        MonthlyBudget updated = target.add(corrected.amount());
        budgets.save(updated);
        expenses.update(corrected);
        events.publishEvent(new ExpenseCorrected(corrected.id(), owner, previous.amount(), corrected.amount(), corrected.expenseDate()));
        if (!target.exceeded() && updated.exceeded())
            events.publishEvent(new LimitExceeded(owner, newPeriod, updated.spendingLimit(), updated.accumulated()));
        return corrected;
    }

    public void delete(UUID owner, UUID expenseId) {
        ownerLock.acquire(owner);
        DeliveryExpense expense = owned(owner, expenseId);
        YearMonth period = YearMonth.from(expense.expenseDate());
        budgets.find(owner, period).ifPresent(budget -> budgets.save(budget.subtract(expense.amount())));
        expenses.delete(expense);
        events.publishEvent(new ExpenseDeleted(expense.id(), owner, expense.amount(), expense.expenseDate()));
    }

    private DeliveryExpense owned(UUID owner, UUID expenseId) {
        return expenses.findOwned(owner, expenseId)
                .orElseThrow(() -> new DeliveryFailure(404, "delivery.expense.not_found", "Expense not found."));
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
    public record ExpenseCorrected(UUID expenseId, UUID ownerId, BigDecimal previousAmount, BigDecimal amount, LocalDate date) { }
    public record ExpenseDeleted(UUID expenseId, UUID ownerId, BigDecimal amount, LocalDate date) { }
    public record LimitExceeded(UUID ownerId, YearMonth period, BigDecimal limit, BigDecimal accumulated) { }
}
