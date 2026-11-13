
package pe.edu.upc.gastify.cravewallet.premium.domain.repositories;

import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumMembership;

import java.util.Optional;
import java.util.UUID;

public interface PremiumMembershipRepository {

    Optional<PremiumMembership> findByOwnerId(UUID ownerId);

    PremiumMembership save(PremiumMembership membership);
}
