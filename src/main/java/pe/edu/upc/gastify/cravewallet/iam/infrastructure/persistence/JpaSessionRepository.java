package pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.Session;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.SessionRepository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaSessionRepository implements SessionRepository {
    private final SpringDataSessionRepository repository;
    public JpaSessionRepository(SpringDataSessionRepository repository) { this.repository = repository; }
    public Optional<Session> findById(UUID id) { return repository.findById(id).map(SessionEntity::toDomain); }
    public Optional<Session> findByRefreshHashForUpdate(String hash) {
        return repository.findByRefreshHash(hash).map(SessionEntity::toDomain);
    }
    public void save(Session session) { repository.saveAndFlush(new SessionEntity(session)); }
}
