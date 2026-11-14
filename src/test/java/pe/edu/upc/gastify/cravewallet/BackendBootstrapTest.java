package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "FLUTTER_WEB_ORIGIN=https://1acc0238-2620-4950-gastify-cravewallet.github.io")
@AutoConfigureMockMvc
class BackendBootstrapTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void localFlutterWebCanPreflightRegistrationAndAuthenticatedRequests() throws Exception {
        String origin = "http://localhost:59730";
        mockMvc.perform(options("/api/v1/auth/register")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mockMvc.perform(options("/api/v1/users/me")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mockMvc.perform(get("/api/v1/users/me").header("Origin", origin))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
        mockMvc.perform(options("/api/v1/auth/register")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deployedFlutterWebCanPreflightAuthenticatedRequests() throws Exception {
        String origin = "https://1acc0238-2620-4950-gastify-cravewallet.github.io";
        mockMvc.perform(options("/api/v1/users/me")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @Test
    void healthChecksApplicationAndLocalDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void localOpenApiDocumentsAuthenticationEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CraveWallet Backend"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/subscriptions'].post.responses['201']").exists())
                .andExpect(jsonPath("$.components.schemas.SubscriptionRegisterRequest.properties.name").exists())
                .andExpect(jsonPath("$.components.schemas.DeliveryRegisterRequest.properties.merchant").exists())
                .andExpect(jsonPath("$.components.schemas.DeliveryRegisterRequest.properties.name").doesNotExist());
    }

    @Test
    void businessRoutesAreNotPublicBeforeAuthenticationIsImplemented() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isUnauthorized());
    }
}
