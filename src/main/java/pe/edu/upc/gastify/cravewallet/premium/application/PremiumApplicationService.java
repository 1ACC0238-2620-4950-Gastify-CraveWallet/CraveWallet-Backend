
package pe.edu.upc.gastify.cravewallet.premium.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PremiumApplicationService {

    private final PremiumMembershipRepository repository;
    private final PremiumBillingRepository billing;
    private final Clock clock;

    public PremiumApplicationService(
            PremiumMembershipRepository repository, PremiumBillingRepository billing, Clock clock
    ) {
        this.repository = repository;
        this.billing = billing;
        this.clock = clock;
    }

    public PremiumStatusView getStatus(UUID ownerId) {

        return repository.findByOwnerId(ownerId)
                .map(membership -> {

                    boolean active =
                            membership.hasAccessAt(clock.instant());

                    return new PremiumStatusView(
                            active ? "PREMIUM" : "FREE",
                            active,
                            membership.status().name(),
                            membership.periodEndsAt(),
                            membership.cancelAtPeriodEnd(),
                            PremiumPaymentGateway.MONTHLY_PRICE,
                            PremiumPaymentGateway.CURRENCY
                    );
                })
                .orElseGet(() -> {
                    var checkout = billing.currentCheckout(ownerId).orElse(null);
                    boolean canceled = checkout != null && checkout.canceledAt() != null;
                    String status = checkout == null ? null : canceled ? "CANCELED" : "PENDING";
                    return new PremiumStatusView("FREE", false, status, null, canceled,
                            PremiumPaymentGateway.MONTHLY_PRICE, PremiumPaymentGateway.CURRENCY);
                });
    }


    public boolean hasPremiumAccess(UUID ownerId) {

        return repository.findByOwnerId(ownerId)
                .map(membership ->
                        membership.hasAccessAt(clock.instant())
                )
                .orElse(false);
    }


    public record PremiumStatusView(
            String plan,
            boolean premium,
            String membershipStatus,
            Instant periodEndsAt,
            boolean cancelAtPeriodEnd,
            BigDecimal monthlyPrice,
            String currency
    ) {
    }
}
