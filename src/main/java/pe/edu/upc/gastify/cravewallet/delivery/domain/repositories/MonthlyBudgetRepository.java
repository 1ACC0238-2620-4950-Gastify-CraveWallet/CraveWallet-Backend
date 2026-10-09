package pe.edu.upc.gastify.cravewallet.delivery.domain.repositories;

import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MonthlyBudget;
import java.time.YearMonth;
import java.util.*;

public interface MonthlyBudgetRepository {
    Optional<MonthlyBudget> find(UUID owner, YearMonth period);
    void save(MonthlyBudget budget);
}
