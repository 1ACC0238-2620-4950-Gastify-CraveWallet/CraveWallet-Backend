
package pe.edu.upc.gastify.cravewallet.premium.domain.services;

import java.util.UUID;

public interface PremiumPaymentGateway {

    CheckoutSession createMonthlyCheckout(UUID ownerId);

    record CheckoutSession(
            String sessionId,
            String checkoutUrl
    ) {
    }
}
