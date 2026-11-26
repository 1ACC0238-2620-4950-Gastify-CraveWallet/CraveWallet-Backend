package pe.edu.upc.gastify.cravewallet.subscriptions.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRateHistoryPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rebuilds the exchange rate applied to each past billing cycle of a USD subscription (US29)
 * and the variation against the previous charge (US37), using the quotes stored since the
 * history was enabled.
 */
@Service
@Transactional(readOnly = true)
public class ConversionHistoryService {
    static final int MAX_CYCLES = 12;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final SubscriptionApplicationService subscriptions;
    private final ExchangeRateHistoryPort history;
    private final Clock clock;

    public ConversionHistoryService(SubscriptionApplicationService subscriptions, ExchangeRateHistoryPort history, Clock clock) {
        this.subscriptions = subscriptions; this.history = history; this.clock = clock;
    }

    public History of(UUID owner, UUID subscriptionId) {
        Subscription subscription = subscriptions.detail(owner, subscriptionId);
        if (subscription.currency() != Subscription.Currency.USD)
            return new History(subscription.id(), subscription.currency().name(), subscription.amount(), List.of(), null);
        LocalDate today = LocalDate.now(clock.withZone(LIMA));
        LocalDate firstQuote = history.earliest().map(instant -> instant.atZone(LIMA).toLocalDate()).orElse(null);
        List<Cycle> cycles = new ArrayList<>();
        if (firstQuote != null) {
            for (int step = 1; step <= MAX_CYCLES; step++) {
                LocalDate billingDate = subscription.billingCycle() == Subscription.BillingCycle.MONTHLY
                        ? subscription.nextBillingDate().minusMonths(step)
                        : subscription.nextBillingDate().minusYears(step);
                if (billingDate.isAfter(today)) continue;
                if (billingDate.isBefore(firstQuote)) break;
                Instant endOfDay = billingDate.plusDays(1).atStartOfDay(LIMA).toInstant().minusNanos(1);
                history.latestAtOrBefore(endOfDay).ifPresent(rate -> cycles.add(0, new Cycle(billingDate, rate.rate(),
                        subscription.amount().multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP), rate.updatedAt())));
            }
        }
        return new History(subscription.id(), "USD", subscription.amount(), cycles, variation(cycles));
    }

    private Variation variation(List<Cycle> cycles) {
        if (cycles.size() < 2) return null;
        Cycle previous = cycles.get(cycles.size() - 2), latest = cycles.get(cycles.size() - 1);
        BigDecimal percent = latest.rate().subtract(previous.rate())
                .multiply(BigDecimal.valueOf(100)).divide(previous.rate(), 2, RoundingMode.HALF_UP);
        String direction = percent.signum() > 0 ? "UP" : percent.signum() < 0 ? "DOWN" : "UNCHANGED";
        return new Variation(previous.billingDate(), latest.billingDate(), previous.rate(), latest.rate(), percent, direction);
    }

    public record Cycle(LocalDate billingDate, BigDecimal rate, BigDecimal amountPen, Instant rateUpdatedAt) { }
    public record Variation(LocalDate previousBillingDate, LocalDate latestBillingDate, BigDecimal previousRate,
                            BigDecimal latestRate, BigDecimal percentChange, String direction) { }
    public record History(UUID subscriptionId, String currency, BigDecimal amount, List<Cycle> cycles, Variation variation) { }
}
