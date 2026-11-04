package ma.sarf.budget;

import java.math.BigDecimal;
import ma.sarf.budget.domaine.LigneBudgetaire;

public record LigneBudgetaireVue(
        long id,
        int exercice,
        String imputation,
        String libelle,
        String nature,
        BigDecimal creditsOuverts,
        BigDecimal montantEngage,
        BigDecimal montantPaye,
        BigDecimal disponibleAEngager) {

    static LigneBudgetaireVue de(LigneBudgetaire ligne) {
        return new LigneBudgetaireVue(ligne.id(), ligne.exercice(), ligne.imputation(), ligne.libelle(),
                ligne.nature().name(), ligne.creditsOuverts().valeur(), ligne.montantEngage().valeur(),
                ligne.montantPaye().valeur(), ligne.disponibleAEngager().valeur());
    }
}
