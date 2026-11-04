package ma.sarf.depense.domaine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Période pendant laquelle le délai d'ordonnancement est suspendu pour une raison imputable au
 * bénéficiaire, notamment des pièces justificatives manquantes (décret n° 2-16-344, art. 3).
 *
 * @param fin date de réception des pièces demandées, ou {@code null} si elles sont attendues
 */
public record SuspensionDelai(LocalDate debut, LocalDate fin, String motif) {

    public SuspensionDelai {
        Objects.requireNonNull(debut);
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif de la suspension est obligatoire");
        }
        if (fin != null && fin.isBefore(debut)) {
            throw new IllegalArgumentException("La fin de suspension précède son début");
        }
    }

    public boolean enCours() {
        return fin == null;
    }

    /** Nombre de jours suspendus à la date donnée. */
    public long jours(LocalDate reference) {
        LocalDate terme = fin != null ? fin : reference;
        return Math.max(0, ChronoUnit.DAYS.between(debut, terme));
    }
}
