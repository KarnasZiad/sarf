package ma.sarf.engagement.persistance;

import java.util.List;
import java.util.Optional;
import ma.sarf.engagement.CompteFournisseurVue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lien entre un compte de connexion et le fournisseur qu'il représente. */
@Repository
public class ComptesFournisseurs {

    private final JdbcClient jdbc;

    ComptesFournisseurs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CompteFournisseurVue> trouver(String utilisateur) {
        return jdbc.sql("SELECT utilisateur, fournisseur_identifiant, fournisseur_nom FROM comptes_fournisseurs "
                        + "WHERE utilisateur = :utilisateur")
                .param("utilisateur", utilisateur)
                .query(ComptesFournisseurs::lire)
                .optional();
    }

    public List<CompteFournisseurVue> lister() {
        return jdbc.sql("SELECT utilisateur, fournisseur_identifiant, fournisseur_nom FROM comptes_fournisseurs "
                        + "ORDER BY utilisateur")
                .query(ComptesFournisseurs::lire)
                .list();
    }

    public void enregistrer(CompteFournisseurVue compte) {
        jdbc.sql("""
                INSERT INTO comptes_fournisseurs (utilisateur, fournisseur_identifiant, fournisseur_nom)
                VALUES (:utilisateur, :identifiant, :nom)
                ON CONFLICT (utilisateur) DO UPDATE
                   SET fournisseur_identifiant = :identifiant, fournisseur_nom = :nom
                """)
                .param("utilisateur", compte.utilisateur())
                .param("identifiant", compte.fournisseurIdentifiant())
                .param("nom", compte.fournisseurNom())
                .update();
    }

    private static CompteFournisseurVue lire(java.sql.ResultSet rs, int numero) throws java.sql.SQLException {
        return new CompteFournisseurVue(rs.getString("utilisateur"), rs.getString("fournisseur_identifiant"),
                rs.getString("fournisseur_nom"));
    }
}
