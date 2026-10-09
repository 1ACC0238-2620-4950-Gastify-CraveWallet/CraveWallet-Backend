package pe.edu.upc.gastify.cravewallet.subscriptions.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.repositories.SubscriptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SubscriptionApplicationService {
    private final SubscriptionRepository repository;
    private final OwnerRegistrationLock ownerLock;
    public SubscriptionApplicationService(SubscriptionRepository repository, OwnerRegistrationLock ownerLock) {
        this.repository = repository;
        this.ownerLock = ownerLock;
    }

    public Subscription register(UUID owner, String name, BigDecimal amount, Subscription.Currency currency,
                                 String category, Subscription.BillingCycle cycle, LocalDate date) {
        validateDate(date);
        ownerLock.acquire(owner);
        // Baseline Free: Premium no está implementado; nunca confiar en un flag enviado por el cliente.
        if (repository.countActive(owner) >= 5) throw new SubscriptionFailure(409, "El plan Free admite cinco suscripciones activas.");
        return repository.save(new Subscription(UUID.randomUUID(), owner, name, amount, currency,
                category, cycle, date, Subscription.Status.ACTIVE, null));
    }

    @Transactional(readOnly = true)
    public List<Subscription> list(UUID owner, Subscription.Status status, String search, String category) {
        return repository.findOwned(owner).stream()
                .filter(s -> s.status() == status)
                .filter(s -> search == null || s.name().toLowerCase(java.util.Locale.ROOT)
                        .contains(search.strip().toLowerCase(java.util.Locale.ROOT)))
                .filter(s -> category == null || s.category().equalsIgnoreCase(category.strip()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Subscription detail(UUID owner, UUID id) {
        return repository.findOwned(id, owner).orElseThrow(SubscriptionApplicationService::notFound);
    }

    public Subscription edit(UUID owner, UUID id, BigDecimal amount, String category, LocalDate date) {
        if (amount == null && category == null && date == null) throw new SubscriptionFailure(400, "Indica al menos un campo para editar.");
        if (date != null) validateDate(date);
        Subscription current = repository.findOwnedForUpdate(id, owner).orElseThrow(SubscriptionApplicationService::notFound);
        if (current.status() == Subscription.Status.CANCELLED) throw new SubscriptionFailure(409, "No se puede editar una suscripción cancelada.");
        return repository.save(current.edit(amount, category, date));
    }

    public Subscription cancel(UUID owner, UUID id) {
        Subscription current = repository.findOwnedForUpdate(id, owner).orElseThrow(SubscriptionApplicationService::notFound);
        return current.status() == Subscription.Status.CANCELLED ? current
                : repository.save(current.cancel(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
    }

    private static SubscriptionFailure notFound() { return new SubscriptionFailure(404, "Suscripción no encontrada."); }
    private static void validateDate(LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now(ZoneId.of("America/Lima"))))
            throw new SubscriptionFailure(400, "La próxima renovación debe ser hoy o una fecha futura (America/Lima).");
    }
}
