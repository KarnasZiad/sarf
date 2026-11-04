package ma.sarf.budget.domaine;

import java.util.Objects;
import java.util.regex.Pattern;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.RegleMetierException;

/**
 * Rubrique budgétaire de la loi de finances sur laquelle les dépenses sont imputées.
 *
 * <p>Simplification assumée : une seule enveloppe de crédits par ligne, sans distinguer crédits
 * d'engagement et crédits de paiement.
 */
public final class LigneBudgetaire {

    private static final Pattern IMPUTATION = Pattern.compile("\\d+(\\.\\d+)*");
    private static final String ART_3 = "Décret n° 2-07-1235, art. 3";

    private final Long id;
    private final int exercice;
    private final String imputation;
    private final String libelle;
    private final NatureCredit nature;
    private final Montant creditsOuverts;
    private Montant montantEngage;
    private Montant montantPaye;
    private final int version;

    private LigneBudgetaire(Long id, int exercice, String imputation, String libelle, NatureCredit nature,
            Montant creditsOuverts, Montant montantEngage, Montant montantPaye, int version) {
        this.id = id;
        this.exercice = exercice;
        this.imputation = imputation;
        this.libelle = libelle;
        this.nature = nature;
        this.creditsOuverts = creditsOuverts;
        this.montantEngage = montantEngage;
        this.montantPaye = montantPaye;
        this.version = version;
    }

    public static LigneBudgetaire ouvrir(int exercice, String imputation, String libelle, NatureCredit nature,
            Montant creditsOuverts) {
        if (exercice < 2000 || exercice > 2100) {
            throw new IllegalArgumentException("Exercice invalide : " + exercice);
        }
        if (imputation == null || !IMPUTATION.matcher(imputation).matches()) {
            throw new IllegalArgumentException("Imputation invalide (chiffres séparés par des points) : " + imputation);
        }
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé est obligatoire");
        }
        return new LigneBudgetaire(null, exercice, imputation, libelle.strip(), Objects.requireNonNull(nature),
                Objects.requireNonNull(creditsOuverts), Montant.ZERO, Montant.ZERO, 0);
    }

    public static LigneBudgetaire reconstituer(long id, int exercice, String imputation, String libelle,
            NatureCredit nature, Montant creditsOuverts, Montant montantEngage, Montant montantPaye, int version) {
        return new LigneBudgetaire(id, exercice, imputation, libelle, nature, creditsOuverts, montantEngage,
                montantPaye, version);
    }

    public Montant disponibleAEngager() {
        return creditsOuverts.moins(montantEngage);
    }

    /** Contrôle budgétaire de l'engagement : disponibilité des crédits. */
    public void engager(Montant montant) {
        if (montant.depasse(disponibleAEngager())) {
            throw new RegleMetierException("CREDITS_INSUFFISANTS", ART_3,
                    "Crédits insuffisants sur la ligne " + imputation + " : " + disponibleAEngager()
                            + " disponibles, " + montant + " demandés.");
        }
        montantEngage = montantEngage.plus(montant);
    }

    public boolean creditsDePaiementDisponibles(Montant montant) {
        return !montantPaye.plus(montant).depasse(montantEngage);
    }

    /** Un paiement ne peut consommer que des crédits déjà engagés sur la ligne. */
    public void payer(Montant montant) {
        if (!creditsDePaiementDisponibles(montant)) {
            throw new RegleMetierException("CREDITS_PAIEMENT_INSUFFISANTS", "Décret n° 2-07-1235, art. 8",
                    "Crédits de paiement insuffisants sur la ligne " + imputation + ".");
        }
        montantPaye = montantPaye.plus(montant);
    }

    /**
     * Sort des crédits non consommés en fin d'année (RGCP, art. 87) : annulés pour le
     * fonctionnement, reportés pour l'investissement.
     */
    public SoldeFinExercice soldeFinExercice() {
        Montant nonConsomme = creditsOuverts.moins(montantPaye);
        return nature == NatureCredit.FONCTIONNEMENT
                ? new SoldeFinExercice(nonConsomme, Montant.ZERO)
                : new SoldeFinExercice(Montant.ZERO, nonConsomme);
    }

    public record SoldeFinExercice(Montant annule, Montant reporte) {
    }

    public Long id() {
        return id;
    }

    public int exercice() {
        return exercice;
    }

    public String imputation() {
        return imputation;
    }

    public String libelle() {
        return libelle;
    }

    public NatureCredit nature() {
        return nature;
    }

    public Montant creditsOuverts() {
        return creditsOuverts;
    }

    public Montant montantEngage() {
        return montantEngage;
    }

    public Montant montantPaye() {
        return montantPaye;
    }

    public int version() {
        return version;
    }
}
