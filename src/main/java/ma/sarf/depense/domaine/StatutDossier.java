package ma.sarf.depense.domaine;

public enum StatutDossier {
    /** Facture déposée par le titulaire de la commande. */
    DEPOSE,
    /** Service fait certifié par l'ordonnateur. */
    SERVICE_FAIT,
    /** Montant de la dette arrêté (RGCP, art. 34). */
    LIQUIDE,
    /** Ordonnance émise et transmise au comptable (RGCP, art. 35 et 86). */
    ORDONNANCE,
    /** Visa de paiement suspendu, ordonnance renvoyée pour régularisation (décret n° 2-07-1235, art. 8). */
    SUSPENDU,
    /** L'ordonnateur a requis le paiement par écrit, sous sa responsabilité (art. 29). */
    REQUISITIONNE,
    PAYE
}
