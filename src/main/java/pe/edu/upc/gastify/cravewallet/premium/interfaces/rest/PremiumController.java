
package pe.edu.upc.gastify.cravewallet.premium.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import pe.edu.upc.gastify.cravewallet.premium.application.PremiumApplicationService;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumBillingService;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository.PaymentEntry;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/premium")
@SecurityRequirement(name = "bearerAuth")
public class PremiumController {

    private final PremiumApplicationService service;
    private final PremiumBillingService billing;

    public PremiumController(PremiumApplicationService service, PremiumBillingService billing) {
        this.service = service;
        this.billing = billing;
    }

    @GetMapping("/status")
    @Operation(summary = "Get Premium plan status")
    public PremiumApplicationService.PremiumStatusView getStatus(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getStatus(owner(jwt));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Open a recurring Mercado Pago checkout",
            description = "No request body required. Amount and payer email are determined by the backend.")
    public PremiumBillingService.CheckoutView checkout(@AuthenticationPrincipal Jwt jwt) {
        return billing.checkout(owner(jwt));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel renewal and keep the paid period",
            description = "Returns the local status after verifying remote cancellation.")
    public PremiumApplicationService.PremiumStatusView cancel(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = owner(jwt);
        billing.cancel(ownerId);
        return service.getStatus(ownerId);
    }

    @GetMapping("/payments")
    @Operation(summary = "Get payment history for the authenticated user")
    public List<PaymentEntry> payments(@AuthenticationPrincipal Jwt jwt) {
        return billing.payments(owner(jwt));
    }

    private static UUID owner(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
