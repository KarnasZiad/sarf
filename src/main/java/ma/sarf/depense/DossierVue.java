package ma.sarf.depense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import ma.sarf.depense.domaine.Chronometre.EtatDelais;
import ma.sarf.depense.domaine.DecisionComptable;
import ma.sarf.depense.domaine.DossierPaiement;
import ma.sarf.depense.domaine.MotifSuspension;
import ma.sarf.depense.domaine.SuspensionDelai;

/**
 * Dossier tel qu'affiché : étapes franchies, délais légaux, intérêts et anomalies du contrôle.
 *
 * @param interetsMoratoires intérêts liquidés au paiement, ou intérêts courus à ce jour si le
 *                           dossier n'est pas encore payé et que le délai est dépassé
 */
public record DossierVue(
        long id,
        long engagementId,
        int exercice,
        String serviceOrdonnateur,
        String ordonnateur,
        String imputation,
        String creancierNom,
        String creancierIdentifiant,
        String creancierRib,
        String numeroFacture,
        BigDecimal montantFacture,
        BigDecimal retenueGarantie,
        LocalDate dateDepot,
        String deposant,
        LocalDate dateCertification,
        LocalDate dateServiceFait,
        BigDecimal montantLiquide,
        String numeroOrdonnance,
        LocalDate dateOrdonnancement,
        String statut,
        LocalDate datePaiement,
        String modeReglement,
        boolean payeSurRequisition,
        BigDecimal interetsMoratoires,
        Delais delais,
        List<Anomalie> anomalies,
        List<Suspension> suspensions,
        List<Decision> decisions,
        Requisition requisition,
        LocalDate echeanceCertification) {

    public record Delais(LocalDate echeanceOrdonnancement, LocalDate echeancePaiement, LocalDate echeanceVisaComptable,
            long joursSuspendus, long joursDeRetard, boolean ordonnancementEnRetard, boolean visaComptableEnRetard) {

        static Delais de(EtatDelais etat) {
            return new Delais(etat.echeanceOrdonnancement(), etat.echeancePaiement(), etat.echeanceVisaComptable(),
                    etat.joursSuspendus(), etat.joursDeRetard(), etat.ordonnancementEnRetard(),
                    etat.visaComptableEnRetard());
        }
    }

    public record Anomalie(String code, String reference, String libelle, boolean requisitionnable) {

        static Anomalie de(MotifSuspension motif) {
            return new Anomalie(motif.name(), motif.reference(), motif.libelle(), motif.requisitionnable());
        }
    }

    public record Suspension(LocalDate debut, LocalDate fin, String motif) {

        static Suspension de(SuspensionDelai s) {
            return new Suspension(s.debut(), s.fin(), s.motif());
        }
    }

    public record Decision(String type, LocalDate date, String comptable, List<String> motifs, String note) {

        static Decision de(DecisionComptable d) {
            return new Decision(d.type().name(), d.date(), d.comptable(),
                    d.motifs().stream().sorted().map(MotifSuspension::libelle).toList(), d.note());
        }
    }

    public record Requisition(LocalDate date, String ordonnateur, String motif) {
    }

    static DossierVue de(DossierPaiement dossier, EtatDelais etat, BigDecimal interets, Set<MotifSuspension> anomalies,
            LocalDate echeanceCertification) {
        var d = dossier.donnees();
        var o = d.origine();
        var r = d.requisition();
        return new DossierVue(d.id(), o.engagementId(), o.exercice(), o.serviceOrdonnateur(), o.ordonnateur(),
                o.imputation(), o.creancierNom(), o.creancierIdentifiant(), d.creancierRib(), d.numeroFacture(),
                d.montantFacture().valeur(), d.retenueGarantie().valeur(), d.dateDepot(), d.deposant(),
                d.dateCertification(), d.dateServiceFait(), d.montantLiquide().valeur(), d.numeroOrdonnance(),
                d.dateOrdonnancement(), d.statut().name(), d.datePaiement(),
                d.modeReglement() == null ? null : d.modeReglement().name(), dossier.payeSurRequisition(), interets,
                Delais.de(etat), anomalies.stream().sorted().map(Anomalie::de).toList(),
                d.suspensions().stream().map(Suspension::de).toList(),
                d.decisions().stream().map(Decision::de).toList(),
                r == null ? null : new Requisition(r.date(), r.ordonnateur(), r.motif()), echeanceCertification);
    }
}
