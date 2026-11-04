package ma.sarf.depense.domaine;

import java.time.LocalDate;

/**
 * Ordre écrit par lequel l'ordonnateur requiert le comptable de payer malgré la suspension,
 * sous sa propre responsabilité (décret n° 2-07-1235, art. 29).
 */
public record Requisition(LocalDate date, String ordonnateur, String motif) {
}
