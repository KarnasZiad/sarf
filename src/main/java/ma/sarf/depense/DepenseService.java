package ma.sarf.depense;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import ma.sarf.budget.BudgetService;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.commun.domaine.Reglementation;
import ma.sarf.commun.domaine.RegleMetierException;
import ma.sarf.depense.domaine.Chronometre;
import ma.sarf.depense.domaine.Chronometre.EtatDelais;
import ma.sarf.depense.domaine.ControleValidite;
import ma.sarf.depense.domaine.DossierPaiement;
import ma.sarf.depense.domaine.InteretsMoratoires;
import ma.sarf.depense.domaine.ModeReglement;
import ma.sarf.depense.domaine.MotifSuspension;
import ma.sarf.depense.domaine.StatutDossier;
import ma.sarf.depense.persistance.CompteurOrdonnances;
import ma.sarf.depense.persistance.Dossiers;
import ma.sarf.depense.persistance.TauxInteretsMoratoires;
import ma.sarf.engagement.EngagementPourDepense;
import ma.sarf.engagement.EngagementService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface publique du module dépense : du dépôt de la facture au paiement.
 */
@Service
public class DepenseService {

    private static final String DOUBLE_PAIEMENT_REF = "Décret n° 2-07-1235, art. 12";

    public enum DecisionDuComptable { VISER, SUSPENDRE }

    private final Dossiers dossiers;
    private final CompteurOrdonnances compteur;
    private final TauxInteretsMoratoires taux;
    private final EngagementService engagements;
    private final BudgetService budget;
    private final Reglementation reglementation;
    private final Clock horloge;
    private final ApplicationEventPublisher evenements;

    DepenseService(Dossiers dossiers, CompteurOrdonnances compteur, TauxInteretsMoratoires taux,
            EngagementService engagements, BudgetService budget, Reglementation reglementation, Clock horloge,
            ApplicationEventPublisher evenements) {
        this.dossiers = dossiers;
        this.compteur = compteur;
        this.taux = taux;
        this.engagements = engagements;
        this.budget = budget;
        this.reglementation = reglementation;
        this.horloge = horloge;
        this.evenements = evenements;
    }

    /**
     * Dépôt d'une facture. Une même créance ne peut être déposée qu'une fois : la règle est
     * vérifiée ici, puis garantie par une contrainte d'unicité en base.
     */
    @Transactional
    public DossierVue deposer(long engagementId, String numeroFacture, BigDecimal montantFacture,
            BigDecimal retenueGarantie, String rib, String acteur) {
        EngagementPourDepense engagement = engagements.pourDepense(engagementId);
        if (!engagement.vise()) {
            throw new RegleMetierException("ENGAGEMENT_NON_VISE", "Décret n° 2-07-1235, art. 7",
                    "Une facture ne peut être déposée que sur un engagement visé par le comptable.");
        }
        if (dossiers.factureDejaDeposee(engagement.fournisseurIdentifiant(), numeroFacture)) {
            throw doublePaiement(numeroFacture);
        }
        var origine = new DossierPaiement.Origine(engagement.id(), engagement.exercice(), engagement.serviceOrdonnateur(),
                engagement.ordonnateur(), engagement.ligneBudgetaireId(), engagement.imputation(),
                engagement.fournisseurNom(), engagement.fournisseurIdentifiant());
        DossierPaiement dossier = DossierPaiement.deposer(origine, numeroFacture, Montant.de(montantFacture),
                Montant.de(retenueGarantie == null ? BigDecimal.ZERO : retenueGarantie), rib, aujourdhui(), acteur);
        long id;
        try {
            id = dossiers.inserer(dossier);
        } catch (DuplicateKeyException exception) {
            throw doublePaiement(numeroFacture);
        }
        publier(id, "FACTURE_DEPOSEE", acteur, "Facture " + numeroFacture + " de " + dossier.montantFacture()
                + " sur l'engagement " + engagementId);
        return consulter(id);
    }

    @Transactional
    public DossierVue certifierServiceFait(long id, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.certifierServiceFait(acteur, aujourdhui(), reglementation);
        return enregistrer(dossier, "SERVICE_FAIT_CERTIFIE", acteur, "Service fait retenu au " + dossier.dateServiceFait());
    }

    @Transactional
    public DossierVue signalerPiecesManquantes(long id, String motif, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.signalerPiecesManquantes(acteur, aujourdhui(), motif);
        return enregistrer(dossier, "DELAI_SUSPENDU", acteur, "Pièces demandées au bénéficiaire : " + motif);
    }

    @Transactional
    public DossierVue recevoirPieces(long id, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.recevoirPieces(acteur, aujourdhui());
        return enregistrer(dossier, "DELAI_REPRIS", acteur, "Pièces reçues, le délai reprend");
    }

    /** La liquidation est aussi imputée sur l'engagement, verrouillé, qu'elle ne peut pas dépasser. */
    @Transactional
    public DossierVue liquider(long id, BigDecimal montant, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.liquider(acteur, Montant.de(montant));
        engagements.enregistrerLiquidation(dossier.origine().engagementId(), dossier.montantLiquide().valeur());
        return enregistrer(dossier, "DEPENSE_LIQUIDEE", acteur, "Montant liquidé : " + dossier.montantLiquide());
    }

    @Transactional
    public DossierVue ordonnancer(long id, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        String numero = compteur.prochainNumero(dossier.origine().serviceOrdonnateur(), dossier.origine().exercice());
        dossier.ordonnancer(acteur, numero, aujourdhui());
        return enregistrer(dossier, "ORDONNANCE_EMISE", acteur, "Ordonnance " + numero + " transmise au comptable");
    }

    /**
     * Contrôle de validité par le comptable : visa et règlement, ou suspension motivée.
     */
    @Transactional
    public DossierVue controler(long id, DecisionDuComptable decision, Set<MotifSuspension> motifs, String note,
            ModeReglement mode, String comptable) {
        DossierPaiement dossier = verrouiller(id);
        Set<MotifSuspension> anomalies = anomalies(dossier);
        LocalDate aujourdhui = aujourdhui();
        if (decision == DecisionDuComptable.VISER) {
            Montant interets = interets(dossier, aujourdhui).total();
            dossier.viserEtPayer(comptable, aujourdhui, mode, anomalies, interets, reglementation);
            budget.constaterPaiement(dossier.origine().ligneBudgetaireId(), dossier.montantLiquide().valeur());
            return enregistrer(dossier, "DEPENSE_PAYEE", comptable, "Visa et règlement de " + dossier.montantLiquide()
                    + " par " + mode + (interets.estNul() ? "" : ", intérêts moratoires dus : " + interets));
        }
        dossier.suspendre(comptable, aujourdhui, motifs == null ? Set.of() : motifs, anomalies, note);
        return enregistrer(dossier, "VISA_PAIEMENT_SUSPENDU", comptable, "Note : " + note);
    }

    @Transactional
    public DossierVue regulariser(long id, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.regulariser(acteur, aujourdhui());
        return enregistrer(dossier, "ORDONNANCE_REGULARISEE", acteur, "Ordonnance renvoyée au comptable");
    }

    @Transactional
    public DossierVue requerir(long id, String motif, String acteur) {
        DossierPaiement dossier = verrouiller(id);
        dossier.requerir(acteur, aujourdhui(), motif);
        return enregistrer(dossier, "PAIEMENT_REQUIS", acteur, "Réquisition écrite : " + motif);
    }

    @Transactional
    public DossierVue payerSurRequisition(long id, ModeReglement mode, String comptable) {
        DossierPaiement dossier = verrouiller(id);
        LocalDate aujourdhui = aujourdhui();
        Montant interets = interets(dossier, aujourdhui).total();
        dossier.payerSurRequisition(comptable, aujourdhui, mode, anomalies(dossier), interets, reglementation);
        budget.constaterPaiement(dossier.origine().ligneBudgetaireId(), dossier.montantLiquide().valeur());
        return enregistrer(dossier, "DEPENSE_PAYEE_SUR_REQUISITION", comptable,
                "Paiement de " + dossier.montantLiquide() + " sur réquisition, responsabilité du comptable dégagée");
    }

    @Transactional(readOnly = true)
    public DossierVue consulter(long id) {
        return vue(dossiers.trouver(id).orElseThrow(() -> new ObjetIntrouvableException("Dossier de paiement", id)));
    }

    @Transactional(readOnly = true)
    public List<DossierVue> lister(StatutDossier statut) {
        return dossiers.lister(statut).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public List<DossierVue> duneAnnee(int exercice) {
        return dossiers.duneAnnee(exercice).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public InteretsMoratoires.Calcul detailDesInterets(long id) {
        DossierPaiement dossier = dossiers.trouver(id)
                .orElseThrow(() -> new ObjetIntrouvableException("Dossier de paiement", id));
        return interets(dossier, dossier.datePaiement() != null ? dossier.datePaiement() : aujourdhui());
    }

    @Transactional(readOnly = true)
    public List<TauxTrimestriel> taux() {
        return taux.lister();
    }

    /**
     * Enregistre le taux d'un trimestre à partir du taux moyen pondéré des bons du Trésor à 3 mois
     * du trimestre précédent (décret n° 2-16-344, art. 11).
     */
    @Transactional
    public TauxTrimestriel enregistrerTaux(int annee, int trimestre, BigDecimal tauxMoyenBonsDuTresor, String source,
            String acteur) {
        var enregistre = new TauxTrimestriel(annee, trimestre,
                InteretsMoratoires.tauxDepuisBonsDuTresor(tauxMoyenBonsDuTresor), source.strip());
        taux.enregistrer(enregistre);
        publier(0, "TAUX_INTERETS_ENREGISTRE", acteur, "T" + trimestre + " " + annee + " : " + enregistre.taux()
                + " % (bons du Trésor à " + tauxMoyenBonsDuTresor + " %), source : " + enregistre.source());
        return enregistre;
    }

    private Set<MotifSuspension> anomalies(DossierPaiement dossier) {
        var origine = dossier.origine();
        EngagementPourDepense engagement = engagements.pourDepense(origine.engagementId());
        boolean credits = budget.creditsDePaiementDisponibles(origine.ligneBudgetaireId(), dossier.montantLiquide().valeur());
        boolean accredite = engagements.estAccredite(origine.ordonnateur(), origine.serviceOrdonnateur());
        return ControleValidite.anomalies(dossier, engagement.vise(), credits, accredite);
    }

    private InteretsMoratoires.Calcul interets(DossierPaiement dossier, LocalDate datePaiement) {
        EtatDelais etat = Chronometre.calculer(dossier, datePaiement, reglementation);
        if (etat.echeancePaiement() == null) {
            return InteretsMoratoires.Calcul.AUCUN;
        }
        return InteretsMoratoires.calculer(dossier.baseInteretsMoratoires(), etat.echeancePaiement(), datePaiement, taux);
    }

    private DossierVue vue(DossierPaiement dossier) {
        LocalDate aujourdhui = aujourdhui();
        EtatDelais etat = Chronometre.calculer(dossier, aujourdhui, reglementation);
        BigDecimal interets;
        if (dossier.statut() == StatutDossier.PAYE) {
            interets = dossier.interetsMoratoires().valeur();
        } else {
            interets = etat.enRetard() ? interets(dossier, aujourdhui).total().valeur() : BigDecimal.ZERO.setScale(2);
        }
        boolean entreLesMainsDuComptable = dossier.statut() == StatutDossier.ORDONNANCE
                || dossier.statut() == StatutDossier.REQUISITIONNE;
        Set<MotifSuspension> anomalies = entreLesMainsDuComptable ? anomalies(dossier) : EnumSet.noneOf(MotifSuspension.class);
        return DossierVue.de(dossier, etat, interets, anomalies);
    }

    private DossierPaiement verrouiller(long id) {
        return dossiers.trouverPourMiseAJour(id).orElseThrow(() -> new ObjetIntrouvableException("Dossier de paiement", id));
    }

    private DossierVue enregistrer(DossierPaiement dossier, String action, String acteur, String details) {
        dossiers.mettreAJour(dossier);
        publier(dossier.id(), action, acteur, details);
        return consulter(dossier.id());
    }

    private void publier(long id, String action, String acteur, String details) {
        evenements.publishEvent(new EvenementDossier(id, action, acteur, details));
    }

    private static RegleMetierException doublePaiement(String numeroFacture) {
        return new RegleMetierException("DOUBLE_PAIEMENT", DOUBLE_PAIEMENT_REF,
                "La facture " + numeroFacture + " de ce fournisseur a déjà été déposée : une même créance ne peut "
                        + "pas être payée deux fois.");
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }
}
