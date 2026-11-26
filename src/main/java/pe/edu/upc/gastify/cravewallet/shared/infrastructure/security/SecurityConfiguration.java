package pe.edu.upc.gastify.cravewallet.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/** API con Bearer JWT; sin login por formulario, cookies ni sesiones HTTP. */
@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean documentationEnabled = environment.getProperty("springdoc.api-docs.enabled", Boolean.class, true);
        String flutterWebOrigin = environment.getProperty("FLUTTER_WEB_ORIGIN", "").trim();
        boolean localWebEnabled = environment.acceptsProfiles(Profiles.of("local & !prod", "postgres & !prod"));
        if (localWebEnabled || !flutterWebOrigin.isBlank()) {
            http.cors(cors -> cors.configurationSource(corsConfiguration(localWebEnabled, flutterWebOrigin)));
        }
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register",
                            "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated();
                    authorize.requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/subscriptions", "/api/v1/subscriptions/*").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/exchange-rate", "/api/v1/subscriptions/*/reminder").authenticated();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/subscriptions", "/api/v1/subscriptions/*/cancel").authenticated();
                    authorize.requestMatchers(HttpMethod.PATCH, "/api/v1/subscriptions/*").authenticated();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/delivery-expenses").authenticated();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/delivery-expenses/summary",
                            "/api/v1/delivery-expenses/merchants/suggestions").authenticated();
                    authorize.requestMatchers(HttpMethod.PUT, "/api/v1/delivery-expenses/budget").authenticated();
                    if (documentationEnabled) {
                        authorize.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/premium/webhook").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/premium/status", "/api/v1/premium/payments").authenticated();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/premium/checkout", "/api/v1/premium/cancel").authenticated();
                    authorize.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    private UrlBasedCorsConfigurationSource corsConfiguration(boolean localWebEnabled, String flutterWebOrigin) {
        var configuration = new CorsConfiguration();
        var origins = new ArrayList<String>();
        if (localWebEnabled) {
            origins.add("http://localhost:[*]");
            origins.add("http://127.0.0.1:[*]");
        }
        if (!flutterWebOrigin.isBlank()) origins.add(flutterWebOrigin);
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        return source;
    }
}
