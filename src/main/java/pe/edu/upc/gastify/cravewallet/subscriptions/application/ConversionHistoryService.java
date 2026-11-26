package pe.edu.upc.gastify.cravewallet.subscriptions.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRateHistoryPort;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.SubscriptionTermsHistoryPort;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.SubscriptionTermsHistoryPort.Terms;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Rebuilds the exchange rate applied to each past billing cycle of a USD subscription (US29)
 * and the variation against the previous charge (US37).
 *
 * <p>Each cycle uses the amount and schedule that were in force on its billing date (from the
 * terms history) and the last stored quote on that date, so editing a subscription never
 * rewrites past charges.
 */
@Service
@Transactional(readOnly = true)
public class ConversionHistoryService {
    static final int MAX_CYCLES = 12;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final SubscriptionApplicationService subscriptions;
    private final ExchangeRateHistoryPort history;
    private final SubscriptionTermsHistoryPort termsHistory;
    private final Clock clock;

    public ConversionHistoryService(SubscriptionApplicationService subscriptions, ExchangeRateHistoryPort history,
                                    SubscriptionTermsHistoryPort termsHistory, Clock clock) {
        this.subscriptions = subscriptions; this.history = history; this.termsHistory = termsHistory; this.clock = clock;
    }

    public History of(UUID owner, UUID subscriptionId) {
        Subscription subscription = subscriptions.detail(owner, subscriptionId);
        if (subscription.currency() != Subscription.Currency.USD)
            return new History(subscription.id(), subscription.currency().name(), subscription.amount(), List.of(), null);
        Optional<Instant> firstQuote = history.earliest();
        List<Terms> versions = termsHistory.find(subscription.id());
        if (firstQuote.isEmpty() || versions.isEmpty())
            return new History(subscription.id(), "USD", subscription.amount(), List.of(), null);

        LocalDate today = LocalDate.now(clock.withZone(LIMA));
        LocalDate lastBillable = subscription.cancelledAt() == null ? today
                : min(today, subscription.cancelledAt().atZone(LIMA).toLocalDate());
        LocalDate firstBillable = firstQuote.get().atZone(LIMA).toLocalDate();

        TreeMap<LocalDate, BigDecimal> charges = new TreeMap<>();
        for (int index = 0; index < versions.size(); index++) {
            Terms version = versions.get(index);
            // The first known version also explains cycles before it was recorded (bounded by the first quote).
            LocalDate from = index == 0 ? firstBillable : max(firstBillable, version.effectiveFrom().atZone(LIMA).toLocalDate());
            LocalDate until = index + 1 < versions.size()
                    ? min(lastBillable, versions.get(index + 1).effectiveFrom().atZone(LIMA).toLocalDate().minusDays(1))
                    : lastBillable;
            for (LocalDate date : billingDates(version, from, until)) charges.put(date, version.amount());
        }

        List<Cycle> cycles = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> charge : charges.descendingMap().entrySet()) {
            if (cycles.size() == MAX_CYCLES) break;
            Instant endOfDay = charge.getKey().plusDays(1).atStartOfDay(LIMA).toInstant().minusNanos(1);
            history.latestAtOrBefore(endOfDay).ifPresent(rate -> cycles.add(0, new Cycle(charge.getKey(), charge.getValue(),
                    rate.rate(), charge.getValue().multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP), rate.updatedAt())));
        }
        return new History(subscription.id(), "USD", subscription.amount(), cycles, variation(cycles));
    }

    /** Billing dates of one version of the terms that fall within [from, until], anchored on its next billing date. */
    public static List<LocalDate> billingDates(Terms version, LocalDate from, LocalDate until) {
        List<LocalDate> dates = new ArrayList<>();
        if (until.isBefore(from)) return dates;
        boolean monthly = version.billingCycle() == Subscription.BillingCycle.MONTHLY;
        LocalDate anchor = version.nextBillingDate();
        long span = monthly ? Period.between(from, anchor).toTotalMonths() + 1 : Period.between(from, anchor).getYears() + 1;
        for (long step = -Math.abs(span) - 1; ; step++) {
            LocalDate date = monthly ? anchor.plusMonths(step) : anchor.plusYears(step);
            if (date.isAfter(until)) break;
            if (!date.isBefore(from)) dates.add(date);
        }
        return dates;
    }

    private static LocalDate min(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }
    private static LocalDate max(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }

    private Variation variation(List<Cycle> cycles) {
        if (cycles.size() < 2) return null;
        Cycle previous = cycles.get(cycles.size() - 2), latest = cycles.get(cycles.size() - 1);
        BigDecimal percent = latest.rate().subtract(previous.rate())
                .multiply(BigDecimal.valueOf(100)).divide(previous.rate(), 2, RoundingMode.HALF_UP);
        String direction = percent.signum() > 0 ? "UP" : percent.signum() < 0 ? "DOWN" : "UNCHANGED";
        return new Variation(previous.billingDate(), latest.billingDate(), previous.rate(), latest.rate(), percent, direction);
    }

    public record Cycle(LocalDate billingDate, BigDecimal amountUsd, BigDecimal rate, BigDecimal amountPen, Instant rateUpdatedAt) { }
    public record Variation(LocalDate previousBillingDate, LocalDate latestBillingDate, BigDecimal previousRate,
                            BigDecimal latestRate, BigDecimal percentChange, String direction) { }
    public record History(UUID subscriptionId, String currency, BigDecimal amount, List<Cycle> cycles, Variation variation) { }
}
