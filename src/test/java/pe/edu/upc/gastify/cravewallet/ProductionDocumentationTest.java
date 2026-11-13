package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-docs;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "JWT_SECRET=production-docs-test-secret-at-least-32-bytes"
})
@AutoConfigureMockMvc
class ProductionDocumentationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void productionDocumentationIsPublicButUserDataRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/api/v1/delivery-expenses/merchants/suggestions'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/premium/status'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/premium/checkout'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/premium/cancel'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/premium/payments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/premium/webhook'].post").exists());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subscriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
