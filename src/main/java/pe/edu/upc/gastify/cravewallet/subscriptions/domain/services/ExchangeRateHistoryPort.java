package pe.edu.upc.gastify.cravewallet.subscriptions.domain.services;

import java.time.Instant;
import java.util.Optional;

/** Persists USD/PEN quotes obtained from the provider so past billing cycles can be explained. */
public interface ExchangeRateHistoryPort {
    void record(ExchangeRatePort.ProviderRate rate, Instant fetchedAt);
    Optional<ExchangeRatePort.ProviderRate> latestAtOrBefore(Instant instant);
    Optional<Instant> earliest();
}
