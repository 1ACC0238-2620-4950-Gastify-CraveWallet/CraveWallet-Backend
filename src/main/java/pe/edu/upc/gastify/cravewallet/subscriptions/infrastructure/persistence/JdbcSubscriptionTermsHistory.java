package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.SubscriptionTermsHistoryPort;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcSubscriptionTermsHistory implements SubscriptionTermsHistoryPort {
    private final JdbcTemplate jdbc;
    public JdbcSubscriptionTermsHistory(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID subscriptionId, Terms terms) {
        jdbc.update("insert into subscription_terms_history (subscription_id, effective_from, amount, next_billing_date, billing_cycle) "
                        + "values (?, ?, ?, ?, ?)", subscriptionId,
                Timestamp.from(terms.effectiveFrom().truncatedTo(ChronoUnit.MICROS)), terms.amount(),
                Date.valueOf(terms.nextBillingDate()), terms.billingCycle().name());
    }

    public List<Terms> find(UUID subscriptionId) {
        return jdbc.query("select effective_from, amount, next_billing_date, billing_cycle from subscription_terms_history "
                        + "where subscription_id = ? order by effective_from",
                (row, index) -> new Terms(row.getTimestamp("effective_from").toInstant(), row.getBigDecimal("amount"),
                        row.getDate("next_billing_date").toLocalDate(),
                        Subscription.BillingCycle.valueOf(row.getString("billing_cycle"))),
                subscriptionId);
    }
}
