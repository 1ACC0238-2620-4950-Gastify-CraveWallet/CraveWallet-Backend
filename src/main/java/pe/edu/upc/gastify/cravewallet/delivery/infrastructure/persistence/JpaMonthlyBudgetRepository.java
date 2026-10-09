package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MonthlyBudget;
import pe.edu.upc.gastify.cravewallet.delivery.domain.repositories.MonthlyBudgetRepository;
import java.time.YearMonth;
import java.util.*;

@Repository
public class JpaMonthlyBudgetRepository implements MonthlyBudgetRepository {
    private final EntityManager em;
    public JpaMonthlyBudgetRepository(EntityManager em) { this.em = em; }
    public Optional<MonthlyBudget> find(UUID owner, YearMonth period) {
        return em.createQuery("select b from MonthlyBudgetEntity b where b.ownerId = :owner and b.year = :year and b.month = :month", MonthlyBudgetEntity.class)
                .setParameter("owner", owner).setParameter("year", period.getYear()).setParameter("month", period.getMonthValue())
                .getResultStream().findFirst().map(MonthlyBudgetEntity::toDomain);
    }
    public void save(MonthlyBudget budget) {
        MonthlyBudgetEntity entity = em.find(MonthlyBudgetEntity.class, budget.id());
        if (entity == null) em.persist(new MonthlyBudgetEntity(budget)); else entity.update(budget);
        em.flush();
    }
}
