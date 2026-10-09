package pe.edu.upc.gastify.cravewallet.subscriptions.application;

import org.springframework.stereotype.Service;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import java.math.*;
import java.time.*;

@Service
public class ExchangeRateService {
    private final ExchangeRatePort provider;
    private final Clock clock;
    private ExchangeRatePort.ProviderRate cached;
    private Instant fetchedAt;
    private Instant retryAt = Instant.MIN;
    public ExchangeRateService(ExchangeRatePort provider, Clock clock) { this.provider = provider; this.clock = clock; }

    /** Una consulta simultánea al proveedor por instancia; caché 24h y retroceso 60s tras fallo. */
    public synchronized Rate quote(Currency from, Currency to) {
        Instant now = clock.instant();
        if (from == to) return new Rate(from, to, BigDecimal.ONE, now, now, false, "identity", null);
        boolean fresh = cached != null && now.isBefore(fetchedAt.plus(Duration.ofHours(24)))
                && now.isBefore(cached.updatedAt().plus(Duration.ofDays(7)));
        if (!fresh && !now.isBefore(retryAt)) {
            try {
                var response = provider.fetchUsdToPen();
                if (response.updatedAt().isAfter(now.plusSeconds(300))
                        || !now.isBefore(response.updatedAt().plus(Duration.ofDays(7))))
                    throw new IllegalArgumentException("Fecha de cotización fuera de rango.");
                cached = response; fetchedAt = now; fresh = true;
            } catch (RuntimeException failure) {
                retryAt = now.plusSeconds(60);
            }
        }
        if (cached == null || !now.isBefore(cached.updatedAt().plus(Duration.ofDays(7))))
            throw new SubscriptionFailure(503, "Cotización temporalmente no disponible; se conserva el importe original.");
        BigDecimal rate = from == Currency.USD ? cached.rate()
                : BigDecimal.ONE.divide(cached.rate(), 10, RoundingMode.HALF_UP);
        return new Rate(from, to, rate, cached.updatedAt(), fetchedAt, !fresh,
                "ExchangeRate-API", "https://www.exchangerate-api.com");
    }
    public record Rate(Currency from, Currency to, BigDecimal rate, Instant updatedAt, Instant fetchedAt,
                       boolean stale, String source, String attributionUrl) { }
}
