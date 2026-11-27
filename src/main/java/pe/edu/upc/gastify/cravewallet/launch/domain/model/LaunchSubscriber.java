package pe.edu.upc.gastify.cravewallet.launch.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A landing page visitor who asked to be notified when CraveWallet launches. */
public record LaunchSubscriber(UUID id, String email, String locale, Instant subscribedAt) {
    public LaunchSubscriber {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("email is required");
        if (!"en".equals(locale) && !"es".equals(locale)) throw new IllegalArgumentException("unsupported locale");
    }

    public static LaunchSubscriber create(String email, String locale, Instant now) {
        return new LaunchSubscriber(UUID.randomUUID(), normalize(email), locale, now);
    }

    /** Addresses are compared case-insensitively, so the same visitor is stored once. */
    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
