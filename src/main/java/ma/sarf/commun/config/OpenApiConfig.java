package ma.sarf.commun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI ; Swagger UI se connecte à Keycloak (code d'autorisation avec PKCE) pour
 * essayer l'API avec chacun des comptes de démonstration.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    private static final String KEYCLOAK = "keycloak";

    @Bean
    OpenAPI documentation(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String emetteur) {
        var flux = new OAuthFlow()
                .authorizationUrl(emetteur + "/protocol/openid-connect/auth")
                .tokenUrl(emetteur + "/protocol/openid-connect/token");
        return new OpenAPI()
                .info(new Info()
                        .title("Sarf : chaîne de la dépense publique")
                        .version("0.1.0")
                        .description("Prototype pédagogique fondé sur le décret royal n° 330-66 (RGCP), le décret "
                                + "n° 2-07-1235 (contrôle des dépenses de l'État) et le décret n° 2-16-344 (délais de "
                                + "paiement et intérêts moratoires). Données fictives."))
                .components(new Components().addSecuritySchemes(KEYCLOAK, new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .flows(new OAuthFlows().authorizationCode(flux))))
                .addSecurityItem(new SecurityRequirement().addList(KEYCLOAK));
    }
}
