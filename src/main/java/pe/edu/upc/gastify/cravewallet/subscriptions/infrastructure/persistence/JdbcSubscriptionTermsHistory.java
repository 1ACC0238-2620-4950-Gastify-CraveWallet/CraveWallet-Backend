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

    /**
     * Two changes can share the same clock reading (the system clock may tick only every few
     * milliseconds), for example a create followed at once by an edit. The later terms replace the
     * earlier ones at that instant instead of failing on the primary key.
     */
    public void record(UUID subscriptionId, Terms terms) {
        Timestamp effectiveFrom = Timestamp.from(terms.effectiveFrom().truncatedTo(ChronoUnit.MICROS));
        int replaced = jdbc.update("update subscription_terms_history set amount = ?, next_billing_date = ?, billing_cycle = ? "
                        + "where subscription_id = ? and effective_from = ?",
                terms.amount(), Date.valueOf(terms.nextBillingDate()), terms.billingCycle().name(), subscriptionId, effectiveFrom);
        if (replaced > 0) return;
        jdbc.update("insert into subscription_terms_history (subscription_id, effective_from, amount, next_billing_date, billing_cycle) "
                        + "values (?, ?, ?, ?, ?)", subscriptionId, effectiveFrom, terms.amount(),
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
