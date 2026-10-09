package pe.edu.upc.gastify.cravewallet.iam.interfaces.rest.resources;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AuthResources {
    private AuthResources() { }
    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 72) String password) { }
    public record RefreshRequest(@NotBlank @Size(max = 256) String refreshToken) { }
    public record ProfileRequest(@NotBlank @Pattern(regexp = "PEN") String referenceCurrency) { }
    public record ProfileResource(UUID id, String email, String referenceCurrency) { }
    public record AuthenticationResource(String accessToken, String refreshToken, String tokenType,
                                         Instant expiresAt, ProfileResource user) { }
}
