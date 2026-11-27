package pe.edu.upc.gastify.cravewallet.launch.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.launch.domain.model.LaunchSubscriber;
import pe.edu.upc.gastify.cravewallet.launch.domain.repositories.LaunchSubscriberRepository;

import java.time.Clock;

@Service
public class LaunchSubscriptionApplicationService {
    private final LaunchSubscriberRepository repository;
    private final Clock clock = Clock.systemUTC();

    public LaunchSubscriptionApplicationService(LaunchSubscriberRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers the address once. Repeating the request is not an error and gives the same
     * answer, so the endpoint does not reveal which addresses are already registered.
     */
    @Transactional
    public void subscribe(String email, String locale) {
        String normalized = LaunchSubscriber.normalize(email);
        if (repository.existsByEmail(normalized)) return;
        repository.save(LaunchSubscriber.create(normalized, locale, clock.instant()));
    }
}
