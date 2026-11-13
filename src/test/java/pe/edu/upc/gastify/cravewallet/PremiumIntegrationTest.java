
package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PremiumIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void anonymousUserCannotReadPremiumStatus() throws Exception {
        mvc.perform(get("/api/v1/premium/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void newUserStartsWithFreePlan() throws Exception {
        String email = "premium-" + UUID.randomUUID() + "@example.com";

        String body = json.writeValueAsString(Map.of(
                "email", email,
                "password", "Password123"
        ));

        String response = mvc.perform(
                        post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = json.readTree(response)
                .get("accessToken")
                .asText();

        mvc.perform(
                        get("/api/v1/premium/status")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.premium").value(false))
                .andExpect(jsonPath("$.membershipStatus").isEmpty());
    }
}
