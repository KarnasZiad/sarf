package ma.sarf.commun.domaine;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Seuils et délais fixés par les textes. Ils sont paramétrables, et non codés en dur, car
 * l'article 20 du décret n° 2-07-1235 permet de les modifier par arrêté du ministre chargé des
 * finances.
 *
 * @param seuilsRegularite   montants au-delà desquels le contrôle de régularité s'exerce
 * @param delaisVisaEngagement délais du comptable pour viser un engagement, en jours ouvrables
 * @param delaisPaiement     délais de la commande publique, en jours calendaires
 * @param tauxSommeAValoir   majoration de l'engagement, en points de pourcentage, pour couvrir
 *                           les intérêts moratoires (décret n° 2-16-344, art. 12)
 * @param seuilVirementObligatoire au-delà, le paiement se fait uniquement par virement
 *                           (RGCP, art. 45)
 */
public record Reglementation(
        SeuilsRegularite seuilsRegularite,
        DelaisVisaEngagement delaisVisaEngagement,
        DelaisPaiement delaisPaiement,
        BigDecimal tauxSommeAValoir,
        Montant seuilVirementObligatoire) {

    public Reglementation {
        Objects.requireNonNull(seuilsRegularite);
        Objects.requireNonNull(delaisVisaEngagement);
        Objects.requireNonNull(delaisPaiement);
        Objects.requireNonNull(tauxSommeAValoir);
        Objects.requireNonNull(seuilVirementObligatoire);
    }

    /** Valeurs des textes en vigueur lus (version de 2008 du décret n° 2-07-1235). */
    public static Reglementation textesEnVigueur() {
        return new Reglementation(
                new SeuilsRegularite(Montant.de(20_000), Montant.de(100_000), Montant.de(400_000), Montant.de(200_000)),
                new DelaisVisaEngagement(12, 5, 10, 4),
                new DelaisPaiement(30, 45, 15, 60, 5),
                BigDecimal.ONE,
                Montant.de(1_500));
    }

    /**
     * @param regimeNormal          décret n° 2-07-1235, art. 10 : biens et services jusqu'à ce
     *                              montant soumis au seul contrôle budgétaire
     * @param moduleBiensEtServices art. 13 d)
     * @param moduleMarches         art. 13 e)
     * @param moduleContrats        art. 13 g)
     */
    public record SeuilsRegularite(
            Montant regimeNormal,
            Montant moduleBiensEtServices,
            Montant moduleMarches,
            Montant moduleContrats) {
    }

    /**
     * @param normalMarches art. 6 : 12 jours ouvrables francs pour les marchés de l'État
     * @param normalAutres  art. 6 : 5 jours pour les autres dépenses
     * @param moduleMarches art. 13 : 10 jours sous contrôle modulé
     * @param moduleAutres  art. 13 : 4 jours sous contrôle modulé
     */
    public record DelaisVisaEngagement(int normalMarches, int normalAutres, int moduleMarches, int moduleAutres) {
    }

    /**
     * Décret n° 2-16-344.
     *
     * @param constatationServiceFait art. 5 : 30 jours après le dépôt de la facture
     * @param ordonnancement          art. 2 : 45 jours après la constatation du service fait
     * @param visaComptable           art. 2 : 15 jours après réception de l'ordonnance
     * @param global                  art. 2 : 60 jours au total
     * @param reliquatMinimumVisa     art. 4 : après régularisation, le comptable dispose d'au
     *                                moins 5 jours
     */
    public record DelaisPaiement(
            int constatationServiceFait,
            int ordonnancement,
            int visaComptable,
            int global,
            int reliquatMinimumVisa) {
    }
}
