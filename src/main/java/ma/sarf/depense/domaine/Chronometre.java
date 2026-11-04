package ma.sarf.depense.domaine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import ma.sarf.commun.domaine.Reglementation;

/**
 * Délais légaux d'un dossier (décret n° 2-16-344, art. 2 à 4) :
 * <ul>
 *   <li>60 jours au total à compter de la constatation du service fait ;</li>
 *   <li>dont 45 pour ordonnancer et 15 pour le visa et le règlement par le comptable ;</li>
 *   <li>prolongés de la durée des suspensions imputables au bénéficiaire (art. 3) ;</li>
 *   <li>après une suspension du visa, le comptable dispose d'au moins 5 jours (art. 4).</li>
 * </ul>
 */
public final class Chronometre {

    private Chronometre() {
    }

    /**
     * @param dateServiceFait      départ des délais ; {@code null} tant que le service fait n'est pas constaté
     * @param echeanceVisaComptable limite du comptable si l'ordonnance est entre ses mains, sinon {@code null}
     * @param joursDeRetard        retard à la date de paiement, ou à la date de référence si non payé
     */
    public record EtatDelais(
            LocalDate dateServiceFait,
            long joursSuspendus,
            LocalDate echeanceOrdonnancement,
            LocalDate echeancePaiement,
            LocalDate echeanceVisaComptable,
            long joursDeRetard,
            boolean ordonnancementEnRetard,
            boolean visaComptableEnRetard) {

        static EtatDelais nonDemarre() {
            return new EtatDelais(null, 0, null, null, null, 0, false, false);
        }

        public boolean enRetard() {
            return joursDeRetard > 0;
        }
    }

    public static EtatDelais calculer(DossierPaiement dossier, LocalDate reference, Reglementation reglementation) {
        LocalDate serviceFait = dossier.dateServiceFait();
        if (serviceFait == null) {
            return EtatDelais.nonDemarre();
        }
        var delais = reglementation.delaisPaiement();
        long joursSuspendus = dossier.suspensions().stream().mapToLong(s -> s.jours(reference)).sum();
        LocalDate echeanceOrdonnancement = serviceFait.plusDays(delais.ordonnancement() + joursSuspendus);
        LocalDate echeancePaiement = serviceFait.plusDays(delais.global() + joursSuspendus);

        LocalDate echeanceVisa = null;
        if (dossier.statut() == StatutDossier.ORDONNANCE) {
            int consommes = dossier.joursComptableConsommes();
            int restant = delais.visaComptable() - consommes;
            if (consommes > 0) {
                restant = Math.max(restant, delais.reliquatMinimumVisa());
            }
            echeanceVisa = dossier.dateTransmission().plusDays(restant);
        }

        LocalDate terme = dossier.datePaiement() != null ? dossier.datePaiement() : reference;
        long retard = Math.max(0, ChronoUnit.DAYS.between(echeancePaiement, terme));
        boolean avantOrdonnancement = dossier.statut() == StatutDossier.SERVICE_FAIT
                || dossier.statut() == StatutDossier.LIQUIDE;
        return new EtatDelais(serviceFait, joursSuspendus, echeanceOrdonnancement, echeancePaiement, echeanceVisa,
                retard,
                avantOrdonnancement && reference.isAfter(echeanceOrdonnancement),
                echeanceVisa != null && reference.isAfter(echeanceVisa));
    }
}
