package ma.sarf.commun.domaine;

/**
 * Deux personnes ont modifié le même objet en même temps : la seconde modification est refusée
 * (verrouillage optimiste) et doit être rejouée sur la version à jour.
 */
public class ConflitDeVersionException extends RuntimeException {

    public ConflitDeVersionException(String typeObjet, Object identifiant) {
        super(typeObjet + " " + identifiant + " a été modifié entre-temps : rechargez-le puis recommencez.");
    }
}
