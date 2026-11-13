
package pe.edu.upc.gastify.cravewallet.delivery.application;

import org.springframework.stereotype.Service;
import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MerchantSuggestion;
import pe.edu.upc.gastify.cravewallet.delivery.domain.services.MerchantSearchPort;

import java.util.List;

@Service
public class MerchantSearchApplicationService {

    private final MerchantSearchPort merchantSearchPort;

    public MerchantSearchApplicationService(
            MerchantSearchPort merchantSearchPort
    ) {
        this.merchantSearchPort = merchantSearchPort;
    }

    public List<MerchantSuggestion> search(String query) {

        if (query == null || query.strip().length() < 2) {
            throw new DeliveryFailure(
                    400,
                    "La búsqueda debe contener al menos 2 caracteres."
            );
        }

        if (query.strip().length() > 100) {
            throw new DeliveryFailure(
                    400,
                    "La búsqueda no puede superar los 100 caracteres."
            );
        }

        return merchantSearchPort.search(query.strip());
    }
}
