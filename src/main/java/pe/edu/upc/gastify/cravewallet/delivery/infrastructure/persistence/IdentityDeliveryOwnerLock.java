package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence.UserEntity;
import pe.edu.upc.gastify.cravewallet.delivery.application.*;
import java.util.UUID;

@Component
public class IdentityDeliveryOwnerLock implements DeliveryOwnerLock {
    private final EntityManager em;
    public IdentityDeliveryOwnerLock(EntityManager em) { this.em = em; }
    public void acquire(UUID owner) {
        if (em.find(UserEntity.class, owner, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new DeliveryFailure(401, "Usuario no disponible.");
    }
}
