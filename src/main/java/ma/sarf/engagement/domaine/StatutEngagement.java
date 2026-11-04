package ma.sarf.engagement.domaine;

public enum StatutEngagement {
    /** Proposition déposée, en attente du visa du comptable. */
    SOUMIS,
    VISE,
    /** Renvoyé à l'ordonnateur pour régularisation (art. 5). */
    SUSPENDU,
    /** Refus de visa motivé (art. 5). La procédure de passer outre (art. 28) est hors périmètre. */
    REFUSE
}
