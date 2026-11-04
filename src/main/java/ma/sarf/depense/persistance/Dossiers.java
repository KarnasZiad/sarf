package ma.sarf.depense.persistance;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import ma.sarf.commun.domaine.ConflitDeVersionException;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.depense.domaine.DecisionComptable;
import ma.sarf.depense.domaine.DossierPaiement;
import ma.sarf.depense.domaine.ModeReglement;
import ma.sarf.depense.domaine.MotifSuspension;
import ma.sarf.depense.domaine.Requisition;
import ma.sarf.depense.domaine.StatutDossier;
import ma.sarf.depense.domaine.SuspensionDelai;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Dossiers {

    private static final String SELECTION = """
            SELECT id, engagement_id, exercice, service_code, ordonnateur, ligne_budgetaire_id, imputation,
                   creancier_nom, creancier_identifiant, creancier_rib, numero_facture, montant_facture,
                   retenue_garantie, date_depot, deposant, date_certification, date_service_fait, montant_liquide,
                   numero_ordonnance, date_ordonnancement, date_transmission, jours_comptable_consommes, statut,
                   requisition_date, requisition_par, requisition_motif, date_paiement, mode_reglement,
                   interets_moratoires, version
              FROM dossiers
            """;

    private final JdbcClient jdbc;

    Dossiers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long inserer(DossierPaiement dossier) {
        var d = dossier.donnees();
        var o = d.origine();
        var cle = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO dossiers (engagement_id, exercice, service_code, ordonnateur, ligne_budgetaire_id, imputation,
                    creancier_nom, creancier_identifiant, creancier_rib, numero_facture, montant_facture, retenue_garantie,
                    date_depot, deposant, montant_liquide, statut, interets_moratoires)
                VALUES (:engagement, :exercice, :service, :ordonnateur, :ligne, :imputation, :creancierNom,
                    :creancierIdentifiant, :rib, :facture, :montantFacture, :retenue, :depot, :deposant, 0, :statut, 0)
                """)
                .param("engagement", o.engagementId())
                .param("exercice", o.exercice())
                .param("service", o.serviceOrdonnateur())
                .param("ordonnateur", o.ordonnateur())
                .param("ligne", o.ligneBudgetaireId())
                .param("imputation", o.imputation())
                .param("creancierNom", o.creancierNom())
                .param("creancierIdentifiant", o.creancierIdentifiant())
                .param("rib", d.creancierRib())
                .param("facture", d.numeroFacture())
                .param("montantFacture", d.montantFacture().valeur())
                .param("retenue", d.retenueGarantie().valeur())
                .param("depot", d.dateDepot())
                .param("deposant", d.deposant())
                .param("statut", d.statut().name())
                .update(cle, "id");
        return cle.getKeyAs(Long.class);
    }

    public boolean factureDejaDeposee(String creancierIdentifiant, String numeroFacture) {
        return jdbc.sql("""
                SELECT count(*) FROM dossiers WHERE creancier_identifiant = :creancier AND numero_facture = :facture
                """)
                .param("creancier", creancierIdentifiant)
                .param("facture", numeroFacture.strip())
                .query(Integer.class)
                .single() > 0;
    }

    public Optional<DossierPaiement> trouver(long id) {
        return jdbc.sql(SELECTION + " WHERE id = :id").param("id", id).query(this::lire).optional();
    }

    public Optional<DossierPaiement> trouverPourMiseAJour(long id) {
        return jdbc.sql(SELECTION + " WHERE id = :id FOR UPDATE").param("id", id).query(this::lire).optional();
    }

    public List<DossierPaiement> lister(StatutDossier statut) {
        if (statut == null) {
            return jdbc.sql(SELECTION + " ORDER BY id DESC").query(this::lire).list();
        }
        return jdbc.sql(SELECTION + " WHERE statut = :statut ORDER BY id DESC")
                .param("statut", statut.name())
                .query(this::lire)
                .list();
    }

    public List<DossierPaiement> duneAnnee(int exercice) {
        return jdbc.sql(SELECTION + " WHERE exercice = :exercice ORDER BY id")
                .param("exercice", exercice)
                .query(this::lire)
                .list();
    }

    public void mettreAJour(DossierPaiement dossier) {
        var d = dossier.donnees();
        var requisition = d.requisition();
        int modifiees = jdbc.sql("""
                UPDATE dossiers
                   SET date_certification = :certification, date_service_fait = :serviceFait,
                       montant_liquide = :liquide, numero_ordonnance = :ordonnance,
                       date_ordonnancement = :ordonnancement, date_transmission = :transmission,
                       jours_comptable_consommes = :consommes, statut = :statut, requisition_date = :reqDate,
                       requisition_par = :reqPar, requisition_motif = :reqMotif, date_paiement = :paiement,
                       mode_reglement = :mode, interets_moratoires = :interets, version = version + 1
                 WHERE id = :id AND version = :version
                """)
                .param("certification", d.dateCertification())
                .param("serviceFait", d.dateServiceFait())
                .param("liquide", d.montantLiquide().valeur())
                .param("ordonnance", d.numeroOrdonnance())
                .param("ordonnancement", d.dateOrdonnancement())
                .param("transmission", d.dateTransmission())
                .param("consommes", d.joursComptableConsommes())
                .param("statut", d.statut().name())
                .param("reqDate", requisition == null ? null : requisition.date())
                .param("reqPar", requisition == null ? null : requisition.ordonnateur())
                .param("reqMotif", requisition == null ? null : requisition.motif())
                .param("paiement", d.datePaiement())
                .param("mode", d.modeReglement() == null ? null : d.modeReglement().name())
                .param("interets", d.interetsMoratoires().valeur())
                .param("id", d.id())
                .param("version", d.version())
                .update();
        if (modifiees == 0) {
            throw new ConflitDeVersionException("Dossier de paiement", d.id());
        }
        jdbc.sql("DELETE FROM dossier_suspensions WHERE dossier_id = :id").param("id", d.id()).update();
        int rang = 0;
        for (SuspensionDelai s : d.suspensions()) {
            jdbc.sql("""
                    INSERT INTO dossier_suspensions (dossier_id, rang, debut, fin, motif)
                    VALUES (:id, :rang, :debut, :fin, :motif)
                    """)
                    .param("id", d.id())
                    .param("rang", rang++)
                    .param("debut", s.debut())
                    .param("fin", s.fin())
                    .param("motif", s.motif())
                    .update();
        }
        jdbc.sql("DELETE FROM dossier_decisions WHERE dossier_id = :id").param("id", d.id()).update();
        rang = 0;
        for (DecisionComptable decision : d.decisions()) {
            jdbc.sql("""
                    INSERT INTO dossier_decisions (dossier_id, rang, type, date_decision, comptable, motifs, note)
                    VALUES (:id, :rang, :type, :date, :comptable, :motifs, :note)
                    """)
                    .param("id", d.id())
                    .param("rang", rang++)
                    .param("type", decision.type().name())
                    .param("date", decision.date())
                    .param("comptable", decision.comptable())
                    .param("motifs", decision.motifs().stream().map(Enum::name).sorted().toArray(String[]::new))
                    .param("note", decision.note())
                    .update();
        }
    }

    private DossierPaiement lire(ResultSet rs, int numero) throws SQLException {
        long id = rs.getLong("id");
        List<SuspensionDelai> suspensions = jdbc.sql("""
                SELECT debut, fin, motif FROM dossier_suspensions WHERE dossier_id = :id ORDER BY rang
                """)
                .param("id", id)
                .query((s, n) -> new SuspensionDelai(s.getObject("debut", LocalDate.class),
                        s.getObject("fin", LocalDate.class), s.getString("motif")))
                .list();
        List<DecisionComptable> decisions = jdbc.sql("""
                SELECT type, date_decision, comptable, motifs, note FROM dossier_decisions
                 WHERE dossier_id = :id ORDER BY rang
                """)
                .param("id", id)
                .query((d, n) -> new DecisionComptable(DecisionComptable.Type.valueOf(d.getString("type")),
                        d.getObject("date_decision", LocalDate.class), d.getString("comptable"),
                        motifs(d.getArray("motifs")), d.getString("note")))
                .list();
        LocalDate dateRequisition = rs.getObject("requisition_date", LocalDate.class);
        Requisition requisition = dateRequisition == null ? null
                : new Requisition(dateRequisition, rs.getString("requisition_par"), rs.getString("requisition_motif"));
        String mode = rs.getString("mode_reglement");
        var origine = new DossierPaiement.Origine(rs.getLong("engagement_id"), rs.getInt("exercice"),
                rs.getString("service_code"), rs.getString("ordonnateur"), rs.getLong("ligne_budgetaire_id"),
                rs.getString("imputation"), rs.getString("creancier_nom"), rs.getString("creancier_identifiant"));
        return DossierPaiement.reconstituer(new DossierPaiement.Donnees(id, origine, rs.getString("numero_facture"),
                montant(rs.getBigDecimal("montant_facture")), montant(rs.getBigDecimal("retenue_garantie")),
                rs.getString("creancier_rib"), rs.getObject("date_depot", LocalDate.class), rs.getString("deposant"),
                rs.getObject("date_certification", LocalDate.class), rs.getObject("date_service_fait", LocalDate.class),
                montant(rs.getBigDecimal("montant_liquide")), rs.getString("numero_ordonnance"),
                rs.getObject("date_ordonnancement", LocalDate.class), rs.getObject("date_transmission", LocalDate.class),
                rs.getInt("jours_comptable_consommes"), StatutDossier.valueOf(rs.getString("statut")), suspensions,
                decisions, requisition, rs.getObject("date_paiement", LocalDate.class),
                mode == null ? null : ModeReglement.valueOf(mode), montant(rs.getBigDecimal("interets_moratoires")),
                rs.getInt("version")));
    }

    private static Montant montant(BigDecimal valeur) {
        return Montant.de(valeur);
    }

    private static Set<MotifSuspension> motifs(Array tableau) throws SQLException {
        if (tableau == null) {
            return Set.of();
        }
        return Arrays.stream((String[]) tableau.getArray())
                .map(MotifSuspension::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MotifSuspension.class)));
    }
}
