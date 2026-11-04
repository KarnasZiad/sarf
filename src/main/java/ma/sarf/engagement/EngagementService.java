package ma.sarf.engagement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import ma.sarf.budget.BudgetService;
import ma.sarf.budget.LigneBudgetaireVue;
import ma.sarf.commun.config.JoursFeries;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.commun.domaine.Observation;
import ma.sarf.commun.domaine.Reglementation;
import ma.sarf.commun.domaine.RegleMetierException;
import ma.sarf.engagement.domaine.Engagement;
import ma.sarf.engagement.domaine.Proposition;
import ma.sarf.engagement.domaine.StatutEngagement;
import ma.sarf.engagement.domaine.TypeDecision;
import ma.sarf.engagement.persistance.Engagements;
import ma.sarf.engagement.persistance.ServicesOrdonnateurs;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface publique du module engagement : proposition par l'ordonnateur, contrôle et visa par le
 * comptable (décret n° 2-07-1235, section I et II).
 */
@Service
public class EngagementService {

    private final Engagements engagements;
    private final ServicesOrdonnateurs services;
    private final BudgetService budget;
    private final Reglementation reglementation;
    private final JoursFeries joursFeries;
    private final Clock horloge;
    private final ApplicationEventPublisher evenements;

    EngagementService(Engagements engagements, ServicesOrdonnateurs services, BudgetService budget,
            Reglementation reglementation, JoursFeries joursFeries, Clock horloge,
            ApplicationEventPublisher evenements) {
        this.engagements = engagements;
        this.services = services;
        this.budget = budget;
        this.reglementation = reglementation;
        this.joursFeries = joursFeries;
        this.horloge = horloge;
        this.evenements = evenements;
    }

    @Transactional
    public EngagementVue soumettre(long ligneBudgetaireId, String serviceCode, Proposition proposition, String acteur) {
        LigneBudgetaireVue ligne = budget.consulter(ligneBudgetaireId);
        var service = services.trouver(serviceCode)
                .orElseThrow(() -> new ObjetIntrouvableException("Service ordonnateur", serviceCode));
        Engagement engagement = Engagement.soumettre(ligne.exercice(), service.code(), service.regime(), ligne.id(),
                ligne.imputation(), acteur, proposition, aujourdhui(), reglementation, joursFeries.calendrier());
        long id = engagements.inserer(engagement);
        evenements.publishEvent(new EvenementEngagement(id, "ENGAGEMENT_SOUMIS", acteur,
                "Proposition de " + proposition.montantPropose() + " sur " + ligne.imputation() + " : "
                        + proposition.objet() + ". Échéance du visa : " + engagement.echeanceVisa()));
        return consulter(id);
    }

    /**
     * Décision du comptable. En cas de visa, les crédits sont réservés sur la ligne, verrouillée
     * pendant la transaction.
     */
    @Transactional
    public EngagementVue decider(long id, TypeDecision type, List<String> observations, String comptable) {
        Engagement engagement = engagements.trouverPourMiseAJour(id)
                .orElseThrow(() -> new ObjetIntrouvableException("Engagement", id));
        List<Observation> anomalies = anomalies(engagement);
        engagement.decider(type, comptable, aujourdhui(), observations, anomalies);
        if (type == TypeDecision.VISA) {
            budget.reserverPourEngagement(engagement.ligneBudgetaireId(), engagement.montantAEngager().valeur());
        }
        engagements.mettreAJour(engagement);
        var decision = engagement.decisions().getLast();
        evenements.publishEvent(new EvenementEngagement(id, "ENGAGEMENT_" + engagement.statut().name(), comptable,
                type == TypeDecision.VISA
                        ? "Visa d'engagement, " + engagement.montantAEngager() + " réservés"
                        : type + " : " + String.join(" | ", decision.observations())));
        return consulter(id);
    }

    @Transactional
    public EngagementVue regulariser(long id, Proposition corrigee, String acteur) {
        Engagement engagement = engagements.trouverPourMiseAJour(id)
                .orElseThrow(() -> new ObjetIntrouvableException("Engagement", id));
        engagement.regulariser(acteur, corrigee, aujourdhui(), reglementation, joursFeries.calendrier());
        engagements.mettreAJour(engagement);
        evenements.publishEvent(new EvenementEngagement(id, "ENGAGEMENT_REGULARISE", acteur,
                "Proposition représentée pour " + corrigee.montantPropose()));
        return consulter(id);
    }

    @Transactional(readOnly = true)
    public EngagementVue consulter(long id) {
        Engagement engagement = engagements.trouver(id).orElseThrow(() -> new ObjetIntrouvableException("Engagement", id));
        return vue(engagement);
    }

    @Transactional(readOnly = true)
    public List<EngagementVue> lister(StatutEngagement statut) {
        return engagements.lister(statut).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public List<EngagementVue> enAttenteDeVisa(int exercice) {
        return engagements.lister(StatutEngagement.SOUMIS).stream()
                .filter(e -> e.exercice() == exercice)
                .map(this::vue)
                .toList();
    }

    @Transactional(readOnly = true)
    public EngagementPourDepense pourDepense(long id) {
        Engagement e = engagements.trouver(id).orElseThrow(() -> new ObjetIntrouvableException("Engagement", id));
        var p = e.proposition();
        return new EngagementPourDepense(e.id(), e.statut() == StatutEngagement.VISE, e.exercice(),
                e.serviceOrdonnateur(), e.regime().name(), e.ordonnateur(), e.ligneBudgetaireId(), e.imputation(),
                p.objet(), p.fournisseurNom(), p.fournisseurIdentifiant(), e.resteALiquider().valeur());
    }

    /** Impute une liquidation sur l'engagement, verrouillé pour éviter deux liquidations concurrentes. */
    @Transactional
    public void enregistrerLiquidation(long id, BigDecimal montant) {
        Engagement engagement = engagements.trouverPourMiseAJour(id)
                .orElseThrow(() -> new ObjetIntrouvableException("Engagement", id));
        engagement.liquider(Montant.de(montant));
        engagements.mettreAJour(engagement);
    }

    @Transactional(readOnly = true)
    public boolean estAccredite(String utilisateur, String serviceCode) {
        return services.estAccredite(utilisateur, serviceCode);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrdonnateurVue> servicesOrdonnateurs() {
        return services.lister().stream()
                .map(s -> new ServiceOrdonnateurVue(s.code(), s.libelle(), s.regime().name()))
                .toList();
    }

    @Transactional
    public void accrediter(String utilisateur, String serviceCode, String acteur) {
        services.trouver(serviceCode).orElseThrow(() -> new ObjetIntrouvableException("Service ordonnateur", serviceCode));
        if (utilisateur == null || utilisateur.isBlank()) {
            throw new RegleMetierException("UTILISATEUR_OBLIGATOIRE", "Décret royal n° 330-66 (RGCP), art. 5",
                    "L'utilisateur à accréditer est obligatoire.");
        }
        services.accrediter(utilisateur.strip(), serviceCode);
        evenements.publishEvent(new EvenementEngagement(0, "ORDONNATEUR_ACCREDITE", acteur,
                utilisateur.strip() + " accrédité pour le service " + serviceCode));
    }

    private EngagementVue vue(Engagement engagement) {
        List<Observation> anomalies = engagement.statut() == StatutEngagement.SOUMIS ? anomalies(engagement) : List.of();
        return EngagementVue.de(engagement, engagement.exigeControleDeRegularite(reglementation), aujourdhui(), anomalies);
    }

    private List<Observation> anomalies(Engagement engagement) {
        LigneBudgetaireVue ligne = budget.consulter(engagement.ligneBudgetaireId());
        boolean accredite = services.estAccredite(engagement.ordonnateur(), engagement.serviceOrdonnateur());
        return engagement.controlesAutomatiques(Montant.de(ligne.disponibleAEngager()), accredite, reglementation);
    }

    private LocalDate aujourdhui() {
        return LocalDate.now(horloge);
    }
}
