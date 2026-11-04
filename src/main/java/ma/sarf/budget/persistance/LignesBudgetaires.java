package ma.sarf.budget.persistance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import ma.sarf.budget.domaine.LigneBudgetaire;
import ma.sarf.budget.domaine.NatureCredit;
import ma.sarf.commun.domaine.ConflitDeVersionException;
import ma.sarf.commun.domaine.Montant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Accès SQL explicite : le verrou {@code FOR UPDATE} et le contrôle de version sont visibles
 * dans les requêtes, pas cachés par un ORM.
 */
@Repository
public class LignesBudgetaires {

    private static final String COLONNES =
            "id, exercice, imputation, libelle, nature, credits_ouverts, montant_engage, montant_paye, version";

    private final JdbcClient jdbc;

    LignesBudgetaires(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long inserer(LigneBudgetaire ligne) {
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO lignes_budgetaires (exercice, imputation, libelle, nature, credits_ouverts)
                VALUES (:exercice, :imputation, :libelle, :nature, :credits)
                """)
                .param("exercice", ligne.exercice())
                .param("imputation", ligne.imputation())
                .param("libelle", ligne.libelle())
                .param("nature", ligne.nature().name())
                .param("credits", ligne.creditsOuverts().valeur())
                .update(cle, "id");
        return cle.getKeyAs(Long.class);
    }

    public Optional<LigneBudgetaire> trouver(long id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM lignes_budgetaires WHERE id = :id")
                .param("id", id)
                .query(LignesBudgetaires::lire)
                .optional();
    }

    /** Verrouille la ligne jusqu'à la fin de la transaction : deux engagements ne se croisent pas. */
    public Optional<LigneBudgetaire> trouverPourMiseAJour(long id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM lignes_budgetaires WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(LignesBudgetaires::lire)
                .optional();
    }

    public List<LigneBudgetaire> lister(int exercice) {
        return jdbc.sql("SELECT " + COLONNES + " FROM lignes_budgetaires WHERE exercice = :exercice ORDER BY imputation")
                .param("exercice", exercice)
                .query(LignesBudgetaires::lire)
                .list();
    }

    public void mettreAJour(LigneBudgetaire ligne) {
        int lignesModifiees = jdbc.sql("""
                UPDATE lignes_budgetaires
                   SET montant_engage = :engage, montant_paye = :paye, version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .param("engage", ligne.montantEngage().valeur())
                .param("paye", ligne.montantPaye().valeur())
                .param("id", ligne.id())
                .param("version", ligne.version())
                .update();
        if (lignesModifiees == 0) {
            throw new ConflitDeVersionException("Ligne budgétaire", ligne.id());
        }
    }

    private static LigneBudgetaire lire(ResultSet rs, int numero) throws SQLException {
        return LigneBudgetaire.reconstituer(
                rs.getLong("id"),
                rs.getInt("exercice"),
                rs.getString("imputation"),
                rs.getString("libelle"),
                NatureCredit.valueOf(rs.getString("nature")),
                montant(rs, "credits_ouverts"),
                montant(rs, "montant_engage"),
                montant(rs, "montant_paye"),
                rs.getInt("version"));
    }

    private static Montant montant(ResultSet rs, String colonne) throws SQLException {
        BigDecimal valeur = rs.getBigDecimal(colonne);
        return Montant.de(valeur);
    }
}
