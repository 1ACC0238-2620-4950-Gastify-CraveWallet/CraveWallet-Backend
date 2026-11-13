package pe.edu.upc.gastify.cravewallet.premium.infrastructure.payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;

@Component
public class MercadoPagoWebhookSignature {
    private final String secret;
    private final Clock clock;

    public MercadoPagoWebhookSignature(@Value("${MERCADOPAGO_WEBHOOK_SECRET:}") String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public String verify(String signature, String requestId, String dataId) {
        if (secret.isBlank()) throw new PremiumFailure(503, "Falta configurar la firma de Mercado Pago.");
        if (signature == null || signature.length() > 256 || requestId == null || dataId == null
                || !requestId.matches("[A-Za-z0-9_-]{1,128}") || !dataId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw invalidSignature();
        }
        var parts = new HashMap<String, String>();
        for (String part : signature.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2 || parts.putIfAbsent(keyValue[0].trim(), keyValue[1].trim()) != null) {
                throw invalidSignature();
            }
        }
        String timestamp = parts.getOrDefault("ts", "");
        String hex = parts.getOrDefault("v1", "");
        if (!timestamp.matches("[0-9]{10}([0-9]{3})?") || !hex.matches("[a-fA-F0-9]{64}")) throw invalidSignature();

        long value = Long.parseLong(timestamp);
        Instant signedAt = timestamp.length() == 13 ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
        Instant now = clock.instant();
        if (signedAt.isBefore(now.minus(Duration.ofHours(24))) || signedAt.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw invalidSignature();
        }

        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected, HexFormat.of().parseHex(hex))) throw invalidSignature();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", failure);
        }
        return dataId;
    }

    private static PremiumFailure invalidSignature() {
        return new PremiumFailure(401, "Firma de Mercado Pago no válida.");
    }
}
