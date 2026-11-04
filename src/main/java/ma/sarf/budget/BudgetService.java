package ma.sarf.budget;

import java.math.BigDecimal;
import java.util.List;
import ma.sarf.budget.domaine.LigneBudgetaire;
import ma.sarf.budget.domaine.NatureCredit;
import ma.sarf.budget.persistance.LignesBudgetaires;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface publique du module budget : crédits ouverts, réservés par les engagements visés et
 * consommés par les paiements. Seule voie d'accès aux lignes budgétaires pour les autres modules.
 */
@Service
public class BudgetService {

    private final LignesBudgetaires lignes;
    private final ApplicationEventPublisher evenements;

    BudgetService(LignesBudgetaires lignes, ApplicationEventPublisher evenements) {
        this.lignes = lignes;
        this.evenements = evenements;
    }

    @Transactional
    public LigneBudgetaireVue ouvrirLigne(int exercice, String imputation, String libelle, NatureCredit nature,
            BigDecimal credits, String acteur) {
        LigneBudgetaire ligne = LigneBudgetaire.ouvrir(exercice, imputation, libelle, nature, Montant.de(credits));
        long id = lignes.inserer(ligne);
        evenements.publishEvent(new LigneOuverte(id, imputation, ligne.creditsOuverts().toString(), acteur));
        return consulter(id);
    }

    @Transactional(readOnly = true)
    public LigneBudgetaireVue consulter(long id) {
        return LigneBudgetaireVue.de(charger(id));
    }

    @Transactional(readOnly = true)
    public List<LigneBudgetaireVue> lister(int exercice) {
        return lignes.lister(exercice).stream().map(LigneBudgetaireVue::de).toList();
    }

    /**
     * Réserve les crédits d'un engagement visé. La ligne est verrouillée : si deux engagements
     * arrivent en même temps, le second voit le disponible déjà diminué.
     */
    @Transactional
    public void reserverPourEngagement(long ligneId, BigDecimal montant) {
        LigneBudgetaire ligne = lignes.trouverPourMiseAJour(ligneId)
                .orElseThrow(() -> new ObjetIntrouvableException("Ligne budgétaire", ligneId));
        ligne.engager(Montant.de(montant));
        lignes.mettreAJour(ligne);
    }

    @Transactional(readOnly = true)
    public boolean creditsDePaiementDisponibles(long ligneId, BigDecimal montant) {
        return charger(ligneId).creditsDePaiementDisponibles(Montant.de(montant));
    }

    @Transactional
    public void constaterPaiement(long ligneId, BigDecimal montant) {
        LigneBudgetaire ligne = lignes.trouverPourMiseAJour(ligneId)
                .orElseThrow(() -> new ObjetIntrouvableException("Ligne budgétaire", ligneId));
        ligne.payer(Montant.de(montant));
        lignes.mettreAJour(ligne);
    }

    private LigneBudgetaire charger(long id) {
        return lignes.trouver(id).orElseThrow(() -> new ObjetIntrouvableException("Ligne budgétaire", id));
    }
}
