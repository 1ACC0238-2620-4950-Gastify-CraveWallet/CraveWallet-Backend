package pe.edu.upc.gastify.cravewallet.launch.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.launch.application.LaunchSubscriptionApplicationService;

@RestController @RequestMapping("/api/v1/launch-subscribers")
public class LaunchSubscriberController {
    private final LaunchSubscriptionApplicationService service;

    public LaunchSubscriberController(LaunchSubscriptionApplicationService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Ask to be notified about the launch",
            description = "Public endpoint used by the landing page (US40). Stores the address once; repeating the request returns the same response.")
    @ApiResponse(responseCode = "202", description = "Address registered")
    @ApiResponse(responseCode = "400", description = "Invalid email address or locale")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request) {
        service.subscribe(request.email(), request.locale() == null ? "es" : request.locale());
        return ResponseEntity.accepted().build();
    }

    public record SubscribeRequest(
            @NotBlank @Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$") @Size(max = 254) String email,
            @Pattern(regexp = "en|es") String locale) { }
}
