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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class I18nIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void authenticationErrorReturnsEnglishByDefaultAndSpanishWithAcceptLanguageHeader() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "email", UUID.randomUUID() + "@example.test",
                "password", "WrongPassword1"
        ));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));

        mvc.perform(post("/api/v1/auth/login")
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials."));

        mvc.perform(post("/api/v1/auth/login")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales no válidas."));

        mvc.perform(post("/api/v1/auth/login")
                        .header("Accept-Language", "es-419")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales no válidas."));
    }

    @Test
    void validationAndMalformedJsonErrorsRespectAcceptLanguageHeader() throws Exception {
        String invalidCredentials = json.writeValueAsString(Map.of(
                "email", "not-an-email",
                "password", "Password123"
        ));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCredentials))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Check the request fields."))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("Email must be a valid address."));

        mvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCredentials))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Revisa los campos de la solicitud."))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("El correo debe tener un formato válido."));

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid JSON body."));

        mvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El cuerpo JSON no es válido."));
    }

    @Test
    void openApiDescriptionsDefaultToEnglish() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.description").value(
                        "Authentication, profile, subscriptions, exchange rates, reminder data, delivery expenses/budgets, and Premium billing."))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.summary").value("Sign in — US02 / TS01"));
    }
}
