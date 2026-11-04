package ma.sarf.commun.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Montant en dirhams, toujours positif ou nul, arrondi au centime.
 *
 * <p>Jamais de {@code double} pour de l'argent : 0,1 + 0,2 ne vaut pas exactement 0,3 en binaire.
 */
public record Montant(BigDecimal valeur) implements Comparable<Montant> {

    public static final Montant ZERO = new Montant(BigDecimal.ZERO);
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    public Montant {
        Objects.requireNonNull(valeur, "valeur");
        valeur = valeur.setScale(2, RoundingMode.HALF_UP);
        if (valeur.signum() < 0) {
            throw new IllegalArgumentException("Un montant ne peut pas être négatif : " + valeur);
        }
    }

    public static Montant de(String valeur) {
        return new Montant(new BigDecimal(valeur));
    }

    public static Montant de(long dirhams) {
        return new Montant(BigDecimal.valueOf(dirhams));
    }

    public static Montant de(BigDecimal valeur) {
        return new Montant(valeur);
    }

    public Montant plus(Montant autre) {
        return new Montant(valeur.add(autre.valeur));
    }

    /** Soustraction ; échoue si le résultat serait négatif. */
    public Montant moins(Montant autre) {
        return new Montant(valeur.subtract(autre.valeur));
    }

    public Montant fois(BigDecimal facteur) {
        return new Montant(valeur.multiply(facteur));
    }

    /** Applique un pourcentage exprimé en points (1 pour 1 %). */
    public Montant pourcentage(BigDecimal points) {
        return new Montant(valeur.multiply(points).divide(CENT, 2, RoundingMode.HALF_UP));
    }

    public boolean depasse(Montant autre) {
        return compareTo(autre) > 0;
    }

    public boolean estNul() {
        return valeur.signum() == 0;
    }

    @Override
    public int compareTo(Montant autre) {
        return valeur.compareTo(autre.valeur);
    }

    @Override
    public String toString() {
        return valeur.toPlainString() + " DH";
    }
}
