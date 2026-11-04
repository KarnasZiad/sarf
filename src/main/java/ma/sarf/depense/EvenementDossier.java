package ma.sarf.depense;

import ma.sarf.commun.domaine.EvenementMetier;

public record EvenementDossier(long id, String action, String acteur, String details) implements EvenementMetier {

    @Override
    public String typeObjet() {
        return "DOSSIER_PAIEMENT";
    }

    @Override
    public String idObjet() {
        return String.valueOf(id);
    }
}
