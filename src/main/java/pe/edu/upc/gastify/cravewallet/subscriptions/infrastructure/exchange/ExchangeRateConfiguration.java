package pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.exchange;

import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
public class ExchangeRateConfiguration {
    @Bean Clock applicationClock() { return Clock.systemUTC(); }
}
