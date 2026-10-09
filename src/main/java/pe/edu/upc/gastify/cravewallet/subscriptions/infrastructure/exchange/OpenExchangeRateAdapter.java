package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import java.net.URI;
import java.net.http.*;
import java.time.*;

/** ACL: solo traduce la cotización USD/PEN; no expone el catálogo del proveedor. */
@Component
public class OpenExchangeRateAdapter implements ExchangeRatePort {
    private final URI endpoint;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public OpenExchangeRateAdapter(ObjectMapper json,
            @Value("${cravewallet.exchange.endpoint:https://open.er-api.com/v6/latest/USD}") String endpoint) {
        this.json = json; this.endpoint = URI.create(endpoint);
    }
    public ProviderRate fetchUsdToPen() {
        try {
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Proveedor no disponible.");
            var body = json.readTree(response.body());
            if (!body.path("result").asText().equals("success") || !body.path("base_code").asText().equals("USD")
                    || !body.path("rates").path("PEN").isNumber() || !body.path("time_last_update_unix").isIntegralNumber())
                throw new IllegalStateException("Respuesta de cotización no válida.");
            return new ProviderRate(body.path("rates").path("PEN").decimalValue(),
                    Instant.ofEpochSecond(body.path("time_last_update_unix").longValue()));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Consulta interrumpida.");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Proveedor no disponible.");
        }
    }
}
