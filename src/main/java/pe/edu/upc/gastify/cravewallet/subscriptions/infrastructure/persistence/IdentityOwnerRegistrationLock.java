package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence.UserEntity;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.OwnerRegistrationLock;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.SubscriptionFailure;
import java.util.UUID;

/** Adaptador técnico a IAM; el dominio de suscripciones no depende de sus entidades. */
@Component
public class IdentityOwnerRegistrationLock implements OwnerRegistrationLock {
    private final EntityManager em;
    public IdentityOwnerRegistrationLock(EntityManager em) { this.em = em; }
    public void acquire(UUID owner) {
        if (em.find(UserEntity.class, owner, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new SubscriptionFailure(401, "Usuario no disponible.");
    }
}
