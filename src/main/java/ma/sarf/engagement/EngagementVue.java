package ma.sarf.engagement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ma.sarf.commun.domaine.Observation;
import ma.sarf.engagement.domaine.DecisionEngagement;
import ma.sarf.engagement.domaine.Engagement;
import ma.sarf.engagement.domaine.LigneDetail;

/**
 * Engagement tel qu'affiché, avec les anomalies que le comptable verra au moment du contrôle.
 */
public record EngagementVue(
        long id,
        int exercice,
        String serviceOrdonnateur,
        String regime,
        long ligneBudgetaireId,
        String imputation,
        String objet,
        String typeCommande,
        String referencePiece,
        String fournisseurNom,
        String fournisseurIdentifiant,
        List<Ligne> lignes,
        BigDecimal montantPropose,
        BigDecimal sommeAValoir,
        BigDecimal montantAEngager,
        BigDecimal montantLiquide,
        String statut,
        LocalDate dateDepot,
        LocalDate echeanceVisa,
        String ordonnateur,
        boolean controleDeRegulariteRequis,
        boolean enRetardDeVisa,
        boolean visaTacitementDu,
        List<Observation> anomalies,
        List<Decision> decisions) {

    public record Ligne(String designation, BigDecimal quantite, BigDecimal prixUnitaire, BigDecimal montant) {

        static Ligne de(LigneDetail ligne) {
            return new Ligne(ligne.designation(), ligne.quantite(), ligne.prixUnitaire().valeur(),
                    ligne.montant().valeur());
        }
    }

    public record Decision(String type, LocalDate date, String comptable, List<String> observations) {

        static Decision de(DecisionEngagement decision) {
            return new Decision(decision.type().name(), decision.date(), decision.comptable(), decision.observations());
        }
    }

    static EngagementVue de(Engagement e, boolean regulariteRequise, LocalDate aujourdhui, List<Observation> anomalies) {
        var p = e.proposition();
        return new EngagementVue(e.id(), e.exercice(), e.serviceOrdonnateur(), e.regime().name(), e.ligneBudgetaireId(),
                e.imputation(), p.objet(), p.typeCommande().name(), p.referencePiece(), p.fournisseurNom(),
                p.fournisseurIdentifiant(), p.lignes().stream().map(Ligne::de).toList(), p.montantPropose().valeur(),
                e.sommeAValoir().valeur(), e.montantAEngager().valeur(), e.montantLiquide().valeur(), e.statut().name(),
                e.dateDepot(), e.echeanceVisa(), e.ordonnateur(), regulariteRequise, e.enRetardDeVisa(aujourdhui),
                e.visaTacitementDu(aujourdhui), anomalies, e.decisions().stream().map(Decision::de).toList());
    }
}
