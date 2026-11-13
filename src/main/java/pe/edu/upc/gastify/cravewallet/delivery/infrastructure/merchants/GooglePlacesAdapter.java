
package pe.edu.upc.gastify.cravewallet.delivery.infrastructure.merchants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import pe.edu.upc.gastify.cravewallet.delivery.application.DeliveryFailure;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MerchantSuggestion;
import pe.edu.upc.gastify.cravewallet.delivery.domain.services.MerchantSearchPort;

import java.util.List;
import java.util.Map;

@Component
public class GooglePlacesAdapter implements MerchantSearchPort {

    private final RestClient client;
    private final String apiKey;

    public GooglePlacesAdapter(
            RestClient.Builder builder,
            @Value("${GOOGLE_PLACES_API_KEY:}") String apiKey
    ) {
        this.client = builder
                .baseUrl("https://places.googleapis.com")
                .build();
        this.apiKey = apiKey;
    }

    @Override
    public List<MerchantSuggestion> search(String query) {

        // Permite iniciar el backend sin credenciales.
        if (apiKey.isBlank()) {
            return List.of();
        }

        try {
            PlacesResponse response = client.post()
                    .uri("/v1/places:searchText")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Goog-Api-Key", apiKey)
                    .header(
                            "X-Goog-FieldMask",
                            "places.id,places.displayName,places.formattedAddress"
                    )
                    .body(Map.of(
                            "textQuery", query + " en Lima, Perú",
                            "languageCode", "es",
                            "regionCode", "PE",
                            "pageSize", 5
                    ))
                    .retrieve()
                    .body(PlacesResponse.class);

            if (response == null || response.places() == null) {
                return List.of();
            }

            return response.places().stream()
                    .filter(place ->
                            place.displayName() != null
                                    && place.displayName().text() != null
                                    && !place.displayName().text().isBlank()
                    )
                    .map(place -> new MerchantSuggestion(
                            place.id(),
                            place.displayName().text(),
                            place.formattedAddress()
                    ))
                    .toList();

        } catch (RestClientException exception) {
            throw new DeliveryFailure(
                    503,
                    "Sugerencias no disponibles. Puedes escribir el comercio manualmente."
            );
        }
    }

    private record PlacesResponse(List<Place> places) {
    }

    private record Place(
            String id,
            DisplayName displayName,
            String formattedAddress
    ) {
    }

    private record DisplayName(String text) {
    }
}
