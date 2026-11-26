package pe.edu.upc.gastify.cravewallet.delivery.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/delivery-expenses") @SecurityRequirement(name = "bearerAuth")
public class DeliveryController {
    private final DeliveryApplicationService service;
    private final MerchantSearchApplicationService merchantSearch;

    public DeliveryController(DeliveryApplicationService service, MerchantSearchApplicationService merchantSearch) {
        this.service = service;
        this.merchantSearch = merchantSearch;
    }

    @GetMapping("/merchants/suggestions")
    @Operation(summary = "Search merchant suggestions in Lima",
            description = "Searches by name (2 to 100 characters). Returns an empty list if there are no suggestions.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid search query")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "A valid JWT is required")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Provider unavailable")
    public List<MerchantSuggestion> suggestMerchants(@RequestParam String query) {
        return merchantSearch.search(query);
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Expense registered")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Idempotent retry acknowledged")
    public ResponseEntity<ExpenseResource> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequest request) {
        if (request.expenseDate().getYear() < 1900 || request.expenseDate().getYear() > 9999)
            throw new DeliveryFailure(400, "delivery.expense.year_out_of_range", "Year is out of range.");
        var result = service.register(owner(jwt), request.requestId(), request.merchant(), request.amount(), request.category(), request.expenseDate());
        return ResponseEntity.status(result.replayed() ? 200 : 201).body(ExpenseResource.from(result.expense()));
    }
    @GetMapping
    @Operation(summary = "List delivery expenses of a month",
            description = "Returns the authenticated user's expenses for the given year and month (current month in Lima when omitted), ordered by date.")
    public List<ExpenseResource> list(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) Integer year,
                                      @RequestParam(required = false) Integer month) {
        if ((year == null) != (month == null))
            throw new DeliveryFailure(400, "delivery.summary.year_and_month_required", "Provide numeric year and month together.");
        YearMonth period = year == null ? YearMonth.now(ZoneId.of("America/Lima")) : period(year, month);
        return service.list(owner(jwt), period).stream().map(ExpenseResource::from).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Correct a delivery expense",
            description = "Replaces merchant, amount, category and date of an expense registered by mistake. The monthly accumulated amount is moved accordingly.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expense corrected")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found for the authenticated user")
    public ExpenseResource correct(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody CorrectionRequest request) {
        if (request.expenseDate().getYear() < 1900 || request.expenseDate().getYear() > 9999)
            throw new DeliveryFailure(400, "delivery.expense.year_out_of_range", "Year is out of range.");
        return ExpenseResource.from(service.correct(owner(jwt), id, request.merchant(), request.amount(), request.category(), request.expenseDate()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a delivery expense",
            description = "Deletes an expense registered by mistake and subtracts it from the monthly accumulated amount.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Expense deleted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found for the authenticated user")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(owner(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/summary")
    public DeliveryApplicationService.Summary summary(@AuthenticationPrincipal Jwt jwt,
                                                      @RequestParam(required = false) Integer year,
                                                      @RequestParam(required = false) String month) {
        YearMonth now = YearMonth.now(ZoneId.of("America/Lima"));
        if (year == null && (month == null || month.equals("actual"))) return service.summary(owner(jwt), now);
        if (year == null || month == null || month.equals("actual"))
            throw new DeliveryFailure(400, "delivery.summary.year_and_month_required", "Provide numeric year and month together.");
        int monthNumber;
        try { monthNumber = Integer.parseInt(month); } catch (NumberFormatException failure) {
            throw new DeliveryFailure(400, "delivery.summary.month_invalid", "Invalid month.");
        }
        return service.summary(owner(jwt), period(year, monthNumber));
    }
    @PutMapping("/budget")
    public BudgetResource budget(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        var result = service.setBudget(owner(jwt), period(request.year(), request.month()), request.spendingLimit());
        return new BudgetResource(result.period().getYear(), result.period().getMonthValue(), "PEN", result.spendingLimit(),
                result.accumulated(), result.spendingLimit() == null ? null : result.spendingLimit().subtract(result.accumulated()), result.exceeded());
    }
    private YearMonth period(int year, int month) {
        if (year < 1900 || year > 9999 || month < 1 || month > 12)
            throw new DeliveryFailure(400, "delivery.period.invalid", "Invalid period.");
        return YearMonth.of(year, month);
    }
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @io.swagger.v3.oas.annotations.media.Schema(name = "DeliveryRegisterRequest")
    public record RegisterRequest(@NotNull(message = "{validation.delivery.request_id.required}") UUID requestId,
                                  @NotBlank(message = "{validation.delivery.merchant.required}")
                                  @Size(max = 100, message = "{validation.delivery.merchant.size}") String merchant,
                                  @NotNull(message = "{validation.delivery.amount.required}")
                                  @DecimalMin(value = "0", inclusive = false, message = "{validation.delivery.amount.positive}")
                                  @Digits(integer = 10, fraction = 2, message = "{validation.delivery.amount.digits}") BigDecimal amount,
                                  @NotBlank(message = "{validation.delivery.category.required}")
                                  @Size(max = 60, message = "{validation.delivery.category.size}") String category,
                                  @NotNull(message = "{validation.delivery.expense_date.required}") LocalDate expenseDate) { }
    @io.swagger.v3.oas.annotations.media.Schema(name = "DeliveryCorrectionRequest")
    public record CorrectionRequest(@NotBlank(message = "{validation.delivery.merchant.required}")
                                    @Size(max = 100, message = "{validation.delivery.merchant.size}") String merchant,
                                    @NotNull(message = "{validation.delivery.amount.required}")
                                    @DecimalMin(value = "0", inclusive = false, message = "{validation.delivery.amount.positive}")
                                    @Digits(integer = 10, fraction = 2, message = "{validation.delivery.amount.digits}") BigDecimal amount,
                                    @NotBlank(message = "{validation.delivery.category.required}")
                                    @Size(max = 60, message = "{validation.delivery.category.size}") String category,
                                    @NotNull(message = "{validation.delivery.expense_date.required}") LocalDate expenseDate) { }
    public record BudgetRequest(@NotNull(message = "{validation.delivery.year.required}")
                                @Min(value = 1900, message = "{validation.delivery.year.range}")
                                @Max(value = 9999, message = "{validation.delivery.year.range}") Integer year,
                                @NotNull(message = "{validation.delivery.month.required}")
                                @Min(value = 1, message = "{validation.delivery.month.range}")
                                @Max(value = 12, message = "{validation.delivery.month.range}") Integer month,
                                @DecimalMin(value = "0", inclusive = false, message = "{validation.delivery.spending_limit.positive}")
                                @Digits(integer = 10, fraction = 2, message = "{validation.delivery.spending_limit.digits}") BigDecimal spendingLimit) { }
    public record ExpenseResource(UUID id, UUID requestId, String merchant, BigDecimal amount, String currency, String category, LocalDate expenseDate) {
        static ExpenseResource from(DeliveryExpense e) { return new ExpenseResource(e.id(), e.requestId(), e.merchant(), e.amount(), "PEN", e.category(), e.expenseDate()); }
    }
    public record BudgetResource(int year, int month, String currency, BigDecimal spendingLimit, BigDecimal accumulated, BigDecimal remaining, boolean exceeded) { }
}
