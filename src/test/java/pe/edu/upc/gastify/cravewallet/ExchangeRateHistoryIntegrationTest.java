package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExchangeRateHistoryIntegrationTest {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean ExchangeRatePort provider;

    @BeforeEach void rateFixture() {
        org.mockito.Mockito.when(provider.fetchUsdToPen())
                .thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), Instant.now()));
    }

    @Test
    void usdSubscriptionListsRateAndPenAmountPerPastCycleWithVariation() throws Exception {
        LocalDate today = LocalDate.now(LIMA);
        LocalDate next = today.plusDays(10);
        LocalDate twoCyclesAgo = next.minusMonths(2), oneCycleAgo = next.minusMonths(1);
        db.update("delete from exchange_rate_snapshots");
        snapshot(twoCyclesAgo.minusDays(3), "3.70000000");
        snapshot(oneCycleAgo.minusDays(1), "3.80000000");
        String token = account();
        String id = create(token, "USD", next).get("id").asText();

        mvc.perform(get("/api/v1/subscriptions/" + id + "/exchange-rate-history").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.cycles.length()").value(2))
                .andExpect(jsonPath("$.cycles[0].billingDate").value(twoCyclesAgo.toString()))
                .andExpect(jsonPath("$.cycles[0].rate").value(3.7))
                .andExpect(jsonPath("$.cycles[0].amountPen").value(37.0))
                .andExpect(jsonPath("$.cycles[1].billingDate").value(oneCycleAgo.toString()))
                .andExpect(jsonPath("$.cycles[1].amountPen").value(38.0))
                .andExpect(jsonPath("$.variation.percentChange").value(2.7))
                .andExpect(jsonPath("$.variation.direction").value("UP"));
    }

    @Test
    void penSubscriptionsHaveNoConversionAndOtherOwnersGetNotFound() throws Exception {
        String owner = account(), other = account();
        String pen = create(owner, "PEN", LocalDate.now(LIMA).plusDays(5)).get("id").asText();
        mvc.perform(get("/api/v1/subscriptions/" + pen + "/exchange-rate-history").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cycles").isEmpty())
                .andExpect(jsonPath("$.variation").isEmpty());
        mvc.perform(get("/api/v1/subscriptions/" + pen + "/exchange-rate-history").header("Authorization", other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/subscriptions/" + pen + "/exchange-rate-history")).andExpect(status().isUnauthorized());
    }

    @Test
    void quotesFetchedFromTheProviderAreStoredOnce() throws Exception {
        Instant updatedAt = Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        org.mockito.Mockito.when(provider.fetchUsdToPen())
                .thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.71"), updatedAt));
        var service = new pe.edu.upc.gastify.cravewallet.subscriptions.application.ExchangeRateService(provider,
                Clock.systemUTC(), new pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence.JdbcExchangeRateHistory(db));
        service.quote(pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency.USD,
                pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency.PEN);
        var second = new pe.edu.upc.gastify.cravewallet.subscriptions.application.ExchangeRateService(provider,
                Clock.systemUTC(), new pe.edu.upc.gastify.cravewallet.subscriptions.infrastructure.persistence.JdbcExchangeRateHistory(db));
        second.quote(pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency.USD,
                pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription.Currency.PEN);
        assertThat(db.queryForObject("select count(*) from exchange_rate_snapshots where provider_updated_at = ?",
                Integer.class, Timestamp.from(updatedAt))).isEqualTo(1);
    }

    private void snapshot(LocalDate date, String rate) {
        Instant at = date.atTime(9, 0).atZone(LIMA).toInstant();
        db.update("insert into exchange_rate_snapshots (base_currency, quote_currency, provider_updated_at, rate, fetched_at) "
                + "values ('USD', 'PEN', ?, ?, ?)", Timestamp.from(at), new BigDecimal(rate), Timestamp.from(at));
    }
    private String account() throws Exception {
        String result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).get("accessToken").asText();
    }
    private JsonNode create(String token, String currency, LocalDate next) throws Exception {
        Map<String, Object> body = Map.of("name", "Cloud " + currency, "amount", 10, "currency", currency,
                "category", "Productividad", "billingCycle", "MONTHLY", "nextBillingDate", next.toString());
        return json.readTree(mvc.perform(post("/api/v1/subscriptions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
}
