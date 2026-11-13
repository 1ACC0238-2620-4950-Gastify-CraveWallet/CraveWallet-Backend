
package pe.edu.upc.gastify.cravewallet.delivery.domain.services;

import pe.edu.upc.gastify.cravewallet.delivery.domain.model.MerchantSuggestion;

import java.util.List;

public interface MerchantSearchPort {

    List<MerchantSuggestion> search(String query);
}
