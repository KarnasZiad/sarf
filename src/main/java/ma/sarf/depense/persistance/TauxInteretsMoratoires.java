package ma.sarf.depense.persistance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import ma.sarf.depense.TauxTrimestriel;
import ma.sarf.depense.domaine.InteretsMoratoires;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Taux trimestriels des intérêts moratoires, saisis d'après la décision du Trésorier général du
 * Royaume publiée sur le portail des marchés publics (décret n° 2-16-344, art. 11).
 */
@Repository
public class TauxInteretsMoratoires implements InteretsMoratoires.TableDesTaux {

    private final JdbcClient jdbc;

    TauxInteretsMoratoires(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BigDecimal> taux(int annee, int trimestre) {
        return jdbc.sql("SELECT taux FROM taux_interets_moratoires WHERE annee = :annee AND trimestre = :trimestre")
                .param("annee", annee)
                .param("trimestre", trimestre)
                .query(BigDecimal.class)
                .optional();
    }

    public List<TauxTrimestriel> lister() {
        return jdbc.sql("SELECT annee, trimestre, taux, source FROM taux_interets_moratoires ORDER BY annee, trimestre")
                .query((rs, n) -> new TauxTrimestriel(rs.getInt("annee"), rs.getInt("trimestre"),
                        rs.getBigDecimal("taux"), rs.getString("source")))
                .list();
    }

    public void enregistrer(TauxTrimestriel taux) {
        jdbc.sql("""
                INSERT INTO taux_interets_moratoires (annee, trimestre, taux, source)
                VALUES (:annee, :trimestre, :taux, :source)
                ON CONFLICT (annee, trimestre) DO UPDATE SET taux = EXCLUDED.taux, source = EXCLUDED.source
                """)
                .param("annee", taux.annee())
                .param("trimestre", taux.trimestre())
                .param("taux", taux.taux())
                .param("source", taux.source())
                .update();
    }
}
