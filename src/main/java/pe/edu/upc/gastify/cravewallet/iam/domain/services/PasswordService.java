package pe.edu.upc.gastify.cravewallet.iam.domain.services;

public interface PasswordService {
    String hash(String password);
    boolean matches(String password, String hash);
}
