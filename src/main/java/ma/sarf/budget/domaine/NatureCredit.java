package ma.sarf.budget.domaine;

/**
 * Nature des crédits : elle détermine leur sort en fin d'année (RGCP, art. 87).
 */
public enum NatureCredit {
    /** Les crédits non consommés tombent en annulation. */
    FONCTIONNEMENT,
    /** Les crédits de paiement disponibles sont reportés sur l'année suivante. */
    INVESTISSEMENT
}
