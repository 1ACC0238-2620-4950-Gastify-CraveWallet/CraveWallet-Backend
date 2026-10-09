package pe.edu.upc.gastify.cravewallet.iam.domain.repositories;

import pe.edu.upc.gastify.cravewallet.iam.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByEmail(String email);
    Optional<User> findById(UUID id);
    User save(User user);
}
