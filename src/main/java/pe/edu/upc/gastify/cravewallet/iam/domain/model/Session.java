package pe.edu.upc.gastify.cravewallet.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Session(UUID id, UUID userId, String refreshHash, Instant expiresAt, boolean revoked) {
    public boolean activeAt(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }

    public Session revoke() {
        return new Session(id, userId, refreshHash, expiresAt, true);
    }
}
