package pe.edu.upc.gastify.cravewallet.premium.domain.model;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PremiumMembership(
        UUID id,
        UUID ownerId,
        PremiumStatus status,
        Instant periodEndsAt,
        boolean cancelAtPeriodEnd
) {

    public PremiumMembership {
        Objects.requireNonNull(id, "El ID es obligatorio");
        Objects.requireNonNull(ownerId, "El propietario es obligatorio");
        Objects.requireNonNull(status, "El estado es obligatorio");
    }

    public boolean hasAccessAt(Instant now) {
        return status == PremiumStatus.ACTIVE
                && periodEndsAt != null
                && periodEndsAt.isAfter(now);
    }
}
