package ma.sarf.documents.persistance;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import ma.sarf.documents.ContenuDocument;
import ma.sarf.documents.DocumentVue;
import ma.sarf.documents.NatureDocument;
import ma.sarf.documents.TypeObjet;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** Stockage des pièces justificatives. Aucune modification ni suppression n'est proposée. */
@Repository
public class Documents {

    private final JdbcClient jdbc;

    Documents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long inserer(TypeObjet type, long idObjet, NatureDocument nature, String nomFichier, String typeContenu,
            String empreinte, byte[] contenu, String deposePar, Instant deposeLe) {
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO documents (type_objet, id_objet, nature, nom_fichier, type_contenu, taille, empreinte,
                    contenu, depose_par, depose_le)
                VALUES (:type, :idObjet, :nature, :nom, :typeContenu, :taille, :empreinte, :contenu, :par, :le)
                """)
                .param("type", type.name())
                .param("idObjet", idObjet)
                .param("nature", nature.name())
                .param("nom", nomFichier)
                .param("typeContenu", typeContenu)
                .param("taille", contenu.length)
                .param("empreinte", empreinte)
                .param("contenu", contenu)
                .param("par", deposePar)
                .param("le", Timestamp.from(deposeLe))
                .update(cle, "id");
        return cle.getKeyAs(Long.class);
    }

    public List<DocumentVue> lister(TypeObjet type, long idObjet) {
        return jdbc.sql("""
                SELECT id, nature, nom_fichier, type_contenu, taille, empreinte, depose_par, depose_le
                  FROM documents WHERE type_objet = :type AND id_objet = :idObjet ORDER BY id
                """)
                .param("type", type.name())
                .param("idObjet", idObjet)
                .query((rs, n) -> new DocumentVue(rs.getLong("id"), NatureDocument.valueOf(rs.getString("nature")),
                        rs.getString("nom_fichier"), rs.getString("type_contenu"), rs.getInt("taille"),
                        rs.getString("empreinte"), rs.getString("depose_par"), rs.getTimestamp("depose_le").toInstant()))
                .list();
    }

    public Optional<ContenuDocument> contenu(TypeObjet type, long idObjet, long id) {
        return jdbc.sql("""
                SELECT nom_fichier, type_contenu, contenu FROM documents
                 WHERE id = :id AND type_objet = :type AND id_objet = :idObjet
                """)
                .param("id", id)
                .param("type", type.name())
                .param("idObjet", idObjet)
                .query((rs, n) -> new ContenuDocument(rs.getString("nom_fichier"), rs.getString("type_contenu"),
                        rs.getBytes("contenu")))
                .optional();
    }
}
