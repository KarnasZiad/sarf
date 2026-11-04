package ma.sarf.commun.securite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * API sans état, authentifiée par jeton Keycloak (OIDC). Les rôles sont lus dans le claim
 * « roles » (voir {@code application.yml}) ; chaque point d'entrée précise les rôles autorisés.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecuriteConfig {

    private static final String[] CHEMINS_PUBLICS = {
        "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    SecurityFilterChain chaineDeSecurite(HttpSecurity http) throws Exception {
        return http
                // Jetons « bearer », aucun cookie de session : pas d'exposition au CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(autorisations -> autorisations
                        .requestMatchers(CHEMINS_PUBLICS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(serveur -> serveur.jwt(Customizer.withDefaults()))
                .headers(entetes -> entetes
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .build();
    }
}
