
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
    @Operation(summary = "Consultar el estado del plan Premium")
    public PremiumApplicationService.PremiumStatusView getStatus(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return service.getStatus(owner(jwt));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Abrir un checkout recurrente de Mercado Pago",
            description = "No requiere cuerpo. El importe y el correo se determinan en el backend.")
    public PremiumBillingService.CheckoutView checkout(@AuthenticationPrincipal Jwt jwt) {
        return billing.checkout(owner(jwt));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancelar la renovación y conservar el período pagado",
            description = "Devuelve el estado local después de verificar la cancelación remota.")
    public PremiumApplicationService.PremiumStatusView cancel(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = owner(jwt);
        billing.cancel(ownerId);
        return service.getStatus(ownerId);
    }

    @GetMapping("/payments")
    @Operation(summary = "Consultar el historial de pagos del usuario autenticado")
    public List<PaymentEntry> payments(@AuthenticationPrincipal Jwt jwt) {
        return billing.payments(owner(jwt));
    }

    private static UUID owner(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
