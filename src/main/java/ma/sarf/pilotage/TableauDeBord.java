package ma.sarf.pilotage;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Indicateurs d'exécution du budget et de respect des délais de paiement.
 *
 * @param delaiMoyenPaiementJours entre la constatation du service fait et le paiement
 * @param partPayesDansLeDelai    part des dossiers payés dans le délai légal, en pourcentage
 */
public record TableauDeBord(
        int exercice,
        List<Ligne> lignes,
        BigDecimal creditsOuverts,
        BigDecimal montantEngage,
        BigDecimal montantPaye,
        BigDecimal tauxEngagement,
        BigDecimal tauxPaiement,
        long engagementsEnAttenteDeVisa,
        long engagementsEnRetardDeVisa,
        Map<String, Long> dossiersParStatut,
        BigDecimal delaiMoyenPaiementJours,
        BigDecimal partPayesDansLeDelai,
        long dossiersEnRetardNonPayes,
        BigDecimal interetsMoratoiresPayes,
        BigDecimal interetsMoratoiresCourus) {

    public record Ligne(String imputation, String libelle, String nature, BigDecimal creditsOuverts,
            BigDecimal montantEngage, BigDecimal montantPaye, BigDecimal tauxEngagement, BigDecimal tauxPaiement) {
    }
}
