package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendBootstrapTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthChecksApplicationAndLocalDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void localOpenApiIsAvailableWithoutClaimingBusinessEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CraveWallet Backend"))
                .andExpect(jsonPath("$.paths").isEmpty());
    }

    @Test
    void businessRoutesAreNotPublicBeforeAuthenticationIsImplemented() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isUnauthorized());
    }
}
