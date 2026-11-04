package ma.sarf.commun.domaine;

public class ObjetIntrouvableException extends RuntimeException {

    public ObjetIntrouvableException(String typeObjet, Object identifiant) {
        super(typeObjet + " introuvable : " + identifiant);
    }
}
