package ma.sarf.engagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ma.sarf.commun.domaine.Observation;

/**
 * Résultat du contrôle d'une proposition avant son envoi au comptable : l'ordonnateur corrige les
 * anomalies en amont au lieu d'attendre une suspension.
 */
public record PreControleVue(BigDecimal sommeAValoir, BigDecimal montantAEngager, LocalDate echeanceVisa,
        boolean controleDeRegulariteRequis, List<Observation> anomalies) {
}
