package ma.sarf.commun.config;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.Reglementation;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
public class ReglementationConfig {

    /** Fuseau de l'administration marocaine : les délais se comptent en jours locaux. */
    public static final ZoneId FUSEAU = ZoneId.of("Africa/Casablanca");

    /**
     * Seuils et délais lus dans {@code sarf.reglementation} ; à défaut, ceux des textes.
     * Le domaine reste indépendant de Spring : il ne reçoit qu'un record Java.
     */
    @Bean
    Reglementation reglementation(ConfigurableEnvironment environment) {
        var conversion = new ApplicationConversionService();
        // En YAML, « 20000 » est lu comme un entier : on le convertit explicitement en montant.
        conversion.addConverter(Number.class, Montant.class, nombre -> Montant.de(new BigDecimal(nombre.toString())));
        conversion.addConverter(String.class, Montant.class, Montant::de);
        var binder = new Binder(ConfigurationPropertySources.get(environment),
                new PropertySourcesPlaceholdersResolver(environment), conversion);
        return binder.bind("sarf.reglementation", Reglementation.class)
                .orElseGet(Reglementation::textesEnVigueur);
    }

    /** L'horloge est injectée partout : les tests et la démonstration peuvent avancer le temps. */
    @Bean
    @Profile("!demo")
    Clock horloge() {
        return Clock.system(FUSEAU);
    }
}
