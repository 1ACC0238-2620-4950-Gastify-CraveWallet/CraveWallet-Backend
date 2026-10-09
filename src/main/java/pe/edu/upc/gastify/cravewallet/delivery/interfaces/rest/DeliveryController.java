package pe.edu.upc.gastify.cravewallet.delivery.interfaces.rest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.delivery.application.*;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/delivery-expenses") @SecurityRequirement(name = "bearerAuth")
public class DeliveryController {
    private final DeliveryApplicationService service;
    public DeliveryController(DeliveryApplicationService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ExpenseResource> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest request) {
        if (request.expenseDate().getYear() < 1900 || request.expenseDate().getYear() > 9999)
            throw new DeliveryFailure(400, "Año fuera de rango.");
        var result = service.register(owner(jwt), request.requestId(), request.merchant(), request.amount(), request.category(), request.expenseDate());
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(ExpenseResource.from(result.expense()));
    }
    @GetMapping("/summary")
    public DeliveryApplicationService.Summary summary(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) Integer year,
                                                      @RequestParam(required = false) String month) {
        YearMonth now = YearMonth.now(ZoneId.of("America/Lima"));
        if (year == null && (month == null || month.equals("actual"))) return service.summary(owner(jwt), now);
        if (year == null || month == null || month.equals("actual")) throw new DeliveryFailure(400, "Indica año y mes numérico juntos.");
        int monthNumber;
        try { monthNumber = Integer.parseInt(month); } catch (NumberFormatException failure) { throw new DeliveryFailure(400, "Mes no válido."); }
        return service.summary(owner(jwt), period(year, monthNumber));
    }
    @PutMapping("/budget")
    public BudgetResource budget(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        var result = service.setBudget(owner(jwt), period(request.year(), request.month()), request.spendingLimit());
        return new BudgetResource(result.period().getYear(), result.period().getMonthValue(), "PEN", result.spendingLimit(),
                result.accumulated(), result.spendingLimit() == null ? null : result.spendingLimit().subtract(result.accumulated()), result.exceeded());
    }
    private YearMonth period(int year, int month) {
        if (year < 1900 || year > 9999 || month < 1 || month > 12) throw new DeliveryFailure(400, "Período no válido.");
        return YearMonth.of(year, month);
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    public record RegisterRequest(@NotNull UUID requestId, @NotBlank @Size(max = 100) String merchant,
                                  @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 60) String category, @NotNull LocalDate expenseDate) { }
    public record BudgetRequest(@NotNull @Min(1900) @Max(9999) Integer year, @NotNull @Min(1) @Max(12) Integer month,
                                @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal spendingLimit) { }
    public record ExpenseResource(UUID id, UUID requestId, String merchant, BigDecimal amount, String currency, String category, LocalDate expenseDate) {
        static ExpenseResource from(DeliveryExpense e) { return new ExpenseResource(e.id(), e.requestId(), e.merchant(), e.amount(), "PEN", e.category(), e.expenseDate()); }
    }
    public record BudgetResource(int year, int month, String currency, BigDecimal spendingLimit, BigDecimal accumulated, BigDecimal remaining, boolean exceeded) { }
}
