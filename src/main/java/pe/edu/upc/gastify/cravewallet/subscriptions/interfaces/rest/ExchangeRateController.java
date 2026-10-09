package pe.edu.upc.gastify.cravewallet.subscriptions.interfaces.rest;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.ExchangeRateService;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency;

@RestController @SecurityRequirement(name = "bearerAuth")
public class ExchangeRateController {
    private final ExchangeRateService service;
    public ExchangeRateController(ExchangeRateService service) { this.service = service; }
    @GetMapping("/api/v1/exchange-rate")
    public ExchangeRateService.Rate quote(@RequestParam(defaultValue = "USD") Currency from,
                                          @RequestParam(defaultValue = "PEN") Currency to) {
        return service.quote(from, to);
    }
}
