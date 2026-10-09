package pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence;

import org.springframework.stereotype.Repository;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.User;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.UserRepository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaUserRepository implements UserRepository {
    private final SpringDataUserRepository repository;
    public JpaUserRepository(SpringDataUserRepository repository) { this.repository = repository; }
    public Optional<User> findByEmail(String email) { return repository.findByEmail(email).map(UserEntity::toDomain); }
    public Optional<User> findById(UUID id) { return repository.findById(id).map(UserEntity::toDomain); }
    public User save(User user) { return repository.saveAndFlush(new UserEntity(user)).toDomain(); }
}
