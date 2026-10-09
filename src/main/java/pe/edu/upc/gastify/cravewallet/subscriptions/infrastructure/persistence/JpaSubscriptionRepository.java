package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.repositories.SubscriptionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaSubscriptionRepository implements SubscriptionRepository {
    private final EntityManager em;
    public JpaSubscriptionRepository(EntityManager em) { this.em = em; }
    public List<Subscription> findOwned(UUID owner) {
        return em.createQuery("select s from SubscriptionEntity s where s.ownerId = :owner order by s.nextBillingDate, s.id", SubscriptionEntity.class)
                .setParameter("owner", owner).getResultList().stream().map(SubscriptionEntity::toDomain).toList();
    }
    public Optional<Subscription> findOwned(UUID id, UUID owner) { return find(id, owner, false); }
    public Optional<Subscription> findOwnedForUpdate(UUID id, UUID owner) { return find(id, owner, true); }
    private Optional<Subscription> find(UUID id, UUID owner, boolean lock) {
        var query = em.createQuery("select s from SubscriptionEntity s where s.id = :id and s.ownerId = :owner", SubscriptionEntity.class)
                .setParameter("id", id).setParameter("owner", owner);
        if (lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultStream().findFirst().map(SubscriptionEntity::toDomain);
    }
    public long countActive(UUID owner) {
        return em.createQuery("select count(s) from SubscriptionEntity s where s.ownerId = :owner and s.status = :status", Long.class)
                .setParameter("owner", owner).setParameter("status", Subscription.Status.ACTIVE).getSingleResult();
    }
    public Subscription save(Subscription value) {
        SubscriptionEntity entity = em.find(SubscriptionEntity.class, value.id());
        if (entity == null) em.persist(new SubscriptionEntity(value)); else entity.update(value);
        em.flush();
        return value;
    }
}
