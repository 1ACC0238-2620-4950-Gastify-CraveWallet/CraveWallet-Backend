package pe.edu.upc.gastify.cravewallet.premium.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPremiumBillingRepository implements PremiumBillingRepository {
    private static final RowMapper<Checkout> CHECKOUT = (row, index) -> new Checkout(
            row.getObject("id", UUID.class), row.getObject("owner_id", UUID.class),
            row.getObject("current_owner_id") != null, row.getString("provider_id"),
            row.getString("checkout_url"), row.getString("status"),
            row.getLong("provider_version"), instant(row, "canceled_at"));
    private static final RowMapper<PaymentEntry> PAYMENT = (row, index) -> new PaymentEntry(
            row.getString("provider_payment_id"), row.getString("provider_invoice_id"),
            row.getBigDecimal("amount"), row.getString("currency"), row.getString("status"),
            instant(row, "paid_at"), instant(row, "recorded_at"));

    private final JdbcTemplate jdbc;

    public JdbcPremiumBillingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockOwner(UUID owner) {
        if (jdbc.queryForList("SELECT id FROM iam_users WHERE id = ? FOR UPDATE", UUID.class, owner).isEmpty()) {
            throw new PremiumFailure(401, "Usuario no disponible.");
        }
    }

    @Override
    public Optional<Checkout> currentCheckout(UUID owner) {
        return jdbc.query("SELECT * FROM premium_checkouts WHERE current_owner_id = ?", CHECKOUT, owner).stream().findFirst();
    }

    @Override
    public Optional<Checkout> findCheckout(UUID reference) {
        return jdbc.query("SELECT * FROM premium_checkouts WHERE id = ?", CHECKOUT, reference).stream().findFirst();
    }

    @Override
    public void releaseCheckout(UUID reference) {
        jdbc.update("UPDATE premium_checkouts SET current_owner_id = NULL WHERE id = ?", reference);
    }

    @Override
    public void reserveCheckout(UUID reference, UUID owner) {
        jdbc.update("INSERT INTO premium_checkouts(id, owner_id, current_owner_id, status) VALUES (?, ?, ?, 'creating')",
                reference, owner, owner);
    }

    @Override
    public void updateCheckout(Checkout checkout) {
        jdbc.update("""
                UPDATE premium_checkouts
                SET provider_id = ?, checkout_url = ?, status = ?, provider_version = ?, canceled_at = ?
                WHERE id = ?
                """, checkout.agreementId(), checkout.checkoutUrl(), checkout.status(), checkout.version(),
                offset(checkout.canceledAt()), checkout.reference());
    }

    @Override
    public boolean processed(String deliveryId, String topic, String resourceId) {
        return !jdbc.queryForList("""
                SELECT request_id FROM premium_webhook_deliveries WHERE request_id = ? AND topic = ? AND resource_id = ?
                """, String.class, deliveryId, topic, resourceId).isEmpty();
    }

    @Override
    public void markProcessed(String deliveryId, String topic, String resourceId, Instant processedAt) {
        jdbc.update("INSERT INTO premium_webhook_deliveries VALUES (?, ?, ?, ?)", deliveryId, topic, resourceId, offset(processedAt));
    }

    @Override
    public Optional<PaymentEntry> findPayment(String externalId) {
        return jdbc.query("SELECT * FROM premium_payments WHERE provider_payment_id = ?", PAYMENT, externalId).stream().findFirst();
    }

    @Override
    public boolean invoicePaid(String invoiceId) {
        return !jdbc.queryForList("SELECT approved_invoice_id FROM premium_payments WHERE approved_invoice_id = ?",
                String.class, invoiceId).isEmpty();
    }

    @Override
    public void insertPayment(PaymentEntry payment, UUID reference) {
        jdbc.update("""
                INSERT INTO premium_payments(provider_payment_id, provider_invoice_id, checkout_id,
                    amount, currency, status, paid_at, recorded_at, approved_invoice_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, payment.externalId(), payment.invoiceId(), reference, payment.amount(), payment.currency(),
                payment.status(), offset(payment.paidAt()), offset(payment.recordedAt()),
                payment.paidAt() == null ? null : payment.invoiceId());
    }

    @Override
    public void approvePayment(PaymentEntry payment) {
        jdbc.update("""
                UPDATE premium_payments SET status = 'approved', paid_at = ?, approved_invoice_id = ?
                WHERE provider_payment_id = ?
                """, offset(payment.paidAt()), payment.invoiceId(), payment.externalId());
    }

    @Override
    public List<PaymentEntry> payments(UUID owner) {
        return jdbc.query("""
                SELECT p.* FROM premium_payments p JOIN premium_checkouts c ON c.id = p.checkout_id
                WHERE c.owner_id = ? ORDER BY COALESCE(p.paid_at, p.recorded_at) DESC, p.provider_payment_id
                """, PAYMENT, owner);
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        var value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime offset(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
