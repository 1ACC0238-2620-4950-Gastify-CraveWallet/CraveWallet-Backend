package pe.edu.upc.gastify.cravewallet.iam.infrastructure.persistence;

import jakarta.persistence.*;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.User;
import java.util.UUID;

@Entity
@Table(name = "iam_users")
public class UserEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(name = "reference_currency", nullable = false, length = 3) private String referenceCurrency;
    protected UserEntity() { }
    public UserEntity(User user) {
        id = user.id(); email = user.email(); passwordHash = user.passwordHash(); referenceCurrency = user.referenceCurrency();
    }
    public User toDomain() { return new User(id, email, passwordHash, referenceCurrency); }
}
