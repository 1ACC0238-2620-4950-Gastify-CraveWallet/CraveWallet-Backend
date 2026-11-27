package pe.edu.upc.gastify.cravewallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US40: a landing page visitor leaves an email address to hear about the launch. */
@SpringBootTest
@AutoConfigureMockMvc
class LaunchSubscriberIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void validAddressIsStoredOnceWithoutAuthentication() throws Exception {
        String local = "visitor-" + UUID.randomUUID();
        subscribe("{\"email\":\"" + local + "@Example.com\",\"locale\":\"en\"}").andExpect(status().isAccepted());
        subscribe("{\"email\":\"" + local.toUpperCase() + "@example.COM\"}").andExpect(status().isAccepted());

        var rows = jdbc.queryForList("select email, locale from launch_subscribers where email = ?", local + "@example.com");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("LOCALE")).isEqualTo("en");
    }

    @Test
    void invalidAddressOrLocaleIsRejectedAndNotStored() throws Exception {
        int before = count();
        subscribe("{\"email\":\"not-an-email\"}").andExpect(status().isBadRequest());
        subscribe("{\"email\":\"someone@example\"}").andExpect(status().isBadRequest());
        subscribe("{\"email\":\"\"}").andExpect(status().isBadRequest());
        subscribe("{\"email\":\"ok-" + UUID.randomUUID() + "@example.com\",\"locale\":\"fr\"}").andExpect(status().isBadRequest());
        assertThat(count()).isEqualTo(before);
    }

    private org.springframework.test.web.servlet.ResultActions subscribe(String body) throws Exception {
        return mvc.perform(post("/api/v1/launch-subscribers").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int count() {
        return jdbc.queryForObject("select count(*) from launch_subscribers", Integer.class);
    }
}
