package ma.sarf.commun.domaine;

/**
 * Fait métier publié par un module et consigné dans le journal d'audit.
 */
public interface EvenementMetier {

    /** Ce qui s'est passé, par exemple « ENGAGEMENT_VISE ». */
    String action();

    String typeObjet();

    String idObjet();

    /** Identifiant de la personne qui a agi. */
    String acteur();

    /** Détails lisibles, conservés tels quels dans le journal. */
    String details();
}
