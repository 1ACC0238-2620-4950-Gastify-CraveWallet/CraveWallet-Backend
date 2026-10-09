package pe.edu.upc.gastify.cravewallet.iam.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.SessionRepository;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class JwtConfiguration {
    @Bean
    SecretKey jwtSecretKey(@Value("${JWT_SECRET:}") String configured, Environment environment) {
        byte[] key;
        if (configured.isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("local", "test"))
                    || environment.acceptsProfiles(Profiles.of("prod", "postgres"))) {
                throw new IllegalStateException("Definir JWT_SECRET con al menos 32 bytes UTF-8.");
            }
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            key = configured.getBytes(StandardCharsets.UTF_8);
            if (key.length < 32) throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes UTF-8.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, SessionRepository sessions) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> sessionValidator = jwt -> {
            boolean valid;
            try {
                valid = jwt.getExpiresAt() != null && jwt.getAudience().contains("cravewallet-mobile")
                        && sessions.findById(UUID.fromString(jwt.getId()))
                        .filter(session -> session.userId().toString().equals(jwt.getSubject()))
                        .filter(session -> session.activeAt(Instant.now())).isPresent();
            } catch (IllegalArgumentException | NullPointerException exception) { valid = false; }
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Sesión no válida.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("cravewallet-backend"), sessionValidator));
        return decoder;
    }
}
