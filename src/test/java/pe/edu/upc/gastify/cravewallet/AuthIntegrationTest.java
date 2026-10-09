package pe.edu.upc.gastify.cravewallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;
    @Autowired JwtEncoder encoder;

    @Test
    void registrationPersistsHashesAndAuthenticatesOwnProfile() throws Exception {
        String email = email();
        JsonNode result = register(email);
        assertThat(result.get("refreshToken").asText()).hasSize(43);
        assertThat(result.get("user").has("passwordHash")).isFalse();
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(result)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.referenceCurrency").value("PEN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        String passwordHash = database.queryForObject("select password_hash from iam_users where email = ?", String.class, email);
        assertThat(passwordHash).startsWith("$2").isNotEqualTo("Password123");
        String refreshHash = database.queryForObject("select refresh_hash from iam_sessions where user_id = ?", String.class,
                UUID.fromString(result.get("user").get("id").asText()));
        assertThat(refreshHash).hasSize(64).isNotEqualTo(result.get("refreshToken").asText());
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email.toUpperCase(), "password", "Password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationDoesNotCreateUser() throws Exception {
        for (String password : List.of("short1", "onlyletters", "12345678", "á".repeat(36) + "1")) {
            String email = email();
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                    .andExpect(status().isBadRequest());
            assertThat(database.queryForObject("select count(*) from iam_users where email = ?", Integer.class, email)).isZero();
        }
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWorksAndFailuresDoNotRevealWhichCredentialFailed() throws Exception {
        String email = email();
        register(email);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString());
        for (String failedEmail : List.of(email, email())) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", failedEmail, "password", "WrongPassword1"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Credenciales no válidas."));
        }
    }

    @Test
    void refreshRotatesBothTokensAndLogoutInvalidatesTheNewPair() throws Exception {
        JsonNode original = register(email());
        JsonNode renewed = json.readTree(mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", original.get("refreshToken").asText()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(original)))
                .andExpect(status().isUnauthorized());
        assertRefreshUnauthorized(original);
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(renewed))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(renewed))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(renewed))).andExpect(status().isUnauthorized());
        assertRefreshUnauthorized(renewed);
    }

    @Test
    void profileUsesAuthenticatedOwnerAndOnlyAcceptsPen() throws Exception {
        JsonNode first = register(email());
        JsonNode second = register(email());
        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("referenceCurrency", "PEN", "userId", second.get("user").get("id").asText()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.get("user").get("id").asText()));
        mvc.perform(patch("/api/v1/users/me").header("Authorization", bearer(first)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"referenceCurrency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedExpiredAndCrossOwnerTokensAreRejected() throws Exception {
        JsonNode first = register(email());
        JsonNode second = register(email());
        String original = first.get("accessToken").asText();
        Map<String, Object> decoded = jsonDecodeClaims(original);
        String forged = original.substring(0, original.lastIndexOf('.') + 1) + "AAAA";
        String expired = signedToken((String) decoded.get("sub"), (String) decoded.get("jti"), Instant.now().minusSeconds(120));
        String wrongOwner = signedToken(second.get("user").get("id").asText(), (String) decoded.get("jti"), Instant.now().plusSeconds(300));
        for (String token : List.of(forged, expired, wrongOwner)) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void concurrentRefreshCanOnlySpendTheTokenOnce() throws Exception {
        JsonNode original = register(email());
        String body = json.writeValueAsString(Map.of("refreshToken", original.get("refreshToken").asText()));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempt = (java.util.concurrent.Callable<Integer>) () -> mvc.perform(post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            var results = executor.invokeAll(List.of(attempt, attempt));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 401);
        }
    }

    private JsonNode register(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void assertRefreshUnauthorized(JsonNode credentials) throws Exception {
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("refreshToken", credentials.get("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    private String signedToken(String subject, String id, Instant expiresAt) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().issuer("cravewallet-backend").subject(subject).id(id)
                        .audience(List.of("cravewallet-mobile")).issuedAt(Instant.now().minusSeconds(600))
                        .expiresAt(expiresAt).build())).getTokenValue();
    }

    private Map<String, Object> jsonDecodeClaims(String token) {
        try {
            return json.readValue(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private static String bearer(JsonNode result) { return "Bearer " + result.get("accessToken").asText(); }
    private static String email() { return UUID.randomUUID() + "@example.test"; }
}
