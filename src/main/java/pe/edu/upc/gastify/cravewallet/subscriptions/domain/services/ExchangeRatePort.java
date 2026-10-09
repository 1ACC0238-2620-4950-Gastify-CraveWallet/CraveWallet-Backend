package pe.edu.upc.gastify.cravewallet.subscriptions.domain.services;

import java.math.BigDecimal;
import java.time.Instant;

public interface ExchangeRatePort {
    ProviderRate fetchUsdToPen();
    record ProviderRate(BigDecimal rate, Instant updatedAt) {
        public ProviderRate {
            if (rate == null || rate.signum() <= 0 || updatedAt == null)
                throw new IllegalArgumentException("Cotización no válida.");
        }
    }
}
