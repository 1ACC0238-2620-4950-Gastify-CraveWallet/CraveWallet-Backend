package pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataSessionRepository extends JpaRepository<SessionEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SessionEntity> findByRefreshHash(String hash);
}
