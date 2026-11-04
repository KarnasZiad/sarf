package ma.sarf.engagement.domaine;

/**
 * Régime de contrôle applicable au service ordonnateur (décret n° 2-07-1235, art. 2 et 12).
 */
public enum RegimeControle {
    /** Contrôle d'engagement et de paiement complet (section I). */
    NORMAL,
    /**
     * Contrôle allégé pour les services dotés d'un contrôle interne (section II). L'ordonnateur
     * vérifie lui-même, entre autres, l'inexistence du double paiement (art. 12).
     */
    MODULE
}
