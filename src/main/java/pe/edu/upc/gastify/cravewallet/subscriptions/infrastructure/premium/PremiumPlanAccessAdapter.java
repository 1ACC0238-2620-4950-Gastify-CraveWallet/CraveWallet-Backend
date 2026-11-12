
package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.premium;

import org.springframework.stereotype.Component;

import pe.edu.upc.gastify.cravewallet.premium.application.PremiumApplicationService;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.PlanAccessPort;

import java.util.UUID;

@Component
public class PremiumPlanAccessAdapter
        implements PlanAccessPort {

    private static final int FREE_PLAN_LIMIT = 5;

    private final PremiumApplicationService premiumService;

    public PremiumPlanAccessAdapter(
            PremiumApplicationService premiumService
    ) {
        this.premiumService = premiumService;
    }

    @Override
    public PlanAccess getPlanAccess(UUID ownerId) {

        boolean premium =
                premiumService.hasPremiumAccess(ownerId);

        return new PlanAccess(
                premium,
                FREE_PLAN_LIMIT
        );
    }
}
