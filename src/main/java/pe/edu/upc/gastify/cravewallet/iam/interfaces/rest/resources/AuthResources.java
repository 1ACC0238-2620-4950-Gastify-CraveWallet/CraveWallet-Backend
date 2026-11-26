package pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AuthResources {
    private AuthResources() { }
    public record Credentials(@NotBlank(message = "{validation.auth.email.required}")
                              @Email(message = "{validation.auth.email.invalid}")
                              @Size(max = 254, message = "{validation.auth.email.size}") String email,
                              @NotBlank(message = "{validation.auth.password.required}")
                              @Size(max = 72, message = "{validation.auth.password.size}") String password) { }
    public record RefreshRequest(@NotBlank(message = "{validation.auth.refresh_token.required}")
                                 @Size(max = 256, message = "{validation.auth.refresh_token.size}") String refreshToken) { }
    public record ProfileRequest(@NotBlank(message = "{validation.auth.reference_currency.required}")
                                 @Pattern(regexp = "PEN", message = "{validation.auth.reference_currency.pen}") String referenceCurrency) { }
    public record ProfileResource(UUID id, String email, String referenceCurrency) { }
    public record AuthenticationResource(String accessToken, String refreshToken, String tokenType,
                                         Instant expiresAt, ProfileResource user) { }
}
