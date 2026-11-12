package pe.edu.upc.gastify.cravewallet.subscriptions.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionApplicationService;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.ExchangeRateService;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionFailure;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final SubscriptionApplicationService service;
    private final ExchangeRateService exchange;
    public SubscriptionController(SubscriptionApplicationService service, ExchangeRateService exchange) { this.service = service; this.exchange = exchange; }

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Suscripción registrada")
    public ResponseEntity<Resource> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest request) {
        Subscription result = service.register(owner(jwt), request.name(), request.amount(), request.currency(),
                request.category(), request.billingCycle(), request.nextBillingDate());
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + result.id())).body(Resource.from(result));
    }

    @GetMapping
    public Portfolio list(@AuthenticationPrincipal Jwt jwt,
                          @RequestParam(defaultValue = "ACTIVE") Subscription.Status status,
                          @RequestParam(required = false) String search, @RequestParam(required = false) String category) {
        List<Subscription> items = service.list(owner(jwt), status, search, category);
        Map<Subscription.Currency, BigDecimal> monthly = new EnumMap<>(Subscription.Currency.class);
        Map<String, Map<Subscription.Currency, BigDecimal>> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Subscription s : items) {
            if (s.status() != Subscription.Status.ACTIVE) continue;
            BigDecimal normalized = s.billingCycle() == Subscription.BillingCycle.ANNUAL
                    ? s.amount().divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP) : s.amount();
            monthly.merge(s.currency(), normalized, BigDecimal::add);
            byCategory.computeIfAbsent(s.category(), key -> new EnumMap<>(Subscription.Currency.class))
                    .merge(s.currency(), normalized, BigDecimal::add);
        }
        ExchangeRateService.Rate rate = monthly.containsKey(Subscription.Currency.USD) ? usdToPenQuote() : null;
        BigDecimal totalPen = monthlyTotalPen(monthly, rate);
        List<CategoryTotal> categories = byCategory.entrySet().stream()
                .map(entry -> {
                    var totals = entry.getValue();
                    BigDecimal subtotalPen = monthlyTotalPen(totals, rate);
                    return new CategoryTotal(entry.getKey(), roundMonthlyTotals(totals), subtotalPen);
                }).toList();
        return new Portfolio(items.stream().map(Resource::from).toList(), roundMonthlyTotals(monthly),
                totalPen, totalPen != null, rate, categories);
    }

    private ExchangeRateService.Rate usdToPenQuote() {
        try {
            return exchange.quote(Subscription.Currency.USD, Subscription.Currency.PEN);
        } catch (SubscriptionFailure unavailable) {
            return null;
        }
    }

    private static BigDecimal monthlyTotalPen(Map<Subscription.Currency, BigDecimal> totals, ExchangeRateService.Rate rate) {
        BigDecimal pen = totals.getOrDefault(Subscription.Currency.PEN, BigDecimal.ZERO);
        BigDecimal usd = totals.get(Subscription.Currency.USD);
        if (usd == null) return pen.setScale(2, RoundingMode.HALF_UP);
        if (rate == null) return null;
        return pen.add(usd.multiply(rate.rate())).setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<Subscription.Currency, BigDecimal> roundMonthlyTotals(Map<Subscription.Currency, BigDecimal> totals) {
        totals.replaceAll((currency, value) -> value.setScale(2, RoundingMode.HALF_UP));
        return totals;
    }

    @GetMapping("/{id}")
    public Resource detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Resource.from(service.detail(owner(jwt), id));
    }

    @PatchMapping("/{id}")
    public Resource edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody EditRequest request) {
        return Resource.from(service.edit(owner(jwt), id, request.amount(), request.category(), request.nextBillingDate()));
    }

    @PostMapping("/{id}/cancel")
    public CancellationResource cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Subscription cancelled = service.cancel(owner(jwt), id);
        return new CancellationResource(Resource.from(cancelled), estimatedSavings(cancelled));
    }

    private EstimatedSavings estimatedSavings(Subscription cancelled) {
        LocalDate cancellationDate = cancelled.cancelledAt().atZone(LIMA).toLocalDate();
        if (!cancellationDate.isBefore(cancelled.nextBillingDate())) return null;

        BigDecimal amount = cancelled.amount().setScale(2, RoundingMode.HALF_UP);
        if (cancelled.currency() == Subscription.Currency.PEN) {
            return new EstimatedSavings(amount, cancelled.currency(), amount, null);
        }
        ExchangeRateService.Rate rate = usdToPenQuote();
        BigDecimal amountPen = rate == null ? null : amount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP);
        return new EstimatedSavings(amount, cancelled.currency(), amountPen, rate);
    }

    @GetMapping("/{id}/reminder")
    public ReminderResource reminder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        Subscription s = service.detail(owner(jwt), id);
        if (s.status() != Subscription.Status.ACTIVE) throw new SubscriptionFailure(409, "Una suscripción cancelada no genera recordatorios.");
        var billingAt = s.nextBillingDate().atStartOfDay(LIMA);
        return new ReminderResource("Renovación: " + s.name(), billingAt.minusHours(24).toInstant(),
                billingAt.toInstant(), "America/Lima", "Revisa la renovación de " + s.name() + ": " + s.amount() + " " + s.currency());
    }

    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @io.swagger.v3.oas.annotations.media.Schema(name = "SubscriptionRegisterRequest")
    public record RegisterRequest(@NotBlank @Size(max = 100) String name,
                                  @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                  @NotNull Subscription.Currency currency,
                                  @NotBlank @Size(max = 60) String category,
                                  @NotNull Subscription.BillingCycle billingCycle,
                                  @NotNull LocalDate nextBillingDate) { }
    public record EditRequest(@DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                              @Size(max = 60) @Pattern(regexp = ".*\\S.*") String category,
                              LocalDate nextBillingDate) { }
    public record Resource(UUID id, String name, BigDecimal amount, Subscription.Currency currency,
                           String category, Subscription.BillingCycle billingCycle, LocalDate nextBillingDate,
                           Subscription.Status status, Instant cancelledAt) {
        static Resource from(Subscription s) { return new Resource(s.id(), s.name(), s.amount(), s.currency(),
                s.category(), s.billingCycle(), s.nextBillingDate(), s.status(), s.cancelledAt()); }
    }
    public record Portfolio(List<Resource> items, Map<Subscription.Currency, BigDecimal> monthlyTotalsByCurrency,
                            BigDecimal monthlyTotalPen, boolean conversionAvailable, ExchangeRateService.Rate exchangeRate,
                            List<CategoryTotal> monthlyTotalsByCategory) { }
    public record CategoryTotal(String category, Map<Subscription.Currency, BigDecimal> monthlyTotalsByCurrency,
                                BigDecimal monthlyTotalPen) { }
    public record CancellationResource(@JsonUnwrapped Resource subscription, EstimatedSavings estimatedSavings) { }
    public record EstimatedSavings(BigDecimal amount, Subscription.Currency currency, BigDecimal amountPen,
                                   ExchangeRateService.Rate exchangeRate) { }
    public record ReminderResource(String title, Instant reminderAt, Instant billingAt, String timeZone, String description) { }
}
