
package pe.edu.upc.gastify.cravewallet.delivery.domain.model;

import java.util.Objects;

public record MerchantSuggestion(
        String externalId,
        String name,
        String address
) {

    public MerchantSuggestion {
        Objects.requireNonNull(name,
                "El nombre del comercio es obligatorio");

        name = name.strip();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "El comercio debe tener un nombre válido"
            );
        }

        address = address == null ? "" : address.strip();
    }
}
