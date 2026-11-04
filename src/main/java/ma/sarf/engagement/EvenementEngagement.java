package ma.sarf.engagement;

import ma.sarf.commun.domaine.EvenementMetier;

public record EvenementEngagement(long id, String action, String acteur, String details) implements EvenementMetier {

    @Override
    public String typeObjet() {
        return "ENGAGEMENT";
    }

    @Override
    public String idObjet() {
        return String.valueOf(id);
    }
}
