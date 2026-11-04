package ma.sarf.depense.domaine;

/**
 * Points du contrôle de validité exercé par le comptable avant le visa pour paiement
 * (décret n° 2-07-1235, art. 8). Trois d'entre eux ne peuvent jamais être levés par une
 * réquisition de l'ordonnateur (art. 30).
 */
public enum MotifSuspension {

    CALCUL_LIQUIDATION_INEXACT("Décret n° 2-07-1235, art. 8", "Calculs de liquidation inexacts", true),
    ABSENCE_VISA_ENGAGEMENT("Décret n° 2-07-1235, art. 8 et 30", "Visa préalable d'engagement absent", false),
    DEFAUT_CARACTERE_LIBERATOIRE("Décret n° 2-07-1235, art. 8 et 30",
            "Règlement non libératoire (créancier ou coordonnées bancaires incorrects)", false),
    SIGNATURE_NON_QUALIFIEE("Décret n° 2-07-1235, art. 8", "Signature d'un ordonnateur non qualifié ou non accrédité", true),
    CREDITS_PAIEMENT_INSUFFISANTS("Décret n° 2-07-1235, art. 8 et 30", "Crédits de paiement indisponibles ou insuffisants", false),
    PIECES_JUSTIFICATIVES_INCOMPLETES("Décret n° 2-07-1235, art. 8",
            "Pièces justificatives incomplètes, dont la certification du service fait", true);

    private final String reference;
    private final String libelle;
    private final boolean requisitionnable;

    MotifSuspension(String reference, String libelle, boolean requisitionnable) {
        this.reference = reference;
        this.libelle = libelle;
        this.requisitionnable = requisitionnable;
    }

    public String reference() {
        return reference;
    }

    public String libelle() {
        return libelle;
    }

    /** Faux pour les trois motifs de l'article 30, qui obligent le comptable à refuser la réquisition. */
    public boolean requisitionnable() {
        return requisitionnable;
    }
}
