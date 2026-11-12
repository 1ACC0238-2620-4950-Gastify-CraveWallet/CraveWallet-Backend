
package pe.edu.upc.gastify.cravewallet.premium.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import pe.edu.upc.gastify.cravewallet.premium.application.PremiumApplicationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/premium")
@SecurityRequirement(name = "bearerAuth")
public class PremiumController {

    private final PremiumApplicationService service;

    public PremiumController(PremiumApplicationService service) {
        this.service = service;
    }

    @GetMapping("/status")
    @Operation(summary = "Consultar el estado del plan Premium")
    public PremiumApplicationService.PremiumStatusView getStatus(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return service.getStatus(ownerId);
    }
}
