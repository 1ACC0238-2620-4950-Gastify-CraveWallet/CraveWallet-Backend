
package pe.edu.upc.gastify.cravewallet.premium.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PremiumApplicationService {

    private final PremiumMembershipRepository repository;

    public PremiumApplicationService(
            PremiumMembershipRepository repository
    ) {
        this.repository = repository;
    }

    public PremiumStatusView getStatus(UUID ownerId) {

        return repository.findByOwnerId(ownerId)
                .map(membership -> {

                    boolean active =
                            membership.hasAccessAt(Instant.now());

                    return new PremiumStatusView(
                            active ? "PREMIUM" : "FREE",
                            active,
                            membership.status().name(),
                            membership.periodEndsAt(),
                            membership.cancelAtPeriodEnd()
                    );
                })
                .orElseGet(() -> new PremiumStatusView(
                        "FREE",
                        false,
                        null,
                        null,
                        false
                ));
    }


    public boolean hasPremiumAccess(UUID ownerId) {

        return repository.findByOwnerId(ownerId)
                .map(membership ->
                        membership.hasAccessAt(Instant.now())
                )
                .orElse(false);
    }


    public record PremiumStatusView(
            String plan,
            boolean premium,
            String membershipStatus,
            Instant periodEndsAt,
            boolean cancelAtPeriodEnd
    ) {
    }
}
