
package pe.edu.upc.gastify.cravewallet.premium.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumMembership;
import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumStatus;

@Entity
@Table(name = "premium_memberships")
public class PremiumMembershipEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PremiumStatus status;

    @Column(name = "period_ends_at")
    private Instant periodEndsAt;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    protected PremiumMembershipEntity() {
    }

    public PremiumMembershipEntity(PremiumMembership membership) {
        update(membership);
    }

    public void update(PremiumMembership membership) {
        this.id = membership.id();
        this.ownerId = membership.ownerId();
        this.status = membership.status();
        this.periodEndsAt = membership.periodEndsAt();
        this.cancelAtPeriodEnd = membership.cancelAtPeriodEnd();
    }

    public PremiumMembership toDomain() {
        return new PremiumMembership(
                id,
                ownerId,
                status,
                periodEndsAt,
                cancelAtPeriodEnd
        );
    }
}
