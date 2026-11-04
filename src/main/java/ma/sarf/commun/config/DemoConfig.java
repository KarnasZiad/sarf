package ma.sarf.commun.config;

import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil « demo » : horloge déplaçable par l'administrateur pour simuler l'écoulement des délais.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
public class DemoConfig {

    @Bean
    HorlogeSimulee horloge() {
        return new HorlogeSimulee(Instant.now(), ReglementationConfig.FUSEAU);
    }

    @RestController
    @RequestMapping("/api/demo/horloge")
    static class HorlogeController {

        private final HorlogeSimulee horloge;

        HorlogeController(HorlogeSimulee horloge) {
            this.horloge = horloge;
        }

        @GetMapping
        Map<String, LocalDate> consulter() {
            return Map.of("date", LocalDate.now(horloge));
        }

        @PostMapping
        @PreAuthorize("hasRole('ADMINISTRATEUR')")
        Map<String, LocalDate> deplacer(@RequestBody @jakarta.validation.Valid NouvelleDate nouvelleDate) {
            horloge.allerAu(nouvelleDate.date());
            return Map.of("date", LocalDate.now((Clock) horloge));
        }

        record NouvelleDate(@NotNull LocalDate date) {
        }
    }
}
