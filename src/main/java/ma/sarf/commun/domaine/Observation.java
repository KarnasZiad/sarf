package ma.sarf.commun.domaine;

import java.util.Objects;

/**
 * Anomalie relevée lors d'un contrôle, avec le texte qui la fonde.
 */
public record Observation(String code, String reference, String message) {

    public Observation {
        Objects.requireNonNull(code);
        Objects.requireNonNull(reference);
        Objects.requireNonNull(message);
    }

    /** Forme lisible, utilisée dans les notes adressées à l'ordonnateur. */
    public String libelle() {
        return message + " (" + reference + ")";
    }
}
