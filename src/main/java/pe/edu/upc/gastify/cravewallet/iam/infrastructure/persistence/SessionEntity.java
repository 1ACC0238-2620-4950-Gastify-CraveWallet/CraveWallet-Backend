package pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.Session;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "iam_sessions")
public class SessionEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "refresh_hash", nullable = false, unique = true, length = 64) private String refreshHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked;
    protected SessionEntity() { }
    public SessionEntity(Session session) {
        id = session.id(); userId = session.userId(); refreshHash = session.refreshHash();
        expiresAt = session.expiresAt(); revoked = session.revoked();
    }
    public Session toDomain() { return new Session(id, userId, refreshHash, expiresAt, revoked); }
}
