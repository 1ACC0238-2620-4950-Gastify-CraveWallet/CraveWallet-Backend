package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import pe.edu.upc.gastify.cravewallet.subscriptions.interfaces.rest.SubscriptionController;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioConversionTest {
    @Test void annualUsdIsConvertedBeforeRoundingMonthlyAmount() {
        var result = controller(() -> new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), Instant.now())).list(jwt(), Subscription.Status.ACTIVE, null, null);
        assertThat(result.monthlyTotalsByCurrency().get(Subscription.Currency.USD)).isEqualByComparingTo("0.08");
        assertThat(result.monthlyTotalPen()).isEqualByComparingTo("12.31");
        assertThat(result.monthlyTotalsByCategory().get(1).monthlyTotalPen()).isEqualByComparingTo("0.31");
    }
    @Test void missingRateKeepsOriginalPortfolioWithoutPretendingToKnowTotalPen() {
        var result = controller(() -> { throw new IllegalStateException("offline"); }).list(jwt(), Subscription.Status.ACTIVE, null, null);
        assertThat(result.conversionAvailable()).isFalse();
        assertThat(result.monthlyTotalPen()).isNull();
        assertThat(result.exchangeRate()).isNull();
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().getFirst().amount()).isEqualByComparingTo("1");
        assertThat(result.monthlyTotalsByCategory().get(0).monthlyTotalPen()).isEqualByComparingTo("12.00");
        assertThat(result.monthlyTotalsByCategory().get(1).monthlyTotalPen()).isNull();
        assertThat(result.monthlyTotalsByCategory().get(1).monthlyTotalsByCurrency().get(Subscription.Currency.USD))
                .isEqualByComparingTo("0.08");
    }

    @Test void estimatedSavingsUsesFullAnnualChargeBeforeRenewalInLima() {
        var savings = cancelAt(Instant.parse("2026-11-08T04:59:59Z"),
                () -> new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), Instant.now())).estimatedSavings();
        assertThat(savings.amount()).isEqualByComparingTo("1.00");
        assertThat(savings.currency()).isEqualTo(Subscription.Currency.USD);
        assertThat(savings.amountPen()).isEqualByComparingTo("3.75");
    }

    @Test void missingRateKeepsEstimatedSavingsInOriginalCurrency() {
        var savings = cancelAt(Instant.parse("2026-11-08T04:59:59Z"),
                () -> { throw new IllegalStateException("offline"); }).estimatedSavings();
        assertThat(savings.amount()).isEqualByComparingTo("1.00");
        assertThat(savings.currency()).isEqualTo(Subscription.Currency.USD);
        assertThat(savings.amountPen()).isNull();
    }

    @Test void cancellationOnRenewalDateHasNoEstimatedSavings() {
        assertThat(cancelAt(Instant.parse("2026-11-08T05:00:00Z"),
                () -> { throw new AssertionError("La cotización no debe consultarse"); }).estimatedSavings()).isNull();
    }

    private final UUID owner = UUID.randomUUID();
    private Jwt jwt() { return Jwt.withTokenValue("fixture").header("alg", "HS256").subject(owner.toString()).build(); }
    private SubscriptionController controller(ExchangeRatePort provider) {
        var service = mock(SubscriptionApplicationService.class);
        var subscription = new Subscription(UUID.randomUUID(), owner, "Annual", BigDecimal.ONE, Subscription.Currency.USD,
                "Trabajo", Subscription.BillingCycle.ANNUAL, LocalDate.of(2026, 11, 8), Subscription.Status.ACTIVE, null);
        var pen = new Subscription(UUID.randomUUID(), owner, "Netflix", new BigDecimal("12.00"), Subscription.Currency.PEN,
                "Streaming", Subscription.BillingCycle.MONTHLY, LocalDate.of(2026, 11, 8), Subscription.Status.ACTIVE, null);
        when(service.list(owner, Subscription.Status.ACTIVE, null, null)).thenReturn(List.of(subscription, pen));
        return new SubscriptionController(service, new ExchangeRateService(provider, Clock.systemUTC()));
    }

    private SubscriptionController.CancellationResource cancelAt(Instant cancelledAt, ExchangeRatePort provider) {
        var service = mock(SubscriptionApplicationService.class);
        var subscription = new Subscription(UUID.randomUUID(), owner, "Annual", BigDecimal.ONE, Subscription.Currency.USD,
                "Trabajo", Subscription.BillingCycle.ANNUAL, LocalDate.of(2026, 11, 8), Subscription.Status.CANCELLED, cancelledAt);
        when(service.cancel(owner, subscription.id())).thenReturn(subscription);
        return new SubscriptionController(service, new ExchangeRateService(provider, Clock.systemUTC())).cancel(jwt(), subscription.id());
    }
}
