package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.model.Subscription;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.SubscriptionTermsHistoryPort;
import pe.edu.upc.gastify.cravewallet.subscriptions.domain.services.SubscriptionTermsHistoryPort.Terms;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: a create followed at once by an edit can read the same clock value. Recording
 * both terms used to fail on the primary key and the edit answered 409.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TermsHistorySameInstantTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SubscriptionTermsHistoryPort history;

    @Test
    void laterTermsAtTheSameInstantReplaceTheEarlierOnes() throws Exception {
        UUID id = subscription();
        Instant instant = Instant.parse("2030-01-01T00:00:00Z");
        LocalDate next = LocalDate.of(2030, 2, 1);

        history.record(id, new Terms(instant, new BigDecimal("10.00"), next, Subscription.BillingCycle.MONTHLY));
        history.record(id, new Terms(instant, new BigDecimal("20.00"), next, Subscription.BillingCycle.MONTHLY));

        var atInstant = history.find(id).stream().filter(terms -> terms.effectiveFrom().equals(instant)).toList();
        assertThat(atInstant).hasSize(1);
        assertThat(atInstant.getFirst().amount()).isEqualByComparingTo("20.00");
    }

    private UUID subscription() throws Exception {
        String auth = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(auth).get("accessToken").asText();
        String created = mvc.perform(post("/api/v1/subscriptions").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Streaming", "amount", 12, "currency", "PEN",
                                "category", "Entretenimiento", "billingCycle", "MONTHLY",
                                "nextBillingDate", LocalDate.now().plusDays(30).toString()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(created).get("id").asText());
    }
}
