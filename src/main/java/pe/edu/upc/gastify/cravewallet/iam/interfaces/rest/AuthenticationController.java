package pe.edu.upc.gastify.cravewallet.iam.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.iam.application.AuthApplicationService;
import pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.resources.AuthResources.*;
import pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.transform.AuthResourceAssembler;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthApplicationService service;
    public AuthenticationController(AuthApplicationService service) { this.service = service; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar cuenta e iniciar sesión — US01 / TS01")
    public AuthenticationResource register(@Valid @RequestBody Credentials resource) {
        return AuthResourceAssembler.authentication(service.register(resource.email(), resource.password()));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión — US02 / TS01")
    public AuthenticationResource login(@Valid @RequestBody Credentials resource) {
        return AuthResourceAssembler.authentication(service.login(resource.email(), resource.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar y rotar el par de tokens; invalida la sesión anterior")
    public AuthenticationResource refresh(@Valid @RequestBody RefreshRequest resource) {
        return AuthResourceAssembler.authentication(service.refresh(resource.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cerrar la sesión actual e invalidar ambos tokens — US33")
    public void logout(@AuthenticationPrincipal Jwt jwt) {
        service.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()));
    }
}
