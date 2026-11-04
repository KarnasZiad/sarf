package ma.sarf.depense;

import java.math.BigDecimal;

/** Taux des intérêts moratoires d'un trimestre (décret n° 2-16-344, art. 11). */
public record TauxTrimestriel(int annee, int trimestre, BigDecimal taux, String source) {
}
