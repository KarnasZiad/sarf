package ma.sarf.depense.domaine;

import java.time.LocalDate;
import java.util.Set;

/**
 * Décision du comptable sur une ordonnance. Une suspension est appuyée d'une note motivée
 * comprenant l'ensemble des observations (décret n° 2-07-1235, art. 8).
 */
public record DecisionComptable(Type type, LocalDate date, String comptable, Set<MotifSuspension> motifs, String note) {

    public enum Type {
        VISA_ET_REGLEMENT,
        SUSPENSION,
        /** Visa sous réquisition : la responsabilité du comptable est dégagée (art. 29). */
        VISA_SUR_REQUISITION
    }

    public DecisionComptable {
        motifs = Set.copyOf(motifs);
    }
}
