package pe.edu.upc.gastify.cravewallet.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** API con Bearer JWT; sin login por formulario, cookies ni sesiones HTTP. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean localDocumentation = environment.acceptsProfiles(Profiles.of("local", "test", "postgres"));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register",
                            "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated();
                    authorize.requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated();
                    if (localDocumentation) {
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
