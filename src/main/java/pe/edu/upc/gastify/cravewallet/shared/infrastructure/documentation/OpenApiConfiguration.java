package pe.edu.upc.gastify.cravewallet.shared.infrastructure.documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI craveWalletOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CraveWallet Backend").version("0.1.0")
                        .description("Autenticación, perfil y CRUD de suscripciones. Cotizaciones, Delivery y facturación pendientes."))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
    }
}
