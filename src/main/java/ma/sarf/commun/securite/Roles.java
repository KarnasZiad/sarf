package ma.sarf.commun.securite;

/**
 * Rôles portés par le jeton Keycloak (claim « roles »), exposés comme autorités {@code ROLE_*}.
 */
public final class Roles {

    /** Gère le référentiel : lignes budgétaires, taux des intérêts moratoires, jours fériés. */
    public static final String ADMINISTRATEUR = "ADMINISTRATEUR";
    /** Engage, constate le service fait, liquide et ordonnance (RGCP, art. 3). */
    public static final String ORDONNATEUR = "ORDONNATEUR";
    /** Vise les engagements et les paiements, puis règle (comptable public de la TGR). */
    public static final String COMPTABLE = "COMPTABLE";
    /** Titulaire de la commande publique : dépose ses factures et suit leur paiement. */
    public static final String FOURNISSEUR = "FOURNISSEUR";
    /** Consulte tout, ne modifie rien. */
    public static final String AUDITEUR = "AUDITEUR";

    private Roles() {
    }
}
