package ma.sarf.budget;

import ma.sarf.commun.domaine.EvenementMetier;

public record LigneOuverte(long id, String imputation, String credits, String acteur) implements EvenementMetier {

    @Override
    public String action() {
        return "LIGNE_BUDGETAIRE_OUVERTE";
    }

    @Override
    public String typeObjet() {
        return "LIGNE_BUDGETAIRE";
    }

    @Override
    public String idObjet() {
        return String.valueOf(id);
    }

    @Override
    public String details() {
        return "Imputation " + imputation + ", crédits ouverts " + credits;
    }
}
