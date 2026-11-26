package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.ExchangeRatePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HistoryAmountRegressionReviewTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean ExchangeRatePort provider;

    @Test
    void changingCurrentPriceMustNotRewriteHistoricalPenAmounts() throws Exception {
        ZoneId lima = ZoneId.of("America/Lima");
        LocalDate next = LocalDate.now(lima).plusDays(10);
        Instant quoteAt = next.minusMonths(2).minusDays(3).atTime(9, 0).atZone(lima).toInstant();
        db.update("delete from exchange_rate_snapshots");
        db.update("insert into exchange_rate_snapshots (base_currency, quote_currency, provider_updated_at, rate, fetched_at) values ('USD','PEN',?,?,?)",
                Timestamp.from(quoteAt), new BigDecimal("3.70"), Timestamp.from(quoteAt));
        when(provider.fetchUsdToPen()).thenReturn(new ExchangeRatePort.ProviderRate(new BigDecimal("3.75"), Instant.now()));

        String registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", UUID.randomUUID()+"@example.com", "password", "ReviewTest123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer "+json.readTree(registered).get("accessToken").asText();
        String created = mvc.perform(post("/api/v1/subscriptions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "name", "Historical price review", "amount", 10, "currency", "USD", "category", "Productividad",
                        "billingCycle", "MONTHLY", "nextBillingDate", next.toString()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();
        JsonNode before = history(token, id);
        assertThat(before.get("cycles").size()).isGreaterThan(0);
        assertThat(before.get("cycles").get(0).get("amountPen").decimalValue()).isEqualByComparingTo("37.00");

        mvc.perform(patch("/api/v1/subscriptions/"+id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20}"))
                .andExpect(status().isOk());
        JsonNode after = history(token, id);
        assertThat(after.get("cycles").get(0).get("billingDate")).isEqualTo(before.get("cycles").get(0).get("billingDate"));
        assertThat(after.get("cycles").get(0).get("amountPen").decimalValue())
                .as("A current price change must not change past billing amounts")
                .isEqualByComparingTo(before.get("cycles").get(0).get("amountPen").decimalValue());
    }

    private JsonNode history(String token, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/subscriptions/"+id+"/exchange-rate-history")
                .header("Authorization", token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
