package ma.sarf.depense.domaine;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Partie automatisable du contrôle de validité de la dépense (décret n° 2-07-1235, art. 8). Le
 * comptable peut ajouter ses propres motifs ; il ne peut pas viser si l'un de ceux-ci subsiste.
 */
public final class ControleValidite {

    /** RIB marocain : 24 chiffres. */
    private static final Pattern RIB = Pattern.compile("\\d{24}");

    private ControleValidite() {
    }

    /**
     * @param engagementVise         l'engagement porte-t-il le visa du comptable ?
     * @param creditsDisponibles     les crédits de paiement couvrent-ils le montant liquidé ?
     * @param ordonnateurAccredite   l'ordonnateur est-il accrédité auprès du comptable ?
     */
    public static Set<MotifSuspension> anomalies(DossierPaiement dossier, boolean engagementVise,
            boolean creditsDisponibles, boolean ordonnateurAccredite) {
        EnumSet<MotifSuspension> anomalies = EnumSet.noneOf(MotifSuspension.class);
        if (dossier.montantLiquide().estNul() || dossier.montantLiquide().depasse(dossier.montantFacture())) {
            anomalies.add(MotifSuspension.CALCUL_LIQUIDATION_INEXACT);
        }
        if (!engagementVise) {
            anomalies.add(MotifSuspension.ABSENCE_VISA_ENGAGEMENT);
        }
        if (dossier.creancierRib() == null || !RIB.matcher(dossier.creancierRib()).matches()) {
            anomalies.add(MotifSuspension.DEFAUT_CARACTERE_LIBERATOIRE);
        }
        if (!ordonnateurAccredite) {
            anomalies.add(MotifSuspension.SIGNATURE_NON_QUALIFIEE);
        }
        if (!creditsDisponibles) {
            anomalies.add(MotifSuspension.CREDITS_PAIEMENT_INSUFFISANTS);
        }
        if (dossier.dateServiceFait() == null) {
            anomalies.add(MotifSuspension.PIECES_JUSTIFICATIVES_INCOMPLETES);
        }
        return anomalies;
    }
}
