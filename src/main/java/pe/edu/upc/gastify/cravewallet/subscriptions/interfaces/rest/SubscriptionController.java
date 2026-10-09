package pe.edu.upc.gastify.cravewallet.subscriptions.interfaces.rest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionApplicationService;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {
    private final SubscriptionApplicationService service;
    public SubscriptionController(SubscriptionApplicationService service) { this.service = service; }

    @PostMapping
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
        // Totales solo de resultados activos filtrados. No sumar monedas distintas ni inventar cotizaciones.
        for (Subscription s : items) if (s.status() == Subscription.Status.ACTIVE) {
            BigDecimal normalized = s.billingCycle() == Subscription.BillingCycle.ANNUAL
                    ? s.amount().divide(BigDecimal.valueOf(12), 8, RoundingMode.HALF_UP) : s.amount();
            monthly.merge(s.currency(), normalized, BigDecimal::add);
        }
        monthly.replaceAll((currency, value) -> value.setScale(2, RoundingMode.HALF_UP));
        return new Portfolio(items.stream().map(Resource::from).toList(), monthly);
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
    public Resource cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return Resource.from(service.cancel(owner(jwt), id));
    }

    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
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
    public record Portfolio(List<Resource> items, Map<Subscription.Currency, BigDecimal> monthlyTotalsByCurrency) { }
}
