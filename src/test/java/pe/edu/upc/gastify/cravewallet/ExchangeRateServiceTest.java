package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import pe.edu.upc.gastify.cravewallet.subscriptions.application.*;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency.*;

class ExchangeRateServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    @Test void cacheIsReusedAndRefreshedAfter24Hours() {
        var provider = mock(ExchangeRatePort.class); var clock = new MutableClock();
        when(provider.fetchUsdToPen()).thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), NOW));
        var service = new ExchangeRateService(provider, clock);
        assertThat(service.quote(USD, PEN).rate()).isEqualByComparingTo("3.75");
        clock.now = NOW.plus(Duration.ofHours(23)); service.quote(USD, PEN);
        verify(provider, times(1)).fetchUsdToPen();
        clock.now = NOW.plus(Duration.ofHours(24)); service.quote(USD, PEN);
        verify(provider, times(2)).fetchUsdToPen();
        assertThat(service.quote(PEN, USD).rate()).isEqualByComparingTo("0.2666666667");
    }
    @Test void outageUsesExplicitStaleRateWithBackoffButNeverOlderThanSevenDays() {
        var provider = mock(ExchangeRatePort.class); var clock = new MutableClock();
        when(provider.fetchUsdToPen()).thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), NOW));
        var service = new ExchangeRateService(provider, clock); service.quote(USD, PEN);
        when(provider.fetchUsdToPen()).thenThrow(new IllegalStateException("offline"));
        clock.now = NOW.plus(Duration.ofDays(1));
        assertThat(service.quote(USD, PEN).stale()).isTrue(); service.quote(USD, PEN);
        verify(provider, times(2)).fetchUsdToPen();
        clock.now = NOW.plus(Duration.ofDays(7));
        assertThatThrownBy(() -> service.quote(USD, PEN)).isInstanceOf(SubscriptionFailure.class);
    }
    @Test void noCachedRateReturns503AndIdentityDoesNotCallProvider() {
        var provider = mock(ExchangeRatePort.class); var clock = new MutableClock();
        when(provider.fetchUsdToPen()).thenThrow(new IllegalStateException("offline"));
        var service = new ExchangeRateService(provider, clock);
        assertThat(service.quote(PEN, PEN).rate()).isEqualByComparingTo("1");
        verifyNoInteractions(provider);
        assertThatThrownBy(() -> service.quote(USD, PEN)).isInstanceOfSatisfying(SubscriptionFailure.class,
                failure -> assertThat(failure.status()).isEqualTo(503));
        assertThatThrownBy(() -> service.quote(USD, PEN)).isInstanceOf(SubscriptionFailure.class);
        verify(provider, times(1)).fetchUsdToPen();
    }
    @Test void invalidTimestampDoesNotBecomeUsableRate() {
        var provider = mock(ExchangeRatePort.class);
        when(provider.fetchUsdToPen()).thenReturn(new ExchangeRatePort.ProviderRate(BigDecimal.ONE, NOW.plusSeconds(301)));
        var service = new ExchangeRateService(provider, new MutableClock());
        assertThatThrownBy(() -> service.quote(USD, PEN)).isInstanceOf(SubscriptionFailure.class);
    }
    @Test void simultaneousQueriesOnlyFetchOnce() throws Exception {
        var provider = mock(ExchangeRatePort.class);
        when(provider.fetchUsdToPen()).thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), NOW));
        var service = new ExchangeRateService(provider, new MutableClock());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<BigDecimal> query = () -> { start.await(); return service.quote(USD, PEN).rate(); };
            var a = executor.submit(query); var b = executor.submit(query); start.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS)).isEqualByComparingTo("3.75");
            assertThat(b.get(5, TimeUnit.SECONDS)).isEqualByComparingTo("3.75");
        }
        verify(provider, times(1)).fetchUsdToPen();
    }
    private static class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
