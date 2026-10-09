package pe.edu.upc.gastify.cravewallet.iam.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.Session;
import pe.edu.upc.gastify.cravewallet.iam.domain.model.User;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.SessionRepository;
import pe.edu.upc.gastify.cravewallet.iam.domain.repositories.UserRepository;
import pe.edu.upc.gastify.cravewallet.iam.domain.services.PasswordService;
import pe.edu.upc.gastify.cravewallet.iam.domain.services.TokenService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class AuthApplicationService {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthApplicationService(UserRepository users, SessionRepository sessions,
                                  PasswordService passwords, TokenService tokens) {
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyHash = passwords.hash(UUID.randomUUID().toString());
    }

    public Authentication register(String email, String password) {
        String normalized = normalize(email);
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new AuthFailure(400, "La contraseña debe tener al menos 8 caracteres, letras y números, y como máximo 72 bytes UTF-8.");
        }
        if (users.findByEmail(normalized).isPresent()) {
            throw new AuthFailure(409, "El correo ya está en uso.");
        }
        User user = users.save(new User(UUID.randomUUID(), normalized, passwords.hash(password), "PEN"));
        return newSession(user);
    }

    public Authentication login(String email, String password) {
        User user = users.findByEmail(normalize(email)).orElse(null);
        String hash = user == null ? dummyHash : user.passwordHash();
        boolean valid = password.getBytes(StandardCharsets.UTF_8).length <= 72 && passwords.matches(password, hash);
        if (user == null || !valid) {
            throw new AuthFailure(401, "Credenciales no válidas.");
        }
        return newSession(user);
    }

    public Authentication refresh(String refreshToken) {
        Session old = sessions.findByRefreshHashForUpdate(digest(refreshToken))
                .filter(session -> session.activeAt(Instant.now()))
                .orElseThrow(() -> new AuthFailure(401, "Token de renovación no válido."));
        User user = requireUser(old.userId());
        sessions.save(old.revoke());
        return newSession(user);
    }

    public void logout(UUID userId, UUID sessionId) {
        Session session = sessions.findById(sessionId)
                .filter(item -> item.userId().equals(userId))
                .orElseThrow(() -> new AuthFailure(401, "Sesión no válida."));
        sessions.save(session.revoke());
    }

    @Transactional(readOnly = true)
    public User profile(UUID userId) { return requireUser(userId); }

    public User updateProfile(UUID userId, String referenceCurrency) {
        if (!"PEN".equals(referenceCurrency)) {
            throw new AuthFailure(400, "La moneda de referencia debe ser PEN.");
        }
        return users.save(requireUser(userId).withReferenceCurrency(referenceCurrency));
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new AuthFailure(401, "Sesión no válida."));
    }

    private Authentication newSession(User user) {
        byte[] entropy = new byte[32];
        random.nextBytes(entropy);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        Session session = new Session(UUID.randomUUID(), user.id(), digest(refresh),
                Instant.now().plus(Duration.ofDays(30)), false);
        sessions.save(session);
        TokenService.AccessToken access = tokens.issue(user.id(), session.id());
        return new Authentication(access.value(), refresh, access.expiresAt(), user);
    }

    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    public record Authentication(String accessToken, String refreshToken, Instant expiresAt, User user) { }
}
