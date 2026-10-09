package pe.edu.upc.gastify.cravewallet.iam.domain.services;

import java.time.Instant;
import java.util.UUID;

public interface TokenService {
    AccessToken issue(UUID userId, UUID sessionId);
    record AccessToken(String value, Instant expiresAt) { }
}
