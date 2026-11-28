package pe.edu.upc.gastify.cravewallet.launch.domain.repositories;

import pe.edu.upc.gastify.cravewallet.launch.domain.model.LaunchSubscriber;

public interface LaunchSubscriberRepository {
    boolean existsByEmail(String normalizedEmail);
    void save(LaunchSubscriber subscriber);
}
