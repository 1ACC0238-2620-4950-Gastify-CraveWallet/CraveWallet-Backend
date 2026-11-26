package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRateHistoryPort;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort.ProviderRate;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Repository
public class JdbcExchangeRateHistory implements ExchangeRateHistoryPort {
    private final JdbcTemplate jdbc;
    public JdbcExchangeRateHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(ProviderRate rate, Instant fetchedAt) {
        Timestamp updatedAt = Timestamp.from(rate.updatedAt().truncatedTo(ChronoUnit.MICROS));
        Integer existing = jdbc.queryForObject("select count(*) from exchange_rate_snapshots where base_currency = 'USD' "
                + "and quote_currency = 'PEN' and provider_updated_at = ?", Integer.class, updatedAt);
        if (existing != null && existing > 0) return;
        try {
            jdbc.update("insert into exchange_rate_snapshots (base_currency, quote_currency, provider_updated_at, rate, fetched_at) "
                    + "values ('USD', 'PEN', ?, ?, ?)", updatedAt, rate.rate(), Timestamp.from(fetchedAt));
        } catch (DuplicateKeyException concurrentInsert) {
            // Another instance stored the same quote first.
        }
    }

    public Optional<ProviderRate> latestAtOrBefore(Instant instant) {
        return jdbc.query("select rate, provider_updated_at from exchange_rate_snapshots where base_currency = 'USD' "
                        + "and quote_currency = 'PEN' and provider_updated_at <= ? order by provider_updated_at desc limit 1",
                (row, index) -> new ProviderRate(row.getBigDecimal("rate"), row.getTimestamp("provider_updated_at").toInstant()),
                Timestamp.from(instant)).stream().findFirst();
    }

    public Optional<Instant> earliest() {
        Timestamp first = jdbc.queryForObject("select min(provider_updated_at) from exchange_rate_snapshots "
                + "where base_currency = 'USD' and quote_currency = 'PEN'", Timestamp.class);
        return Optional.ofNullable(first).map(Timestamp::toInstant);
    }
}
