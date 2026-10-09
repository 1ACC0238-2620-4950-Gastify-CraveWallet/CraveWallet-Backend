package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DeliveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    private static final String ROOT = "/api/v1/delivery-expenses";
    private static final LocalDate DATE = LocalDate.of(2026, 9, 8);

    @Test
    void budgetSummaryAndPeriodBoundaries() throws Exception {
        String token = account();
        budget(token, "100").andExpect(status().isOk());
        register(token, body(UUID.randomUUID(), 60, DATE)).andExpect(status().isCreated());
        register(token, body(UUID.randomUUID(), 50, DATE.withDayOfMonth(30))).andExpect(status().isCreated());
        register(token, body(UUID.randomUUID(), 999, DATE.minusMonths(1))).andExpect(status().isCreated());
        summary(token).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(110))
                .andExpect(jsonPath("$.remaining").value(-10)).andExpect(jsonPath("$.exceeded").value(true))
                .andExpect(jsonPath("$.totalsByCategory.Comida").value(110))
                .andExpect(jsonPath("$.totalsByWeek.2").value(60)).andExpect(jsonPath("$.totalsByWeek.5").value(50));
        budget(token, "null").andExpect(status().isOk()).andExpect(jsonPath("$.accumulated").value(110))
                .andExpect(jsonPath("$.exceeded").value(false));
        summary(token).andExpect(jsonPath("$.spendingLimit").isEmpty()).andExpect(jsonPath("$.remaining").isEmpty());
    }

    @Test
    void duplicateRequestDoesNotAddAgainAndConflictingPayloadIsRejected() throws Exception {
        String token = account();
        Map<String, Object> body = body(UUID.randomUUID(), 20, DATE);
        JsonNode first = json.readTree(register(token, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        register(token, body).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.get("id").asText()));
        body.put("amount", 21);
        register(token, body).andExpect(status().isConflict());
        summary(token).andExpect(jsonPath("$.total").value(20));
        budget(token, "100").andExpect(jsonPath("$.accumulated").value(20));
    }

    @Test
    void ownersAreIsolatedAndRequestIdentifiersAreScopedToOwner() throws Exception {
        String first = account(), second = account();
        Map<String, Object> body = body(UUID.randomUUID(), 25, DATE);
        register(first, body).andExpect(status().isCreated());
        summary(second).andExpect(jsonPath("$.total").value(0));
        budget(first, "10").andExpect(jsonPath("$.exceeded").value(true));
        budget(second, "100").andExpect(jsonPath("$.accumulated").value(0));
        register(second, body).andExpect(status().isCreated());
        summary(second).andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.spendingLimit").value(100));
        mvc.perform(get(ROOT + "/summary")).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        mvc.perform(put(ROOT + "/budget").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidInputsDoNotCreateExpensesOrBudgets() throws Exception {
        String token = account();
        for (var field : Map.<String, Object>of("merchant", " ", "amount", 0, "category", " ",
                "expenseDate", LocalDate.now(ZoneId.of("America/Lima")).plusDays(1).toString(), "requestId", "bad").entrySet()) {
            Map<String, Object> body = body(UUID.randomUUID(), 20, DATE); body.put(field.getKey(), field.getValue());
            register(token, body).andExpect(status().isBadRequest());
        }
        budget(token, "0").andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/summary").param("month", "13").param("year", "2026").header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ROOT + "/summary").param("month", "9").header("Authorization", token)).andExpect(status().isBadRequest());
        summary(token).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.spendingLimit").isEmpty());
    }

    @Test
    void simultaneousExpensesAndReplaysKeepOneAccurateAccumulation() throws Exception {
        String token = account();
        var first = body(UUID.randomUUID(), 20, DATE);
        var second = body(UUID.randomUUID(), 30, DATE);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> calls = new ArrayList<>();
            for (var body : List.of(first, first, second)) calls.add(executor.submit(() -> {
                start.await(); return register(token, body).andReturn().getResponse().getStatus();
            }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var call : calls) statuses.add(call.get(15, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 200, 201);
        }
        summary(token).andExpect(jsonPath("$.total").value(50));
        budget(token, "100").andExpect(jsonPath("$.accumulated").value(50));
        assertThat(db.queryForObject("select count(*) from delivery_expenses where request_id in (?, ?)", Integer.class,
                first.get("requestId"), second.get("requestId"))).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.ResultActions register(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post(ROOT).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private org.springframework.test.web.servlet.ResultActions summary(String token) throws Exception {
        return mvc.perform(get(ROOT + "/summary").param("year", "2026").param("month", "9").header("Authorization", token));
    }
    private org.springframework.test.web.servlet.ResultActions budget(String token, String limit) throws Exception {
        return mvc.perform(put(ROOT + "/budget").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"year\":2026,\"month\":9,\"spendingLimit\":" + limit + "}"));
    }
    private Map<String, Object> body(UUID request, int amount, LocalDate date) {
        return new HashMap<>(Map.of("requestId", request, "merchant", "Restaurante", "amount", amount, "category", "Comida", "expenseDate", date.toString()));
    }
    private String account() throws Exception {
        String result = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.com", "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).get("accessToken").asText();
    }
}
