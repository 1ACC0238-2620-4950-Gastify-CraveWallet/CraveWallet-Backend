package pe.edu.upc.gastify.cravewallet.premium.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.UserRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumMembership;
import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumStatus;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository.Checkout;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumBillingRepository.PaymentEntry;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Agreement;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Invoice;
import pe.edu.upc.gastify.cravewallet.premium.domain.services.PremiumPaymentGateway.Payment;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PremiumBillingService {
    private static final Logger LOG = LoggerFactory.getLogger(PremiumBillingService.class);
    private static final ZoneId BILLING_ZONE = ZoneId.of("America/Lima");
    private static final Set<String> AGREEMENT_STATUSES = Set.of("pending", "authorized", "paused", "canceled", "cancelled");

    private final PremiumBillingRepository billing;
    private final PremiumMembershipRepository memberships;
    private final UserRepository users;
    private final PremiumPaymentGateway gateway;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final boolean expectedLiveMode;

    public PremiumBillingService(PremiumBillingRepository billing, PremiumMembershipRepository memberships,
                                 UserRepository users, PremiumPaymentGateway gateway, Clock clock,
                                 PlatformTransactionManager transactionManager,
                                 @Value("${MERCADOPAGO_EXPECTED_LIVE_MODE:false}") boolean expectedLiveMode) {
        this.billing = billing;
        this.memberships = memberships;
        this.users = users;
        this.gateway = gateway;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.expectedLiveMode = expectedLiveMode;
    }

    public CheckoutView checkout(UUID owner) {
        var start = transaction.execute(status -> prepareCheckout(owner));
        if (start.existing() != null) return start.existing();

        Agreement agreement = start.recovering()
                ? gateway.findCheckout(start.reference(), start.email())
                    .orElseThrow(() -> conflict("Ya existe un checkout pendiente de confirmación."))
                : createAgreement(owner, start);
        if (!start.reference().equals(agreement.reference())) throw checkoutResponseMismatch("external_reference");
        if (!agreement.matchesMonthlyTerms()) throw checkoutResponseMismatch("recurring_terms");
        if ("pending".equals(agreement.status()) && !validCheckoutUrl(agreement.checkoutUrl())) {
            throw checkoutResponseMismatch("init_point");
        }
        if (!knownAgreementStatus(agreement)) throw checkoutResponseMismatch("agreement_status");
        if (agreement.version() < 0 && !"pending".equals(agreement.status())) {
            throw checkoutResponseMismatch("agreement_version");
        }

        Checkout updated = transaction.execute(status -> {
            billing.lockOwner(owner);
            Checkout attempt = billing.findCheckout(start.reference()).orElseThrow();
            if (!attempt.current()) throw conflict("El checkout ya cambió de estado.");
            // Un checkout pendiente puede conservarse sin versión y sin conceder acceso.
            return persistAgreement(attempt, agreement);
        });
        if (!"pending".equals(updated.status())) throw conflict("La suscripción ya cambió de estado.");
        return checkoutView(updated);
    }

    private Agreement createAgreement(UUID owner, CheckoutStart start) {
        try {
            return gateway.createMonthlyCheckout(start.reference(), start.email());
        } catch (PremiumFailure.CheckoutRejected rejected) {
            transaction.executeWithoutResult(status -> {
                billing.lockOwner(owner);
                var attempt = billing.findCheckout(start.reference()).orElseThrow();
                if (attempt.agreementId() == null) billing.releaseCheckout(attempt.reference());
            });
            throw rejected;
        }
    }

    private CheckoutStart prepareCheckout(UUID owner) {
        billing.lockOwner(owner);
        var membership = memberships.findByOwnerId(owner);
        if (membership.filter(value -> value.hasAccessAt(clock.instant())).isPresent()) {
            throw conflict("Ya existe un período Premium vigente.");
        }
        var current = billing.currentCheckout(owner);
        if (current.isPresent() && current.get().canceledAt() == null) {
            var checkout = current.get();
            if ("pending".equals(checkout.status()) && validCheckoutUrl(checkout.checkoutUrl())) {
                Agreement remote = gateway.findAgreement(checkout.agreementId()).orElseThrow(PremiumBillingService::unavailable);
                requireSameAgreement(checkout, remote);
                if (remote.canceled()) {
                    sync(checkout, remote);
                    markMembershipCanceled(owner);
                } else {
                    if (!knownAgreementStatus(remote) || !remote.matchesMonthlyTerms()) throw unavailable();
                    if (!"pending".equals(remote.status())) {
                        throw conflict("La suscripción ya cambió de estado.");
                    }
                    return new CheckoutStart(null, null, checkoutView(persistAgreement(checkout, remote)), false);
                }
            } else if ("creating".equals(checkout.status())) {
                gateway.checkCheckoutConfiguration();
                return new CheckoutStart(checkout.reference(), payerEmail(owner), null, true);
            } else {
                throw conflict("Ya existe un checkout o una suscripción en curso.");
            }
        }

        gateway.checkCheckoutConfiguration();
        String email = payerEmail(owner);
        current.ifPresent(value -> billing.releaseCheckout(value.reference()));
        UUID reference = UUID.randomUUID();
        billing.reserveCheckout(reference, owner);
        return new CheckoutStart(reference, email, null, false);
    }

    private String payerEmail(UUID owner) {
        return users.findById(owner).orElseThrow(() -> new PremiumFailure(401, "Usuario no disponible.")).email();
    }

    @Transactional
    public void cancel(UUID owner) {
        billing.lockOwner(owner);
        Checkout checkout = billing.currentCheckout(owner)
                .orElseThrow(() -> new PremiumFailure(404, "No hay una suscripción para cancelar."));
        if (checkout.agreementId() == null) throw conflict("El checkout todavía no ha sido confirmado.");

        Agreement remote = gateway.findAgreement(checkout.agreementId()).orElseThrow(PremiumBillingService::unavailable);
        requireSameAgreement(checkout, remote);
        if (!remote.canceled()) {
            gateway.cancelAgreement(remote.id());
            remote = gateway.findAgreement(remote.id()).orElseThrow(PremiumBillingService::unavailable);
            requireSameAgreement(checkout, remote);
        }
        if (!remote.canceled()) throw unavailable();
        sync(checkout, remote);
        markMembershipCanceled(owner);
    }

    @Transactional(readOnly = true)
    public List<PaymentEntry> payments(UUID owner) {
        return billing.payments(owner);
    }

    public void receiveNotification(String deliveryId, String topic, String resourceId) {
        if (completedDelivery(deliveryId, topic, resourceId)) return;

        Invoice invoice = null;
        Payment payment = null;
        String agreementId = resourceId;
        switch (topic) {
            case "subscription_preapproval" -> { }
            case "subscription_authorized_payment", "payment" -> {
                boolean paymentTopic = "payment".equals(topic);
                invoice = (paymentTopic ? gateway.findInvoiceByPayment(resourceId) : gateway.findInvoice(resourceId))
                        .orElse(null);
                if (invoice == null || invoice.paymentId() == null) return;
                if (!(paymentTopic ? resourceId.equals(invoice.paymentId()) : resourceId.equals(invoice.id()))) return;
                agreementId = invoice.agreementId();
                payment = gateway.findPayment(invoice.paymentId()).orElseThrow(PremiumBillingService::unavailable);
                if (!invoice.paymentId().equals(payment.id())) return;
            }
            default -> { return; }
        }

        Agreement agreement = gateway.findAgreement(agreementId).orElse(null);
        if (agreement == null || !agreementId.equals(agreement.id()) || agreement.reference() == null) return;
        var checkout = billing.findCheckout(agreement.reference()).orElse(null);
        if (checkout == null) return;

        Invoice verifiedInvoice = invoice;
        Payment verifiedPayment = payment;
        transaction.executeWithoutResult(status -> {
            billing.lockOwner(checkout.owner());
            if (completedDelivery(deliveryId, topic, resourceId)) return;
            Checkout current = billing.findCheckout(checkout.reference()).orElseThrow();
            if (current.agreementId() == null && (!current.current() || !agreement.matchesMonthlyTerms())) return;
            if (current.agreementId() != null && !current.agreementId().equals(agreement.id())) return;
            Checkout updated = sync(current, agreement);
            if (updated.current() && updated.canceledAt() != null) markMembershipCanceled(updated.owner());
            if (verifiedPayment != null && agreement.matchesMonthlyTerms()) applyPayment(updated, verifiedInvoice, verifiedPayment);
            if (!billing.processed(deliveryId, topic, resourceId)) {
                billing.markProcessed(deliveryId, topic, resourceId, clock.instant());
            }
        });
    }

    private boolean completedDelivery(String deliveryId, String topic, String resourceId) {
        if (!billing.processed(deliveryId, topic, resourceId)) return false;
        // Older deliveries could be acknowledged without recording the payment.
        // Revalidate these on replay; payment/invoice uniqueness still prevents double credit.
        return switch (topic) {
            case "subscription_authorized_payment" -> billing.invoicePaid(resourceId);
            case "payment" -> billing.findPayment(resourceId)
                    .filter(payment -> "approved".equals(payment.status())).isPresent();
            default -> true;
        };
    }

    private Checkout sync(Checkout checkout, Agreement agreement) {
        if (!knownAgreementStatus(agreement) || agreement.version() < 0) {
            throw unavailable();
        }
        return persistAgreement(checkout, agreement);
    }

    private Checkout persistAgreement(Checkout checkout, Agreement agreement) {
        requireSameAgreement(checkout, agreement);
        if (checkout.canceledAt() != null || (!agreement.canceled() && agreement.version() < checkout.version())) {
            return checkout;
        }

        Instant canceledAt = agreement.canceled()
                ? (agreement.modifiedAt() == null ? clock.instant() : agreement.modifiedAt()) : null;
        String url = validCheckoutUrl(agreement.checkoutUrl()) ? agreement.checkoutUrl() : checkout.checkoutUrl();
        var updated = new Checkout(checkout.reference(), checkout.owner(), checkout.current(), agreement.id(),
                url, agreement.canceled() ? "canceled" : agreement.status(),
                Math.max(checkout.version(), agreement.version()), canceledAt);
        billing.updateCheckout(updated);
        return updated;
    }

    private void applyPayment(Checkout checkout, Invoice invoice, Payment payment) {
        if (!termsMatch(invoice.amount(), invoice.currency()) || !termsMatch(payment.amount(), payment.currency())) return;
        if (!Boolean.valueOf(expectedLiveMode).equals(payment.liveMode())) {
            throw new PremiumFailure(503,
                    "El live_mode del pago no coincide con MERCADOPAGO_EXPECTED_LIVE_MODE del backend.");
        }
        boolean approved = "approved".equals(payment.status());
        if (!approved && !"rejected".equals(payment.status())) return;

        Instant now = clock.instant();
        if (approved && (payment.paidAt() == null || invoice.debitAt() == null
                || payment.paidAt().isAfter(now.plusSeconds(300))
                || (payment.refundedAmount() != null && payment.refundedAmount().signum() > 0))) return;
        if (approved && "pending".equals(checkout.status())) throw unavailable();

        var existing = billing.findPayment(payment.id());
        if (existing.isPresent() && (!approved || !invoice.id().equals(existing.get().invoiceId())
                || "approved".equals(existing.get().status()))) return;
        if (approved && billing.invoicePaid(invoice.id())) return;

        var entry = new PaymentEntry(payment.id(), invoice.id(), payment.amount(), payment.currency(),
                payment.status(), approved ? payment.paidAt() : null, now);
        if (existing.isPresent()) billing.approvePayment(entry);
        else billing.insertPayment(entry, checkout.reference());

        if (!approved || !checkout.current() || (checkout.canceledAt() != null
                && !invoice.debitAt().isBefore(checkout.canceledAt()))) return;

        Instant endsAt = invoice.debitAt().atZone(BILLING_ZONE).plusMonths(1).toInstant();
        var membership = memberships.findByOwnerId(checkout.owner());
        if (membership.isPresent() && membership.get().periodEndsAt() != null
                && !endsAt.isAfter(membership.get().periodEndsAt())) return;
        UUID membershipId = membership.map(PremiumMembership::id).orElseGet(UUID::randomUUID);
        memberships.save(new PremiumMembership(membershipId, checkout.owner(), PremiumStatus.ACTIVE,
                endsAt, checkout.canceledAt() != null));
    }

    private void markMembershipCanceled(UUID owner) {
        memberships.findByOwnerId(owner).filter(value -> !value.cancelAtPeriodEnd()).ifPresent(value ->
                memberships.save(new PremiumMembership(value.id(), owner, value.status(), value.periodEndsAt(), true)));
    }

    private static void requireSameAgreement(Checkout checkout, Agreement agreement) {
        if (!checkout.reference().equals(agreement.reference())
                || (checkout.agreementId() != null && !checkout.agreementId().equals(agreement.id()))) {
            throw unavailable();
        }
    }

    private static boolean termsMatch(BigDecimal amount, String currency) {
        return amount != null && PremiumPaymentGateway.MONTHLY_PRICE.compareTo(amount) == 0
                && PremiumPaymentGateway.CURRENCY.equals(currency);
    }

    private static boolean validCheckoutUrl(String url) {
        if (url == null || url.length() > 2048) return false;
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getPort() == -1
                    && Set.of("www.mercadopago.com.pe", "www.mercadopago.com.ar", "www.mercadopago.com").contains(uri.getHost());
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static CheckoutView checkoutView(Checkout checkout) {
        return new CheckoutView(checkout.agreementId(), checkout.checkoutUrl(), "PENDING",
                PremiumPaymentGateway.MONTHLY_PRICE, PremiumPaymentGateway.CURRENCY);
    }

    private static PremiumFailure unavailable() {
        return new PremiumFailure(503, "Mercado Pago no confirmó la operación.");
    }

    private static boolean knownAgreementStatus(Agreement agreement) {
        return agreement.status() != null && AGREEMENT_STATUSES.contains(agreement.status());
    }

    private static PremiumFailure checkoutResponseMismatch(String field) {
        LOG.warn("Respuesta de Mercado Pago no verificable para checkout field={}", field);
        return unavailable();
    }

    private static PremiumFailure conflict(String message) {
        return new PremiumFailure(409, message);
    }

    private record CheckoutStart(UUID reference, String email, CheckoutView existing, boolean recovering) {}
    public record CheckoutView(String sessionId, String checkoutUrl, String status, BigDecimal amount, String currency) {}
}
