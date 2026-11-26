package pe.edu.upc.gastify.cravewallet.premium.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumBillingService;
import pe.edu.upc.gastify.cravewallet.premium.application.PremiumFailure;
import pe.edu.upc.gastify.cravewallet.premium.infrastructure.payments.MercadoPagoWebhookSignature;

@RestController
public class PremiumWebhookController {
    private final MercadoPagoWebhookSignature signatures;
    private final PremiumBillingService billing;

    public PremiumWebhookController(MercadoPagoWebhookSignature signatures, PremiumBillingService billing) {
        this.signatures = signatures;
        this.billing = billing;
    }

    @PostMapping("/api/v1/premium/webhook")
    @Operation(summary = "Receive a signed notification from Mercado Pago")
    public void notify(@RequestHeader(value = "x-signature", required = false) String signature,
                       @RequestHeader(value = "x-request-id", required = false) String requestId,
                       @RequestParam(value = "data.id", required = false) String resourceId,
                       @RequestBody JsonNode event) {
        String verifiedId = signatures.verify(signature, requestId, resourceId);
        if (!verifiedId.equalsIgnoreCase(event.path("data").path("id").asText())) {
            throw new PremiumFailure(400, "premium.webhook.id_mismatch",
                    "Notification does not match the signed identifier.");
        }
        billing.receiveNotification(requestId, event.path("type").asText(), verifiedId);
    }
}
