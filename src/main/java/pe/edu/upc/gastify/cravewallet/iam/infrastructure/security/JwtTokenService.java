package pe.edu.upc.gastify.cravewallet.iam.infrastructure.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.iam.domain.services.TokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService implements TokenService {
    private final JwtEncoder encoder;
    public JwtTokenService(JwtEncoder encoder) { this.encoder = encoder; }
    public AccessToken issue(UUID userId, UUID sessionId) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("cravewallet-backend")
                .subject(userId.toString()).id(sessionId.toString())
                .audience(List.of("cravewallet-mobile"))
                .issuedAt(now).expiresAt(expiresAt).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
