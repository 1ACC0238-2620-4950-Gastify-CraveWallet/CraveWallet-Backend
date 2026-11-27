package pe.edu.upc.gastify.cravewallet.delivery.domain.repositories;

import pe.edu.upc.gastify.cravewallet.delivery.domain.model.DeliveryExpense;
import java.time.YearMonth;
import java.util.*;

public interface DeliveryExpenseRepository {
    Optional<DeliveryExpense> findByRequest(UUID owner, UUID request);
    void save(DeliveryExpense expense);
    List<DeliveryExpense> findPeriod(UUID owner, YearMonth period);
    Optional<DeliveryExpense> findOwned(UUID owner, UUID id);
    void update(DeliveryExpense expense);
    void delete(DeliveryExpense expense);
}
