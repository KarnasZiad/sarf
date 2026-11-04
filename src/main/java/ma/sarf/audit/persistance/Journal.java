package ma.sarf.audit.persistance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import ma.sarf.audit.domaine.EntreeJournal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Table en ajout seul : un déclencheur PostgreSQL refuse toute modification ou suppression (voir la
 * migration V1).
 */
@Repository
public class Journal {

    /** Clé du verrou consultatif qui sérialise les écritures dans le journal. */
    private static final long VERROU_JOURNAL = 7_316_173L;

    private final JdbcClient jdbc;

    Journal(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ajoute une entrée chaînée à la dernière. Le verrou de transaction garantit qu'aucune autre
     * écriture ne s'intercale entre la lecture de la dernière empreinte et l'insertion.
     */
    public void ajouter(Instant horodatage, String acteur, String action, String typeObjet, String idObjet,
            String details) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:cle)").param("cle", VERROU_JOURNAL).query(Object.class).single();
        String precedente = jdbc.sql("SELECT empreinte FROM journal_audit ORDER BY id DESC LIMIT 1")
                .query(String.class)
                .optional()
                .orElse(EntreeJournal.ORIGINE);
        String empreinte = EntreeJournal.empreinte(precedente, horodatage, acteur, action, typeObjet, idObjet, details);
        jdbc.sql("""
                INSERT INTO journal_audit (horodatage, acteur, action, type_objet, id_objet, details,
                    empreinte_precedente, empreinte)
                VALUES (:horodatage, :acteur, :action, :type, :idObjet, :details, :precedente, :empreinte)
                """)
                .param("horodatage", Timestamp.from(horodatage))
                .param("acteur", acteur)
                .param("action", action)
                .param("type", typeObjet)
                .param("idObjet", idObjet)
                .param("details", details)
                .param("precedente", precedente)
                .param("empreinte", empreinte)
                .update();
    }

    public List<EntreeJournal> dernieres(int limite) {
        return jdbc.sql("SELECT * FROM journal_audit ORDER BY id DESC LIMIT :limite")
                .param("limite", limite)
                .query(Journal::lire)
                .list();
    }

    public List<EntreeJournal> toutes() {
        return jdbc.sql("SELECT * FROM journal_audit ORDER BY id").query(Journal::lire).list();
    }

    private static EntreeJournal lire(ResultSet rs, int numero) throws SQLException {
        return new EntreeJournal(rs.getLong("id"), rs.getTimestamp("horodatage").toInstant(), rs.getString("acteur"),
                rs.getString("action"), rs.getString("type_objet"), rs.getString("id_objet"), rs.getString("details"),
                rs.getString("empreinte_precedente"), rs.getString("empreinte"));
    }
}
