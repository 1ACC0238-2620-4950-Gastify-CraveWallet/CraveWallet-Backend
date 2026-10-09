package pe.edu.upc.gastify.cravewallet.iam.domain.repositories;

import pe.edu.upc.gastify.cravewallet.iam.domain.model.Session;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    Optional<Session> findById(UUID id);
    /** El adaptador bloquea el registro durante renovación para impedir uso concurrente. */
    Optional<Session> findByRefreshHashForUpdate(String hash);
    void save(Session session);
}
