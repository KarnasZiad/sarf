package ma.sarf.depense.domaine;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.Reglementation;
import ma.sarf.commun.domaine.RegleMetierException;

/**
 * Dossier de paiement d'une facture : du dépôt par le fournisseur jusqu'au règlement par le
 * comptable. C'est sur lui que court le délai légal de 60 jours (décret n° 2-16-344).
 */
public final class DossierPaiement {

    private static final String RGCP_4 = "Décret royal n° 330-66 (RGCP), art. 4";
    private static final String DELAIS = "Décret n° 2-16-344";

    /** Informations reprises de l'engagement visé au moment du dépôt. */
    public record Origine(long engagementId, int exercice, String serviceOrdonnateur, String ordonnateur,
            long ligneBudgetaireId, String imputation, String creancierNom, String creancierIdentifiant) {

        public Origine {
            Objects.requireNonNull(serviceOrdonnateur);
            Objects.requireNonNull(ordonnateur);
            Objects.requireNonNull(imputation);
            Objects.requireNonNull(creancierNom);
            Objects.requireNonNull(creancierIdentifiant);
        }
    }

    /** État complet du dossier, pour la persistance. */
    public record Donnees(Long id, Origine origine, String numeroFacture, Montant montantFacture,
            Montant retenueGarantie, String creancierRib, LocalDate dateDepot, String deposant,
            LocalDate dateCertification, LocalDate dateServiceFait, Montant montantLiquide, String numeroOrdonnance,
            LocalDate dateOrdonnancement, LocalDate dateTransmission, int joursComptableConsommes,
            StatutDossier statut, List<SuspensionDelai> suspensions, List<DecisionComptable> decisions,
            Requisition requisition, LocalDate datePaiement, ModeReglement modeReglement,
            Montant interetsMoratoires, int version) {
    }

    private final Long id;
    private final Origine origine;
    private final String numeroFacture;
    private final Montant montantFacture;
    private final Montant retenueGarantie;
    private final String creancierRib;
    private final LocalDate dateDepot;
    private final String deposant;
    private LocalDate dateCertification;
    private LocalDate dateServiceFait;
    private Montant montantLiquide;
    private String numeroOrdonnance;
    private LocalDate dateOrdonnancement;
    private LocalDate dateTransmission;
    private int joursComptableConsommes;
    private StatutDossier statut;
    private final List<SuspensionDelai> suspensions;
    private final List<DecisionComptable> decisions;
    private Requisition requisition;
    private LocalDate datePaiement;
    private ModeReglement modeReglement;
    private Montant interetsMoratoires;
    private final int version;

    private DossierPaiement(Donnees d) {
        this.id = d.id();
        this.origine = Objects.requireNonNull(d.origine());
        this.numeroFacture = d.numeroFacture();
        this.montantFacture = d.montantFacture();
        this.retenueGarantie = d.retenueGarantie();
        this.creancierRib = d.creancierRib();
        this.dateDepot = d.dateDepot();
        this.deposant = d.deposant();
        this.dateCertification = d.dateCertification();
        this.dateServiceFait = d.dateServiceFait();
        this.montantLiquide = d.montantLiquide();
        this.numeroOrdonnance = d.numeroOrdonnance();
        this.dateOrdonnancement = d.dateOrdonnancement();
        this.dateTransmission = d.dateTransmission();
        this.joursComptableConsommes = d.joursComptableConsommes();
        this.statut = d.statut();
        this.suspensions = new ArrayList<>(d.suspensions());
        this.decisions = new ArrayList<>(d.decisions());
        this.requisition = d.requisition();
        this.datePaiement = d.datePaiement();
        this.modeReglement = d.modeReglement();
        this.interetsMoratoires = d.interetsMoratoires();
        this.version = d.version();
    }

    /** Dépôt de la facture sur un engagement visé ; il fait courir le délai de constatation. */
    public static DossierPaiement deposer(Origine origine, String numeroFacture, Montant montantFacture,
            Montant retenueGarantie, String creancierRib, LocalDate dateDepot, String deposant) {
        if (numeroFacture == null || numeroFacture.isBlank()) {
            throw new IllegalArgumentException("Le numéro de facture est obligatoire");
        }
        if (montantFacture.estNul()) {
            throw new IllegalArgumentException("Le montant de la facture doit être positif");
        }
        if (retenueGarantie.depasse(montantFacture)) {
            throw new IllegalArgumentException("La retenue de garantie dépasse le montant de la facture");
        }
        String rib = creancierRib == null || creancierRib.isBlank() ? null : creancierRib.replace(" ", "");
        return new DossierPaiement(new Donnees(null, origine, numeroFacture.strip(), montantFacture, retenueGarantie,
                rib, dateDepot, deposant, null, null, Montant.ZERO, null, null, null, 0, StatutDossier.DEPOSE,
                List.of(), List.of(), null, null, null, Montant.ZERO, 0));
    }

    public static DossierPaiement reconstituer(Donnees donnees) {
        return new DossierPaiement(donnees);
    }

    public Donnees donnees() {
        return new Donnees(id, origine, numeroFacture, montantFacture, retenueGarantie, creancierRib, dateDepot,
                deposant, dateCertification, dateServiceFait, montantLiquide, numeroOrdonnance, dateOrdonnancement,
                dateTransmission, joursComptableConsommes, statut, List.copyOf(suspensions), List.copyOf(decisions),
                requisition, datePaiement, modeReglement, interetsMoratoires, version);
    }

    /**
     * Certification du service fait par l'ordonnateur. Elle doit intervenir dans les 30 jours du
     * dépôt ; au-delà, la date retenue est le 31e jour (décret n° 2-16-344, art. 5 et 6), pour
     * qu'un retard de l'administration ne retarde pas le point de départ du délai de paiement.
     */
    public void certifierServiceFait(String acteur, LocalDate date, Reglementation reglementation) {
        exigerStatut(StatutDossier.DEPOSE, "certifier le service fait");
        exigerOrdonnateur(acteur, "Décret n° 2-07-1235, art. 8", "certifier le service fait");
        if (date.isBefore(dateDepot)) {
            throw new IllegalArgumentException("Le service fait ne peut pas précéder le dépôt de la facture");
        }
        int delai = reglementation.delaisPaiement().constatationServiceFait();
        LocalDate limite = dateDepot.plusDays(delai);
        dateCertification = date;
        dateServiceFait = date.isAfter(limite) ? dateDepot.plusDays(delai + 1L) : date;
        statut = StatutDossier.SERVICE_FAIT;
    }

    /** Pièces manquantes imputables au bénéficiaire : le délai d'ordonnancement est suspendu (art. 3). */
    public void signalerPiecesManquantes(String acteur, LocalDate date, String motif) {
        if (statut != StatutDossier.SERVICE_FAIT && statut != StatutDossier.LIQUIDE) {
            throw etatIncompatible("suspendre le délai d'ordonnancement");
        }
        exigerOrdonnateur(acteur, DELAIS + ", art. 3", "suspendre le délai");
        if (suspensionEnCours()) {
            throw new RegleMetierException("SUSPENSION_DEJA_EN_COURS", DELAIS + ", art. 3",
                    "Des pièces sont déjà attendues : le délai est déjà suspendu.");
        }
        suspensions.add(new SuspensionDelai(date, null, motif));
    }

    /** Le délai restant reprend à la date de réception des justifications demandées (art. 3). */
    public void recevoirPieces(String acteur, LocalDate date) {
        exigerOrdonnateur(acteur, DELAIS + ", art. 3", "constater la réception des pièces");
        if (!suspensionEnCours()) {
            throw new RegleMetierException("AUCUNE_SUSPENSION", DELAIS + ", art. 3",
                    "Aucune pièce n'est attendue pour ce dossier.");
        }
        SuspensionDelai enCours = suspensions.removeLast();
        suspensions.add(new SuspensionDelai(enCours.debut(), date, enCours.motif()));
    }

    /** Arrêter le montant de la dette au vu des titres du créancier (RGCP, art. 34). */
    public void liquider(String acteur, Montant montant) {
        exigerStatut(StatutDossier.SERVICE_FAIT, "liquider");
        exigerOrdonnateur(acteur, "Décret royal n° 330-66 (RGCP), art. 34", "liquider");
        if (montant.estNul() || montant.depasse(montantFacture)) {
            throw new RegleMetierException("LIQUIDATION_INVALIDE", "Décret royal n° 330-66 (RGCP), art. 34",
                    "Le montant liquidé doit être positif et ne pas dépasser la facture (" + montantFacture + ").");
        }
        montantLiquide = montant;
        statut = StatutDossier.LIQUIDE;
    }

    /**
     * Émission de l'ordonnance de paiement et transmission au comptable (RGCP, art. 35 et 86). Le
     * numéro vient d'une série unique et ininterrompue (art. 36), attribuée par l'appelant.
     */
    public void ordonnancer(String acteur, String numero, LocalDate date) {
        exigerStatut(StatutDossier.LIQUIDE, "ordonnancer");
        exigerOrdonnateur(acteur, "Décret royal n° 330-66 (RGCP), art. 35", "ordonnancer");
        if (suspensionEnCours()) {
            throw new RegleMetierException("PIECES_EN_ATTENTE", DELAIS + ", art. 3",
                    "Ordonnancement impossible : des pièces justificatives sont attendues du bénéficiaire.");
        }
        numeroOrdonnance = Objects.requireNonNull(numero);
        dateOrdonnancement = date;
        dateTransmission = date;
        joursComptableConsommes = 0;
        statut = StatutDossier.ORDONNANCE;
    }

    /**
     * Visa et règlement par le comptable lorsque le contrôle de validité ne relève aucune
     * irrégularité (décret n° 2-07-1235, art. 8).
     */
    public void viserEtPayer(String comptable, LocalDate date, ModeReglement mode, Set<MotifSuspension> anomalies,
            Montant interets, Reglementation reglementation) {
        exigerStatut(StatutDossier.ORDONNANCE, "viser pour paiement");
        exigerComptableDistinct(comptable);
        if (!anomalies.isEmpty()) {
            throw new RegleMetierException("VISA_PAIEMENT_IMPOSSIBLE", "Décret n° 2-07-1235, art. 8",
                    "Visa impossible, le contrôle de validité relève : " + libelles(anomalies) + ".");
        }
        decisions.add(new DecisionComptable(DecisionComptable.Type.VISA_ET_REGLEMENT, date, comptable, Set.of(), null));
        payer(date, mode, interets, reglementation);
    }

    /**
     * Suspension du visa : l'ordonnance est renvoyée à l'ordonnateur avec une note motivée
     * reprenant toutes les observations (art. 8). Le temps déjà passé chez le comptable est
     * mémorisé pour le reliquat de son délai (décret n° 2-16-344, art. 4).
     */
    public void suspendre(String comptable, LocalDate date, Set<MotifSuspension> motifsDuComptable,
            Set<MotifSuspension> anomalies, String note) {
        exigerStatut(StatutDossier.ORDONNANCE, "suspendre le visa");
        exigerComptableDistinct(comptable);
        EnumSet<MotifSuspension> motifs = EnumSet.noneOf(MotifSuspension.class);
        motifs.addAll(motifsDuComptable);
        motifs.addAll(anomalies);
        if (motifs.isEmpty() || note == null || note.isBlank()) {
            throw new RegleMetierException("SUSPENSION_NON_MOTIVEE", "Décret n° 2-07-1235, art. 8",
                    "La suspension doit être appuyée d'une note motivée et d'au moins un motif.");
        }
        joursComptableConsommes += (int) Math.max(0, ChronoUnit.DAYS.between(dateTransmission, date));
        decisions.add(new DecisionComptable(DecisionComptable.Type.SUSPENSION, date, comptable, motifs, note.strip()));
        statut = StatutDossier.SUSPENDU;
    }

    /** L'ordonnateur renvoie l'ordonnance régularisée au comptable. */
    public void regulariser(String acteur, LocalDate date) {
        exigerStatut(StatutDossier.SUSPENDU, "régulariser");
        exigerOrdonnateur(acteur, "Décret n° 2-07-1235, art. 8", "régulariser l'ordonnance");
        dateTransmission = date;
        statut = StatutDossier.ORDONNANCE;
    }

    /**
     * Réquisition de payer, par écrit et sous la responsabilité de l'ordonnateur (art. 29). Elle
     * est irrecevable si la suspension repose sur l'un des motifs de l'article 30.
     */
    public void requerir(String acteur, LocalDate date, String motif) {
        exigerStatut(StatutDossier.SUSPENDU, "requérir le paiement");
        exigerOrdonnateur(acteur, "Décret n° 2-07-1235, art. 29", "requérir le paiement");
        if (motif == null || motif.isBlank()) {
            throw new RegleMetierException("REQUISITION_NON_ECRITE", "Décret n° 2-07-1235, art. 29",
                    "La réquisition doit être formulée par écrit et motivée.");
        }
        Set<MotifSuspension> bloquants = derniereSuspension().motifs().stream()
                .filter(m -> !m.requisitionnable())
                .collect(Collectors.toSet());
        if (!bloquants.isEmpty()) {
            throw requisitionIrrecevable(bloquants);
        }
        requisition = new Requisition(date, acteur, motif.strip());
        statut = StatutDossier.REQUISITIONNE;
    }

    /**
     * Le comptable défère à la réquisition : sa responsabilité est dégagée (art. 29). Les motifs de
     * l'article 30 sont vérifiés à nouveau au moment du paiement.
     */
    public void payerSurRequisition(String comptable, LocalDate date, ModeReglement mode,
            Set<MotifSuspension> anomalies, Montant interets, Reglementation reglementation) {
        exigerStatut(StatutDossier.REQUISITIONNE, "payer sur réquisition");
        exigerComptableDistinct(comptable);
        Set<MotifSuspension> bloquants = anomalies.stream().filter(m -> !m.requisitionnable()).collect(Collectors.toSet());
        if (!bloquants.isEmpty()) {
            throw requisitionIrrecevable(bloquants);
        }
        decisions.add(new DecisionComptable(DecisionComptable.Type.VISA_SUR_REQUISITION, date, comptable,
                derniereSuspension().motifs(), "Paiement sur réquisition de " + requisition.ordonnateur()));
        payer(date, mode, interets, reglementation);
    }

    private void payer(LocalDate date, ModeReglement mode, Montant interets, Reglementation reglementation) {
        Objects.requireNonNull(mode, "mode de règlement");
        if (mode == ModeReglement.ESPECES && montantLiquide.depasse(reglementation.seuilVirementObligatoire())) {
            throw new RegleMetierException("VIREMENT_OBLIGATOIRE", "Décret royal n° 330-66 (RGCP), art. 45",
                    "Au-delà de " + reglementation.seuilVirementObligatoire() + ", le paiement se fait par virement.");
        }
        datePaiement = date;
        modeReglement = mode;
        interetsMoratoires = Objects.requireNonNull(interets);
        statut = StatutDossier.PAYE;
    }

    /** Base des intérêts moratoires : principal TTC diminué de la retenue de garantie (art. 10). */
    public Montant baseInteretsMoratoires() {
        return montantLiquide.depasse(retenueGarantie) ? montantLiquide.moins(retenueGarantie) : Montant.ZERO;
    }

    public boolean suspensionEnCours() {
        return !suspensions.isEmpty() && suspensions.getLast().enCours();
    }

    public boolean payeSurRequisition() {
        return decisions.stream().anyMatch(d -> d.type() == DecisionComptable.Type.VISA_SUR_REQUISITION);
    }

    private DecisionComptable derniereSuspension() {
        return decisions.reversed().stream()
                .filter(d -> d.type() == DecisionComptable.Type.SUSPENSION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucune suspension enregistrée"));
    }

    private RegleMetierException requisitionIrrecevable(Set<MotifSuspension> bloquants) {
        return new RegleMetierException("REQUISITION_IRRECEVABLE", "Décret n° 2-07-1235, art. 30",
                "Le comptable doit refuser de déférer à la réquisition et en rendre compte au ministre chargé des "
                        + "finances : " + libelles(bloquants) + ".");
    }

    private static String libelles(Set<MotifSuspension> motifs) {
        return motifs.stream().sorted().map(MotifSuspension::libelle).collect(Collectors.joining(" ; "));
    }

    private void exigerStatut(StatutDossier attendu, String operation) {
        if (statut != attendu) {
            throw etatIncompatible(operation);
        }
    }

    private RegleMetierException etatIncompatible(String operation) {
        return new RegleMetierException("ETAPE_INCORRECTE", "Décret royal n° 330-66 (RGCP), art. 32",
                "Impossible de " + operation + " : le dossier est au statut " + statut
                        + ". Les dépenses sont engagées, liquidées, ordonnancées puis payées, dans cet ordre.");
    }

    private void exigerOrdonnateur(String acteur, String reference, String operation) {
        if (!origine.ordonnateur().equals(acteur)) {
            throw new RegleMetierException("ACTE_RESERVE_A_L_ORDONNATEUR", reference,
                    "Seul l'ordonnateur de la dépense peut " + operation + ".");
        }
    }

    private void exigerComptableDistinct(String comptable) {
        if (origine.ordonnateur().equals(comptable)) {
            throw new RegleMetierException("INCOMPATIBILITE_FONCTIONS", RGCP_4,
                    "Le comptable ne peut pas payer une dépense qu'il a lui-même ordonnancée.");
        }
    }

    public Long id() {
        return id;
    }

    public Origine origine() {
        return origine;
    }

    public String numeroFacture() {
        return numeroFacture;
    }

    public Montant montantFacture() {
        return montantFacture;
    }

    public Montant retenueGarantie() {
        return retenueGarantie;
    }

    public String creancierRib() {
        return creancierRib;
    }

    public LocalDate dateDepot() {
        return dateDepot;
    }

    public LocalDate dateServiceFait() {
        return dateServiceFait;
    }

    public Montant montantLiquide() {
        return montantLiquide;
    }

    public String numeroOrdonnance() {
        return numeroOrdonnance;
    }

    public LocalDate dateTransmission() {
        return dateTransmission;
    }

    public int joursComptableConsommes() {
        return joursComptableConsommes;
    }

    public StatutDossier statut() {
        return statut;
    }

    public List<SuspensionDelai> suspensions() {
        return List.copyOf(suspensions);
    }

    public List<DecisionComptable> decisions() {
        return List.copyOf(decisions);
    }

    public Requisition requisition() {
        return requisition;
    }

    public LocalDate datePaiement() {
        return datePaiement;
    }

    public ModeReglement modeReglement() {
        return modeReglement;
    }

    public Montant interetsMoratoires() {
        return interetsMoratoires;
    }
}
