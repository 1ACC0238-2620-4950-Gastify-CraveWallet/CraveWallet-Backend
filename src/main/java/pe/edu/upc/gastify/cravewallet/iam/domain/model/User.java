package pe.edu.upc.gastify.cravewallet.iam.domain.model;

import java.util.UUID;

public record User(UUID id, String email, String passwordHash, String referenceCurrency) {
    public User withReferenceCurrency(String currency) {
        if (!"PEN".equals(currency)) {
            throw new IllegalArgumentException("La moneda de referencia debe ser PEN.");
        }
        return new User(id, email, passwordHash, currency);
    }
}
