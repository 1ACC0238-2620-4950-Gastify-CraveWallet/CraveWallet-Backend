package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private static final String ROOT = "/api/v1/subscriptions";

    @Test
    void ownerIsolationAndAuthenticatedRoutes() throws Exception {
        String first = account(), second = account();
        String id = create(first, body()).get("id").asText();
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT).header("Authorization", second)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get(ROOT + "/" + id).header("Authorization", second)).andExpect(status().isNotFound());
        mvc.perform(patch(ROOT + "/" + id).header("Authorization", second).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":20}")).andExpect(status().isNotFound());
        mvc.perform(post(ROOT + "/" + id + "/cancel").header("Authorization", second)).andExpect(status().isNotFound());
        mvc.perform(get(ROOT + "/" + id).header("Authorization", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(12));
    }

    @Test
    void editingCancellationHistoryAndFreeSlotReuse() throws Exception {
        String token = account();
        String id = create(token, body()).get("id").asText();
        mvc.perform(patch(ROOT + "/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":24.50,\"category\":\"Educación\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(24.5))
                .andExpect(jsonPath("$.category").value("Educación"));
        for (int i = 0; i < 4; i++) create(token, body());
        mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body()))).andExpect(status().isConflict());
        String cancelled = mvc.perform(post(ROOT + "/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String repeated = mvc.perform(post(ROOT + "/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repeated)).isEqualTo(json.readTree(cancelled));
        mvc.perform(get(ROOT).header("Authorization", token)).andExpect(jsonPath("$.items.length()").value(4));
        mvc.perform(get(ROOT).param("status", "CANCELLED").header("Authorization", token))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].cancelledAt").isString())
                .andExpect(jsonPath("$.monthlyTotalsByCurrency").isEmpty());
        mvc.perform(patch(ROOT + "/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":99}")).andExpect(status().isConflict());
        create(token, body());
    }

    @Test
    void invalidRequestsDoNotPersistAndEmptyPatchIsRejected() throws Exception {
        String token = account();
        for (Map.Entry<String, Object> invalid : Map.<String, Object>of("amount", -1, "currency", "EUR",
                "name", " ", "category", " ", "billingCycle", "QUARTERLY",
                "nextBillingDate", "2000-01-01").entrySet()) {
            Map<String, Object> request = body(); request.put(invalid.getKey(), invalid.getValue());
            mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        }
        Map<String, Object> fraction = body(); fraction.put("amount", 1.123);
        mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(fraction))).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT).header("Authorization", token)).andExpect(jsonPath("$.items").isEmpty());
        String id = create(token, body()).get("id").asText();
        for (String invalid : List.of("{}", "{\"category\":\" \"}", "{\"amount\":-1}", "{\"nextBillingDate\":\"2000-01-01\"}"))
            mvc.perform(patch(ROOT + "/" + id).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/" + id).header("Authorization", token)).andExpect(jsonPath("$.amount").value(12));
    }

    @Test
    void totalsRespectCurrenciesCyclesAndFilters() throws Exception {
        String token = account();
        create(token, body());
        Map<String, Object> annual = body(); annual.put("currency", "USD"); annual.put("billingCycle", "ANNUAL");
        annual.put("amount", 120); annual.put("name", "Cloud"); annual.put("category", "Trabajo");
        create(token, annual);
        mvc.perform(get(ROOT).header("Authorization", token)).andExpect(jsonPath("$.monthlyTotalsByCurrency.PEN").value(12))
                .andExpect(jsonPath("$.monthlyTotalsByCurrency.USD").value(10));
        mvc.perform(get(ROOT).param("search", "clO").param("category", "trabajo").header("Authorization", token))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("Cloud"))
                .andExpect(jsonPath("$.monthlyTotalsByCurrency.PEN").doesNotExist());
    }

    @Test
    void concurrentRegistrationsCannotExceedFreeLimit() throws Exception {
        String token = account();
        for (int i = 0; i < 4; i++) create(token, body());
        String payload = json.writeValueAsString(body());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> submit = () -> {
                start.await();
                return mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(payload)).andReturn().getResponse().getStatus();
            };
            Future<Integer> a = executor.submit(submit), b = executor.submit(submit);
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        mvc.perform(get(ROOT).header("Authorization", token)).andExpect(jsonPath("$.items.length()").value(5));
    }

    private String account() throws Exception {
        String result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).get("accessToken").asText();
    }
    private JsonNode create(String token, Map<String, Object> body) throws Exception {
        return json.readTree(mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString());
    }
    private Map<String, Object> body() {
        return new HashMap<>(Map.of("name", "Streaming", "amount", 12, "currency", "PEN", "category", "Entretenimiento",
                "billingCycle", "MONTHLY", "nextBillingDate", LocalDate.now().plusDays(30).toString()));
    }
}
