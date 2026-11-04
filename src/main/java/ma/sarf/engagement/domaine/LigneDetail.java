package ma.sarf.engagement.domaine;

import java.math.BigDecimal;
import java.util.Objects;
import ma.sarf.commun.domaine.Montant;

/**
 * Ligne de la proposition d'engagement : le comptable vérifie que leur somme correspond au
 * montant proposé (exactitude des calculs, art. 3).
 */
public record LigneDetail(String designation, BigDecimal quantite, Montant prixUnitaire) {

    public LigneDetail {
        if (designation == null || designation.isBlank()) {
            throw new IllegalArgumentException("La désignation est obligatoire");
        }
        Objects.requireNonNull(quantite);
        Objects.requireNonNull(prixUnitaire);
        if (quantite.signum() <= 0) {
            throw new IllegalArgumentException("La quantité doit être positive");
        }
    }

    public Montant montant() {
        return prixUnitaire.fois(quantite);
    }
}
