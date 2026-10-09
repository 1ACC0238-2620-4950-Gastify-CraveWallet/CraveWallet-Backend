package pe.edu.upc.gastify.cravewallet.subscriptions.domain.repositories;

import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository {
    List<Subscription> findOwned(UUID owner);
    Optional<Subscription> findOwned(UUID id, UUID owner);
    Optional<Subscription> findOwnedForUpdate(UUID id, UUID owner);
    long countActive(UUID owner);
    Subscription save(Subscription subscription);
}
