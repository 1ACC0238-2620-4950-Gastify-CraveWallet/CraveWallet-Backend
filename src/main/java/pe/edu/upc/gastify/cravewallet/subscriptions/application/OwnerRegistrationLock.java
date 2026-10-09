package pe.edu.upc.gastify.cravewallet.subscriptions.application;

import java.util.UUID;

/** Serializa el alta por propietario durante la transacción. */
public interface OwnerRegistrationLock {
    void acquire(UUID owner);
}
