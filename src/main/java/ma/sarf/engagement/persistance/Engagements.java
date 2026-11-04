package ma.sarf.engagement.persistance;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import ma.sarf.commun.domaine.ConflitDeVersionException;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.engagement.domaine.DecisionEngagement;
import ma.sarf.engagement.domaine.Engagement;
import ma.sarf.engagement.domaine.LigneDetail;
import ma.sarf.engagement.domaine.Proposition;
import ma.sarf.engagement.domaine.RegimeControle;
import ma.sarf.engagement.domaine.StatutEngagement;
import ma.sarf.engagement.domaine.TypeCommande;
import ma.sarf.engagement.domaine.TypeDecision;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Engagements {

    private static final String SELECTION = """
            SELECT id, exercice, service_code, regime, ligne_budgetaire_id, imputation, ordonnateur, objet,
                   type_commande, reference_piece, fournisseur_nom, fournisseur_identifiant, montant_propose,
                   somme_a_valoir, montant_liquide, statut, date_depot, echeance_visa, version
              FROM engagements
            """;

    private final JdbcClient jdbc;

    Engagements(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long inserer(Engagement e) {
        var cle = new GeneratedKeyHolder();
        var p = e.proposition();
        jdbc.sql("""
                INSERT INTO engagements (exercice, service_code, regime, ligne_budgetaire_id, imputation, ordonnateur,
                    objet, type_commande, reference_piece, fournisseur_nom, fournisseur_identifiant, montant_propose,
                    somme_a_valoir, montant_liquide, statut, date_depot, echeance_visa)
                VALUES (:exercice, :service, :regime, :ligne, :imputation, :ordonnateur, :objet, :type, :reference,
                    :fournisseurNom, :fournisseurIdentifiant, :montant, :sommeAValoir, :liquide, :statut, :depot,
                    :echeance)
                """)
                .param("exercice", e.exercice())
                .param("service", e.serviceOrdonnateur())
                .param("regime", e.regime().name())
                .param("ligne", e.ligneBudgetaireId())
                .param("imputation", e.imputation())
                .param("ordonnateur", e.ordonnateur())
                .param("objet", p.objet())
                .param("type", p.typeCommande().name())
                .param("reference", p.referencePiece())
                .param("fournisseurNom", p.fournisseurNom())
                .param("fournisseurIdentifiant", p.fournisseurIdentifiant())
                .param("montant", p.montantPropose().valeur())
                .param("sommeAValoir", e.sommeAValoir().valeur())
                .param("liquide", e.montantLiquide().valeur())
                .param("statut", e.statut().name())
                .param("depot", e.dateDepot())
                .param("echeance", e.echeanceVisa())
                .update(cle, "id");
        long id = cle.getKeyAs(Long.class);
        ecrireLignes(id, p.lignes());
        return id;
    }

    public Optional<Engagement> trouver(long id) {
        return jdbc.sql(SELECTION + " WHERE id = :id").param("id", id).query(this::lire).optional();
    }

    /** Verrouille l'engagement : deux liquidations ne peuvent pas dépasser ensemble le montant engagé. */
    public Optional<Engagement> trouverPourMiseAJour(long id) {
        return jdbc.sql(SELECTION + " WHERE id = :id FOR UPDATE").param("id", id).query(this::lire).optional();
    }

    public List<Engagement> lister(StatutEngagement statut) {
        if (statut == null) {
            return jdbc.sql(SELECTION + " ORDER BY id DESC").query(this::lire).list();
        }
        return jdbc.sql(SELECTION + " WHERE statut = :statut ORDER BY id DESC")
                .param("statut", statut.name())
                .query(this::lire)
                .list();
    }

    public void mettreAJour(Engagement e) {
        var p = e.proposition();
        int modifiees = jdbc.sql("""
                UPDATE engagements
                   SET objet = :objet, type_commande = :type, reference_piece = :reference,
                       fournisseur_nom = :fournisseurNom, montant_propose = :montant, somme_a_valoir = :sommeAValoir,
                       montant_liquide = :liquide, statut = :statut, date_depot = :depot, echeance_visa = :echeance,
                       version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .param("objet", p.objet())
                .param("type", p.typeCommande().name())
                .param("reference", p.referencePiece())
                .param("fournisseurNom", p.fournisseurNom())
                .param("montant", p.montantPropose().valeur())
                .param("sommeAValoir", e.sommeAValoir().valeur())
                .param("liquide", e.montantLiquide().valeur())
                .param("statut", e.statut().name())
                .param("depot", e.dateDepot())
                .param("echeance", e.echeanceVisa())
                .param("id", e.id())
                .param("version", e.version())
                .update();
        if (modifiees == 0) {
            throw new ConflitDeVersionException("Engagement", e.id());
        }
        jdbc.sql("DELETE FROM engagement_lignes WHERE engagement_id = :id").param("id", e.id()).update();
        ecrireLignes(e.id(), p.lignes());
        jdbc.sql("DELETE FROM engagement_decisions WHERE engagement_id = :id").param("id", e.id()).update();
        int rang = 0;
        for (DecisionEngagement d : e.decisions()) {
            jdbc.sql("""
                    INSERT INTO engagement_decisions (engagement_id, rang, type, date_decision, comptable, observations)
                    VALUES (:id, :rang, :type, :date, :comptable, :observations)
                    """)
                    .param("id", e.id())
                    .param("rang", rang++)
                    .param("type", d.type().name())
                    .param("date", d.date())
                    .param("comptable", d.comptable())
                    .param("observations", d.observations().toArray(String[]::new))
                    .update();
        }
    }

    private void ecrireLignes(long engagementId, List<LigneDetail> lignes) {
        int rang = 0;
        for (LigneDetail ligne : lignes) {
            jdbc.sql("""
                    INSERT INTO engagement_lignes (engagement_id, rang, designation, quantite, prix_unitaire)
                    VALUES (:id, :rang, :designation, :quantite, :prix)
                    """)
                    .param("id", engagementId)
                    .param("rang", rang++)
                    .param("designation", ligne.designation())
                    .param("quantite", ligne.quantite())
                    .param("prix", ligne.prixUnitaire().valeur())
                    .update();
        }
    }

    private Engagement lire(ResultSet rs, int numero) throws SQLException {
        long id = rs.getLong("id");
        List<LigneDetail> lignes = jdbc.sql("""
                SELECT designation, quantite, prix_unitaire FROM engagement_lignes
                 WHERE engagement_id = :id ORDER BY rang
                """)
                .param("id", id)
                .query((l, n) -> new LigneDetail(l.getString("designation"), l.getBigDecimal("quantite"),
                        Montant.de(l.getBigDecimal("prix_unitaire"))))
                .list();
        List<DecisionEngagement> decisions = jdbc.sql("""
                SELECT type, date_decision, comptable, observations FROM engagement_decisions
                 WHERE engagement_id = :id ORDER BY rang
                """)
                .param("id", id)
                .query((d, n) -> new DecisionEngagement(TypeDecision.valueOf(d.getString("type")),
                        d.getObject("date_decision", java.time.LocalDate.class), d.getString("comptable"),
                        texte(d.getArray("observations"))))
                .list();
        var proposition = new Proposition(rs.getString("objet"), TypeCommande.valueOf(rs.getString("type_commande")),
                rs.getString("reference_piece"), rs.getString("fournisseur_nom"), rs.getString("fournisseur_identifiant"),
                lignes, Montant.de(rs.getBigDecimal("montant_propose")));
        return Engagement.reconstituer(id, rs.getInt("exercice"), rs.getString("service_code"),
                RegimeControle.valueOf(rs.getString("regime")), rs.getLong("ligne_budgetaire_id"),
                rs.getString("imputation"), rs.getString("ordonnateur"), proposition,
                Montant.de(rs.getBigDecimal("somme_a_valoir")), Montant.de(rs.getBigDecimal("montant_liquide")),
                StatutEngagement.valueOf(rs.getString("statut")), rs.getObject("date_depot", java.time.LocalDate.class),
                rs.getObject("echeance_visa", java.time.LocalDate.class), decisions, rs.getInt("version"));
    }

    private static List<String> texte(Array tableau) throws SQLException {
        return tableau == null ? List.of() : Arrays.asList((String[]) tableau.getArray());
    }
}
