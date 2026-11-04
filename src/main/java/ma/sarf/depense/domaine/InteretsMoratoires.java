package ma.sarf.depense.domaine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.RegleMetierException;

/**
 * Intérêts moratoires dus au titulaire d'une commande publique payée en retard du fait de
 * l'administration (décret n° 2-16-344, art. 8 à 11) :
 *
 * <pre>intérêts = créance × jours × taux / 365</pre>
 *
 * <p>Les intérêts courent du lendemain de l'expiration du délai jusqu'au jour du règlement. Le taux
 * est fixé chaque trimestre ; quand la période de retard couvre plusieurs trimestres, chaque jour
 * est compté au taux de son trimestre (interprétation documentée dans
 * {@code docs/interpretations.md}).
 */
public final class InteretsMoratoires {

    private static final BigDecimal JOURS_PAR_AN = BigDecimal.valueOf(365);
    private static final BigDecimal CENT = BigDecimal.valueOf(100);
    private static final int TRIMESTRES_DE_REPLI = 8;

    /** Taux publiés chaque trimestre par décision du Trésorier général du Royaume (art. 11). */
    @FunctionalInterface
    public interface TableDesTaux {
        Optional<BigDecimal> taux(int annee, int trimestre);
    }

    public record Tranche(int annee, int trimestre, long jours, BigDecimal taux, Montant montant) {
    }

    public record Calcul(Montant total, long jours, List<Tranche> tranches) {

        public static final Calcul AUCUN = new Calcul(Montant.ZERO, 0, List.of());
    }

    private InteretsMoratoires() {
    }

    /**
     * Taux d'un trimestre : taux moyen pondéré des bons du Trésor à 3 mois du trimestre précédent,
     * majoré d'un point et arrondi au dixième supérieur (art. 11).
     */
    public static BigDecimal tauxDepuisBonsDuTresor(BigDecimal tauxMoyenPondere) {
        return tauxMoyenPondere.add(BigDecimal.ONE).setScale(1, RoundingMode.CEILING);
    }

    public static Calcul calculer(Montant creance, LocalDate echeance, LocalDate datePaiement, TableDesTaux table) {
        if (!datePaiement.isAfter(echeance) || creance.estNul()) {
            return Calcul.AUCUN;
        }
        Map<String, long[]> joursParTrimestre = new LinkedHashMap<>();
        for (LocalDate jour = echeance.plusDays(1); !jour.isAfter(datePaiement); jour = jour.plusDays(1)) {
            int trimestre = (jour.getMonthValue() - 1) / 3 + 1;
            joursParTrimestre.computeIfAbsent(jour.getYear() + "-" + trimestre, k -> new long[1])[0]++;
        }
        BigDecimal totalExact = BigDecimal.ZERO;
        long totalJours = 0;
        List<Tranche> tranches = new ArrayList<>();
        for (var entree : joursParTrimestre.entrySet()) {
            String[] cle = entree.getKey().split("-");
            int annee = Integer.parseInt(cle[0]);
            int trimestre = Integer.parseInt(cle[1]);
            long jours = entree.getValue()[0];
            BigDecimal taux = tauxApplicable(table, annee, trimestre);
            BigDecimal montant = creance.valeur()
                    .multiply(BigDecimal.valueOf(jours))
                    .multiply(taux)
                    .divide(CENT.multiply(JOURS_PAR_AN), 10, RoundingMode.HALF_UP);
            totalExact = totalExact.add(montant);
            totalJours += jours;
            tranches.add(new Tranche(annee, trimestre, jours, taux, Montant.de(montant)));
        }
        return new Calcul(Montant.de(totalExact), totalJours, List.copyOf(tranches));
    }

    /**
     * Faute de taux publié pour un trimestre, le taux du trimestre précédent est retenu, par
     * analogie avec l'article 11, alinéa 2.
     */
    private static BigDecimal tauxApplicable(TableDesTaux table, int annee, int trimestre) {
        int a = annee;
        int t = trimestre;
        for (int i = 0; i <= TRIMESTRES_DE_REPLI; i++) {
            Optional<BigDecimal> taux = table.taux(a, t);
            if (taux.isPresent()) {
                return taux.get();
            }
            t--;
            if (t == 0) {
                t = 4;
                a--;
            }
        }
        throw new RegleMetierException("TAUX_INCONNU", "Décret n° 2-16-344, art. 11",
                "Aucun taux d'intérêts moratoires n'est enregistré pour le trimestre " + trimestre + " de " + annee
                        + " ni pour les trimestres précédents.");
    }
}
