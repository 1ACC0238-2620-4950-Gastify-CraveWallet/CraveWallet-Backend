package pe.edu.upc.gastify.cravewallet.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.iam.application.AuthApplicationService;
import pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.resources.AuthResources.*;
import pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.transform.AuthResourceAssembler;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final AuthApplicationService service;
    public UserController(AuthApplicationService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Consultar el perfil de la identidad autenticada")
    public ProfileResource profile(@AuthenticationPrincipal Jwt jwt) {
        return AuthResourceAssembler.profile(service.profile(UUID.fromString(jwt.getSubject())));
    }

    @PatchMapping
    @Operation(summary = "Confirmar PEN como moneda de referencia — US03 / TS01")
    public ProfileResource update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest resource) {
        return AuthResourceAssembler.profile(service.updateProfile(UUID.fromString(jwt.getSubject()), resource.referenceCurrency()));
    }
}
