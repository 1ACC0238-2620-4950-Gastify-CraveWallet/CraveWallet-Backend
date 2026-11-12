
package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumMembership;
import pe.edu.upc.gastify.cravewallet.premium.domain.model.PremiumStatus;
import pe.edu.upc.gastify.cravewallet.premium.domain.repositories.PremiumMembershipRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PremiumPlanAccessIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PremiumMembershipRepository memberships;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void activePremiumAllowsSixthSubscription() throws Exception {
        TestAccount user = createAccount();

        // Primero registra cinco suscripciones siendo Free.
        for (int i = 0; i < 5; i++) {
            createSubscription(user.token(), 201);
        }

        // Simula una membresía activa solo en testing.
        saveTestMembership(
                user.ownerId(),
                Instant.now().plusSeconds(30L * 24 * 3600)
        );

        mvc.perform(get("/api/v1/premium/status")
                        .header("Authorization", user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.premium").value(true));

        // Premium debe poder registrar la sexta.
        createSubscription(user.token(), 201);
    }

    @Test
    void expiredPremiumKeepsFreeLimit() throws Exception {
        TestAccount user = createAccount();

        for (int i = 0; i < 5; i++) {
            createSubscription(user.token(), 201);
        }

        // La membresía tiene una fecha de vigencia pasada.
        saveTestMembership(
                user.ownerId(),
                Instant.now().minusSeconds(3600)
        );

        mvc.perform(get("/api/v1/premium/status")
                        .header("Authorization", user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false));

        // Debe rechazar la sexta suscripción.
        createSubscription(user.token(), 409);
    }

    private TestAccount createAccount() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", UUID.randomUUID() + "@example.com",
                "password", "Password123"
        ));

        String response = mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = json.readTree(response);

        return new TestAccount(
                UUID.fromString(data.get("user").get("id").asText()),
                "Bearer " + data.get("accessToken").asText()
        );
    }

    private void saveTestMembership(UUID ownerId, Instant endsAt) {
        TransactionTemplate tx =
                new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status ->
                memberships.save(new PremiumMembership(
                        UUID.randomUUID(),
                        ownerId,
                        PremiumStatus.ACTIVE,
                        endsAt,
                        false
                ))
        );
    }

    private void createSubscription(
            String token,
            int expectedStatus
    ) throws Exception {

        String body = json.writeValueAsString(Map.of(
                "name", "Streaming",
                "amount", 12,
                "currency", "PEN",
                "category", "Entretenimiento",
                "billingCycle", "MONTHLY",
                "nextBillingDate",
                LocalDate.now().plusDays(30).toString()
        ));

        mvc.perform(post("/api/v1/subscriptions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus));
    }

    private record TestAccount(UUID ownerId, String token) {
    }
}
