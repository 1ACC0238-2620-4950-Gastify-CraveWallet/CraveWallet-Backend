
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
                    "delivery.merchants.query_too_short",
                    "Search query must contain at least 2 characters."
            );
        }

        if (query.strip().length() > 100) {
            throw new DeliveryFailure(
                    400,
                    "delivery.merchants.query_too_long",
                    "Search query cannot exceed 100 characters."
            );
        }

        return merchantSearchPort.search(query.strip());
    }
}
