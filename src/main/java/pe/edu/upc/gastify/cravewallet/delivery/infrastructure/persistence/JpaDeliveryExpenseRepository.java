package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.DeliveryExpense;
import pe.edu.upc.gastify.cravewallet.delivery.domain.repositories.DeliveryExpenseRepository;
import java.time.YearMonth;
import java.util.*;

@Repository
public class JpaDeliveryExpenseRepository implements DeliveryExpenseRepository {
    private final EntityManager em;
    public JpaDeliveryExpenseRepository(EntityManager em) { this.em = em; }
    public Optional<DeliveryExpense> findByRequest(UUID owner, UUID request) {
        return em.createQuery("select e from DeliveryExpenseEntity e where e.ownerId = :owner and e.requestId = :request", DeliveryExpenseEntity.class)
                .setParameter("owner", owner).setParameter("request", request).getResultStream().findFirst().map(DeliveryExpenseEntity::toDomain);
    }
    public void save(DeliveryExpense expense) { em.persist(new DeliveryExpenseEntity(expense)); em.flush(); }
    public List<DeliveryExpense> findPeriod(UUID owner, YearMonth period) {
        return em.createQuery("select e from DeliveryExpenseEntity e where e.ownerId = :owner and e.expenseDate >= :start and e.expenseDate < :end order by e.expenseDate, e.id", DeliveryExpenseEntity.class)
                .setParameter("owner", owner).setParameter("start", period.atDay(1))
                .setParameter("end", period.plusMonths(1).atDay(1)).getResultList().stream().map(DeliveryExpenseEntity::toDomain).toList();
    }
}
