package pe.edu.upc.gastify.cravewallet.launch.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.launch.domain.model.LaunchSubscriber;
import pe.edu.upc.gastify.cravewallet.launch.domain.repositories.LaunchSubscriberRepository;

@Repository
public class JpaLaunchSubscriberRepository implements LaunchSubscriberRepository {
    private final EntityManager em;

    public JpaLaunchSubscriberRepository(EntityManager em) { this.em = em; }

    public boolean existsByEmail(String normalizedEmail) {
        return !em.createQuery("select s.id from LaunchSubscriberEntity s where s.email = :email", java.util.UUID.class)
                .setParameter("email", normalizedEmail).setMaxResults(1).getResultList().isEmpty();
    }

    public void save(LaunchSubscriber subscriber) { em.persist(new LaunchSubscriberEntity(subscriber)); em.flush(); }
}
