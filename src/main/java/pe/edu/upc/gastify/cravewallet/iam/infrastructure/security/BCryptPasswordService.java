package pe.edu.upc.gastify.cravewallet.iam.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.iam.domain.services.PasswordService;

@Component
public class BCryptPasswordService implements PasswordService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public String hash(String password) { return encoder.encode(password); }
    public boolean matches(String password, String hash) { return encoder.matches(password, hash); }
}
