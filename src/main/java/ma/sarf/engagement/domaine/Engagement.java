package ma.sarf.engagement.domaine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ma.sarf.commun.domaine.CalendrierOuvrable;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.Observation;
import ma.sarf.commun.domaine.Reglementation;
import ma.sarf.commun.domaine.RegleMetierException;

/**
 * Engagement de dépense : l'acte par lequel l'État crée une obligation de nature à entraîner une
 * charge (RGCP, art. 33). Il ne devient définitif qu'avec le visa du comptable public.
 */
public final class Engagement {

    static final String RGCP_4 = "Décret royal n° 330-66 (RGCP), art. 4";
    static final String CONTROLE_ART_3 = "Décret n° 2-07-1235, art. 3";
    static final String CONTROLE_ART_5 = "Décret n° 2-07-1235, art. 5";

    private final Long id;
    private final int exercice;
    private final String serviceOrdonnateur;
    private final RegimeControle regime;
    private final long ligneBudgetaireId;
    private final String imputation;
    private final String ordonnateur;
    private Proposition proposition;
    private Montant sommeAValoir;
    private Montant montantLiquide;
    private StatutEngagement statut;
    private LocalDate dateDepot;
    private LocalDate echeanceVisa;
    private final List<DecisionEngagement> decisions;
    private final int version;

    private Engagement(Long id, int exercice, String serviceOrdonnateur, RegimeControle regime,
            long ligneBudgetaireId, String imputation, String ordonnateur, Proposition proposition,
            Montant sommeAValoir, Montant montantLiquide, StatutEngagement statut, LocalDate dateDepot,
            LocalDate echeanceVisa, List<DecisionEngagement> decisions, int version) {
        this.id = id;
        this.exercice = exercice;
        this.serviceOrdonnateur = serviceOrdonnateur;
        this.regime = regime;
        this.ligneBudgetaireId = ligneBudgetaireId;
        this.imputation = imputation;
        this.ordonnateur = ordonnateur;
        this.proposition = proposition;
        this.sommeAValoir = sommeAValoir;
        this.montantLiquide = montantLiquide;
        this.statut = statut;
        this.dateDepot = dateDepot;
        this.echeanceVisa = echeanceVisa;
        this.decisions = new ArrayList<>(decisions);
        this.version = version;
    }

    /**
     * Dépôt de la proposition. Toute commande publique est engagée pour son montant majoré d'une
     * somme à valoir de 1 % destinée aux éventuels intérêts moratoires (décret n° 2-16-344,
     * art. 12). Le comptable dispose d'un délai en jours ouvrables francs (décret n° 2-07-1235,
     * art. 6 et 13).
     */
    public static Engagement soumettre(int exercice, String serviceOrdonnateur, RegimeControle regime,
            long ligneBudgetaireId, String imputation, String ordonnateur, Proposition proposition,
            LocalDate aujourdhui, Reglementation reglementation, CalendrierOuvrable calendrier) {
        Objects.requireNonNull(ordonnateur);
        Engagement engagement = new Engagement(null, exercice, serviceOrdonnateur, Objects.requireNonNull(regime),
                ligneBudgetaireId, imputation, ordonnateur, Objects.requireNonNull(proposition), Montant.ZERO,
                Montant.ZERO, StatutEngagement.SOUMIS, aujourdhui, null, List.of(), 0);
        engagement.deposer(proposition, aujourdhui, reglementation, calendrier);
        return engagement;
    }

    public static Engagement reconstituer(long id, int exercice, String serviceOrdonnateur, RegimeControle regime,
            long ligneBudgetaireId, String imputation, String ordonnateur, Proposition proposition,
            Montant sommeAValoir, Montant montantLiquide, StatutEngagement statut, LocalDate dateDepot,
            LocalDate echeanceVisa, List<DecisionEngagement> decisions, int version) {
        return new Engagement(id, exercice, serviceOrdonnateur, regime, ligneBudgetaireId, imputation, ordonnateur,
                proposition, sommeAValoir, montantLiquide, statut, dateDepot, echeanceVisa, decisions, version);
    }

    private void deposer(Proposition nouvelle, LocalDate date, Reglementation reglementation,
            CalendrierOuvrable calendrier) {
        this.proposition = nouvelle;
        this.sommeAValoir = nouvelle.montantPropose().pourcentage(reglementation.tauxSommeAValoir());
        this.dateDepot = date;
        this.echeanceVisa = calendrier.ajouterJoursOuvrables(date, delaiDeVisa(reglementation));
        this.statut = StatutEngagement.SOUMIS;
    }

    /** Délai du comptable pour viser, suspendre ou refuser (art. 6 ; art. 13 en contrôle modulé). */
    int delaiDeVisa(Reglementation reglementation) {
        var delais = reglementation.delaisVisaEngagement();
        boolean marche = proposition.typeCommande() == TypeCommande.MARCHE;
        return regime == RegimeControle.NORMAL
                ? (marche ? delais.normalMarches() : delais.normalAutres())
                : (marche ? delais.moduleMarches() : delais.moduleAutres());
    }

    /**
     * Le contrôle de régularité ne porte que sur les dépenses au-delà de certains seuils : 20 000 DH
     * pour les biens et services en régime normal (art. 10), des seuils plus élevés selon la forme
     * de la commande en contrôle modulé (art. 13). En dessous, seul le contrôle budgétaire s'exerce.
     */
    public boolean exigeControleDeRegularite(Reglementation reglementation) {
        var seuils = reglementation.seuilsRegularite();
        Montant seuil = switch (regime) {
            case NORMAL -> seuils.regimeNormal();
            case MODULE -> switch (proposition.typeCommande()) {
                case MARCHE -> seuils.moduleMarches();
                case BON_DE_COMMANDE -> seuils.moduleBiensEtServices();
                case CONTRAT_DROIT_COMMUN -> seuils.moduleContrats();
            };
        };
        return proposition.montantPropose().depasse(seuil);
    }

    /** Montant réservé sur la ligne budgétaire : principal et somme à valoir. */
    public Montant montantAEngager() {
        return proposition.montantPropose().plus(sommeAValoir);
    }

    /**
     * Contrôles que la machine peut faire seule ; l'appréciation de la régularité au regard des
     * textes reste celle du comptable, qui ajoute ses propres observations.
     */
    public List<Observation> controlesAutomatiques(Montant disponibleSurLaLigne, boolean ordonnateurAccredite,
            Reglementation reglementation) {
        List<Observation> observations = new ArrayList<>();
        if (!ordonnateurAccredite) {
            observations.add(new Observation("ORDONNATEUR_NON_ACCREDITE", "Décret royal n° 330-66 (RGCP), art. 5",
                    "L'ordonnateur " + ordonnateur + " n'est pas accrédité auprès du comptable pour le service "
                            + serviceOrdonnateur + "."));
        }
        Montant total = proposition.totalDesLignes();
        if (total.compareTo(proposition.montantPropose()) != 0) {
            observations.add(new Observation("CALCUL_INEXACT", CONTROLE_ART_3,
                    "Le total des lignes (" + total + ") ne correspond pas au montant proposé ("
                            + proposition.montantPropose() + ")."));
        }
        if (montantAEngager().depasse(disponibleSurLaLigne)) {
            observations.add(new Observation("CREDITS_INSUFFISANTS", CONTROLE_ART_3,
                    "Crédits insuffisants sur la ligne " + imputation + " : " + disponibleSurLaLigne
                            + " disponibles, " + montantAEngager() + " à engager (somme à valoir comprise)."));
        }
        if (exigeControleDeRegularite(reglementation) && proposition.referencePiece() == null) {
            observations.add(new Observation("PIECE_ENGAGEMENT_MANQUANTE", CONTROLE_ART_3,
                    "La pièce qui fonde l'engagement (marché, bon de commande ou contrat) doit être référencée."));
        }
        return List.copyOf(observations);
    }

    /**
     * Décision du comptable : visa, suspension avec renvoi pour régularisation, ou refus motivé.
     * Le visa est impossible tant qu'une anomalie automatique subsiste.
     */
    public void decider(TypeDecision type, String comptable, LocalDate date, List<String> observationsDuComptable,
            List<Observation> anomaliesAutomatiques) {
        if (statut != StatutEngagement.SOUMIS) {
            throw new RegleMetierException("ENGAGEMENT_DEJA_TRAITE", CONTROLE_ART_5,
                    "L'engagement n'est pas en attente de visa (statut " + statut + ").");
        }
        if (ordonnateur.equals(comptable)) {
            throw new RegleMetierException("INCOMPATIBILITE_FONCTIONS", RGCP_4,
                    "Le comptable ne peut pas viser un engagement qu'il a lui-même proposé.");
        }
        List<String> motifs = Stream.concat(
                        anomaliesAutomatiques.stream().map(Observation::libelle),
                        observationsDuComptable.stream().filter(o -> o != null && !o.isBlank()).map(String::strip))
                .toList();
        if (type == TypeDecision.VISA && !anomaliesAutomatiques.isEmpty()) {
            throw new RegleMetierException("VISA_IMPOSSIBLE", CONTROLE_ART_3,
                    "Visa impossible tant que ces anomalies subsistent : "
                            + anomaliesAutomatiques.stream().map(Observation::message).collect(Collectors.joining(" ")));
        }
        if (type != TypeDecision.VISA && motifs.isEmpty()) {
            throw new RegleMetierException("DECISION_NON_MOTIVEE", CONTROLE_ART_5,
                    "Une suspension ou un refus de visa doit être motivé.");
        }
        decisions.add(new DecisionEngagement(type, date, comptable, type == TypeDecision.VISA ? List.of() : motifs));
        statut = switch (type) {
            case VISA -> StatutEngagement.VISE;
            case SUSPENSION -> StatutEngagement.SUSPENDU;
            case REFUS -> StatutEngagement.REFUSE;
        };
    }

    /** L'ordonnateur corrige une proposition suspendue et la représente au comptable (art. 5). */
    public void regulariser(String acteur, Proposition corrigee, LocalDate date, Reglementation reglementation,
            CalendrierOuvrable calendrier) {
        if (statut != StatutEngagement.SUSPENDU) {
            throw new RegleMetierException("ENGAGEMENT_NON_SUSPENDU", CONTROLE_ART_5,
                    "Seul un engagement suspendu peut être régularisé (statut " + statut + ").");
        }
        verifierOrdonnateur(acteur);
        if (!corrigee.fournisseurIdentifiant().equals(proposition.fournisseurIdentifiant())) {
            throw new RegleMetierException("FOURNISSEUR_MODIFIE", CONTROLE_ART_5,
                    "La régularisation ne peut pas changer de fournisseur : soumettez un nouvel engagement.");
        }
        deposer(corrigee, date, reglementation, calendrier);
    }

    /**
     * Pour les marchés, faute de réponse dans le délai, le comptable est tenu de viser (art. 6,
     * alinéa 2).
     */
    public boolean visaTacitementDu(LocalDate aujourdhui) {
        return statut == StatutEngagement.SOUMIS
                && proposition.typeCommande() == TypeCommande.MARCHE
                && aujourdhui.isAfter(echeanceVisa);
    }

    public boolean enRetardDeVisa(LocalDate aujourdhui) {
        return statut == StatutEngagement.SOUMIS && aujourdhui.isAfter(echeanceVisa);
    }

    /**
     * Liquidation d'une facture sur cet engagement : elle exige le visa préalable et ne peut pas
     * dépasser le montant engagé en principal (RGCP, art. 33 et 34).
     */
    public void liquider(Montant montant) {
        if (statut != StatutEngagement.VISE) {
            throw new RegleMetierException("ENGAGEMENT_NON_VISE", "Décret n° 2-07-1235, art. 8",
                    "Impossible de liquider une dépense dont l'engagement n'est pas visé.");
        }
        if (montantLiquide.plus(montant).depasse(proposition.montantPropose())) {
            throw new RegleMetierException("LIQUIDATION_DEPASSE_ENGAGEMENT", "Décret royal n° 330-66 (RGCP), art. 33",
                    "La liquidation dépasserait le montant engagé : reste " + resteALiquider() + ", demandé " + montant + ".");
        }
        montantLiquide = montantLiquide.plus(montant);
    }

    public Montant resteALiquider() {
        return proposition.montantPropose().moins(montantLiquide);
    }

    private void verifierOrdonnateur(String acteur) {
        if (!ordonnateur.equals(acteur)) {
            throw new RegleMetierException("ACTE_RESERVE_A_L_ORDONNATEUR", "Décret royal n° 330-66 (RGCP), art. 33",
                    "Seul l'ordonnateur qui a proposé l'engagement peut le régulariser.");
        }
    }

    public Long id() {
        return id;
    }

    public int exercice() {
        return exercice;
    }

    public String serviceOrdonnateur() {
        return serviceOrdonnateur;
    }

    public RegimeControle regime() {
        return regime;
    }

    public long ligneBudgetaireId() {
        return ligneBudgetaireId;
    }

    public String imputation() {
        return imputation;
    }

    public String ordonnateur() {
        return ordonnateur;
    }

    public Proposition proposition() {
        return proposition;
    }

    public Montant sommeAValoir() {
        return sommeAValoir;
    }

    public Montant montantLiquide() {
        return montantLiquide;
    }

    public StatutEngagement statut() {
        return statut;
    }

    public LocalDate dateDepot() {
        return dateDepot;
    }

    public LocalDate echeanceVisa() {
        return echeanceVisa;
    }

    public List<DecisionEngagement> decisions() {
        return List.copyOf(decisions);
    }

    public int version() {
        return version;
    }
}
