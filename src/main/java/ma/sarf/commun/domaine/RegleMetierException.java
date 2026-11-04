package ma.sarf.commun.domaine;

import java.util.Objects;

/**
 * Une règle de la réglementation empêche l'opération demandée.
 *
 * <p>Chaque violation porte la référence du texte qui la fonde, pour que l'utilisateur sache
 * pourquoi l'opération est refusée et puisse le vérifier.
 *
 * @param code identifiant stable de la règle, exploitable par un client
 * @param reference article du texte réglementaire, par exemple « Décret n° 2-07-1235, art. 3 »
 */
public class RegleMetierException extends RuntimeException {

    private final String code;
    private final String reference;

    public RegleMetierException(String code, String reference, String message) {
        super(message);
        this.code = Objects.requireNonNull(code);
        this.reference = Objects.requireNonNull(reference);
    }

    public String code() {
        return code;
    }

    public String reference() {
        return reference;
    }
}
