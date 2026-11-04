package ma.sarf.commun.config;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import ma.sarf.commun.domaine.CalendrierOuvrable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fournit le calendrier des jours ouvrables à partir de la table des jours fériés, tenue par
 * l'administrateur (les fêtes religieuses changent de date chaque année).
 */
@Component
public class JoursFeries {

    public record JourFerie(LocalDate jour, String libelle) {
    }

    private final JdbcClient jdbc;

    JoursFeries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CalendrierOuvrable calendrier() {
        var jours = jdbc.sql("SELECT jour FROM jours_feries")
                .query(LocalDate.class)
                .list();
        return new CalendrierOuvrable(new HashSet<>(jours));
    }

    public List<JourFerie> lister(int annee) {
        return jdbc.sql("""
                SELECT jour, libelle FROM jours_feries
                 WHERE jour >= make_date(:annee, 1, 1) AND jour < make_date(:annee + 1, 1, 1) ORDER BY jour
                """)
                .param("annee", annee)
                .query((rs, n) -> new JourFerie(rs.getObject("jour", LocalDate.class), rs.getString("libelle")))
                .list();
    }

    public void enregistrer(JourFerie jourFerie) {
        jdbc.sql("""
                INSERT INTO jours_feries (jour, libelle) VALUES (:jour, :libelle)
                ON CONFLICT (jour) DO UPDATE SET libelle = EXCLUDED.libelle
                """)
                .param("jour", jourFerie.jour())
                .param("libelle", jourFerie.libelle())
                .update();
    }
}
