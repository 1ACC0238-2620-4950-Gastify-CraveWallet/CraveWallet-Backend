
package pe.edu.upc.gastify.cravewallet.premium.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumMembership;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaPremiumMembershipRepository
        implements PremiumMembershipRepository {

    private final EntityManager em;

    public JpaPremiumMembershipRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<PremiumMembership> findByOwnerId(UUID ownerId) {

        return em.createQuery(
                        "SELECT m FROM PremiumMembershipEntity m " +
                                "WHERE m.ownerId = :ownerId",
                        PremiumMembershipEntity.class
                )
                .setParameter("ownerId", ownerId)
                .getResultStream()
                .findFirst()
                .map(PremiumMembershipEntity::toDomain);
    }

    @Override
    public PremiumMembership save(PremiumMembership membership) {

        PremiumMembershipEntity entity =
                em.find(PremiumMembershipEntity.class, membership.id());

        if (entity == null) {
            em.persist(new PremiumMembershipEntity(membership));
        } else {
            entity.update(membership);
        }

        em.flush();
        return membership;
    }
}
