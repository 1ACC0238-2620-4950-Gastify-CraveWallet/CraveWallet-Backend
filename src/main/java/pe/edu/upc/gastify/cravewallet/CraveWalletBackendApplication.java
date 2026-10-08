package pe.edu.upc.gastify.cravewallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CraveWalletBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CraveWalletBackendApplication.class, args);
    }
}
