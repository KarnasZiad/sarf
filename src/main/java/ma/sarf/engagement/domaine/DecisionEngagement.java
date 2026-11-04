package ma.sarf.engagement.domaine;

import java.time.LocalDate;
import java.util.List;

/**
 * Décision du comptable. Les observations sont regroupées en une seule communication à
 * l'ordonnateur (décret n° 2-07-1235, art. 5).
 */
public record DecisionEngagement(TypeDecision type, LocalDate date, String comptable, List<String> observations) {

    public DecisionEngagement {
        observations = List.copyOf(observations);
    }
}
