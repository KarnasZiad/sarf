package ma.sarf.pilotage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import ma.sarf.budget.BudgetService;
import ma.sarf.budget.LigneBudgetaireVue;
import ma.sarf.depense.DepenseService;
import ma.sarf.depense.DossierVue;
import ma.sarf.engagement.EngagementService;
import ma.sarf.engagement.EngagementVue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau de bord en lecture seule, construit à partir des interfaces publiques des autres modules.
 * Il éclaire la « performance publique » : exécution des crédits et respect des délais de paiement.
 */
@Service
public class PilotageService {

    private static final String PAYE = "PAYE";
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final BudgetService budget;
    private final EngagementService engagements;
    private final DepenseService depense;

    PilotageService(BudgetService budget, EngagementService engagements, DepenseService depense) {
        this.budget = budget;
        this.engagements = engagements;
        this.depense = depense;
    }

    @Transactional(readOnly = true)
    public TableauDeBord tableauDeBord(int exercice) {
        List<LigneBudgetaireVue> lignes = budget.lister(exercice);
        BigDecimal credits = somme(lignes, LigneBudgetaireVue::creditsOuverts);
        BigDecimal engage = somme(lignes, LigneBudgetaireVue::montantEngage);
        BigDecimal paye = somme(lignes, LigneBudgetaireVue::montantPaye);

        List<EngagementVue> enAttente = engagements.enAttenteDeVisa(exercice);

        List<DossierVue> dossiers = depense.duneAnnee(exercice);
        List<DossierVue> payes = dossiers.stream().filter(d -> PAYE.equals(d.statut())).toList();
        List<DossierVue> nonPayes = dossiers.stream().filter(d -> !PAYE.equals(d.statut())).toList();

        BigDecimal delaiMoyen = payes.isEmpty() ? null : BigDecimal.valueOf(payes.stream()
                        .mapToLong(d -> ChronoUnit.DAYS.between(d.dateServiceFait(), d.datePaiement()))
                        .average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP);
        BigDecimal partDansLeDelai = payes.isEmpty() ? null
                : pourcentage(BigDecimal.valueOf(payes.stream().filter(d -> d.delais().joursDeRetard() == 0).count()),
                        BigDecimal.valueOf(payes.size()));

        Map<String, Long> parStatut = dossiers.stream()
                .collect(Collectors.groupingBy(DossierVue::statut, TreeMap::new, Collectors.counting()));

        return new TableauDeBord(exercice,
                lignes.stream().map(l -> new TableauDeBord.Ligne(l.imputation(), l.libelle(), l.nature(),
                        l.creditsOuverts(), l.montantEngage(), l.montantPaye(),
                        pourcentage(l.montantEngage(), l.creditsOuverts()),
                        pourcentage(l.montantPaye(), l.creditsOuverts()))).toList(),
                credits, engage, paye, pourcentage(engage, credits), pourcentage(paye, credits),
                enAttente.size(), enAttente.stream().filter(EngagementVue::enRetardDeVisa).count(),
                parStatut, delaiMoyen, partDansLeDelai,
                nonPayes.stream().filter(d -> d.delais().joursDeRetard() > 0).count(),
                somme(payes, DossierVue::interetsMoratoires),
                somme(nonPayes, DossierVue::interetsMoratoires));
    }

    private static <T> BigDecimal somme(List<T> elements, Function<T, BigDecimal> valeur) {
        return elements.stream().map(valeur).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pourcentage(BigDecimal partie, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return partie.multiply(CENT).divide(total, 1, RoundingMode.HALF_UP);
    }
}
