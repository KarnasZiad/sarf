package ma.sarf.engagement;

import java.math.BigDecimal;

/**
 * Ce que le module dépense a besoin de savoir d'un engagement pour traiter une facture.
 */
public record EngagementPourDepense(
        long id,
        boolean vise,
        int exercice,
        String serviceOrdonnateur,
        String regime,
        String ordonnateur,
        long ligneBudgetaireId,
        String imputation,
        String objet,
        String fournisseurNom,
        String fournisseurIdentifiant,
        BigDecimal resteALiquider) {
}
