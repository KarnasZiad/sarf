package ma.sarf.documents;

import ma.sarf.commun.domaine.EvenementMetier;

record EvenementDocument(TypeObjet type, long id, String acteur, String details) implements EvenementMetier {

    @Override
    public String action() {
        return "PIECE_JOINTE";
    }

    @Override
    public String typeObjet() {
        return type.name();
    }

    @Override
    public String idObjet() {
        return String.valueOf(id);
    }
}
