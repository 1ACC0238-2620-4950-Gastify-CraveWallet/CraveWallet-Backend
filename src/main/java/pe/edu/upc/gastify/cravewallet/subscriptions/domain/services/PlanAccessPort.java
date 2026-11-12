
package pe.edu.upc.gastify.cravewallet.subscriptions.domain.services;

import java.util.UUID;

public interface PlanAccessPort {

    PlanAccess getPlanAccess(UUID ownerId);

    record PlanAccess(
            boolean premium,
            int freePlanLimit
    ) {

        public boolean canRegister(long activeCount) {
            return premium || activeCount < freePlanLimit;
        }
    }
}
