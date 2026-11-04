package ma.sarf.engagement.domaine;

import java.util.List;
import java.util.Objects;
import ma.sarf.commun.domaine.Montant;

/**
 * Contenu de la proposition d'engagement présentée par l'ordonnateur, accompagnée de la fiche
 * d'engagement rappelant l'imputation (décret n° 2-07-1235, art. 4).
 */
public record Proposition(
        String objet,
        TypeCommande typeCommande,
        String referencePiece,
        String fournisseurNom,
        String fournisseurIdentifiant,
        List<LigneDetail> lignes,
        Montant montantPropose) {

    public Proposition {
        if (objet == null || objet.isBlank()) {
            throw new IllegalArgumentException("L'objet de la dépense est obligatoire");
        }
        Objects.requireNonNull(typeCommande);
        if (fournisseurNom == null || fournisseurNom.isBlank()
                || fournisseurIdentifiant == null || fournisseurIdentifiant.isBlank()) {
            throw new IllegalArgumentException("Le fournisseur (nom et identifiant) est obligatoire");
        }
        lignes = List.copyOf(lignes);
        if (lignes.isEmpty()) {
            throw new IllegalArgumentException("La proposition doit comporter au moins une ligne");
        }
        Objects.requireNonNull(montantPropose);
        if (montantPropose.estNul()) {
            throw new IllegalArgumentException("Le montant proposé doit être positif");
        }
        referencePiece = referencePiece == null || referencePiece.isBlank() ? null : referencePiece.strip();
    }

    public Montant totalDesLignes() {
        return lignes.stream().map(LigneDetail::montant).reduce(Montant.ZERO, Montant::plus);
    }
}
