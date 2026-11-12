package ma.sarf.engagement.persistance;

import java.util.List;
import java.util.Optional;
import ma.sarf.engagement.AccreditationVue;
import ma.sarf.engagement.domaine.RegimeControle;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Services ordonnateurs, leur régime de contrôle et les accréditations des ordonnateurs auprès du
 * comptable (RGCP, art. 5).
 */
@Repository
public class ServicesOrdonnateurs {

    private final JdbcClient jdbc;

    ServicesOrdonnateurs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Service(String code, String libelle, RegimeControle regime) {
    }

    public Optional<Service> trouver(String code) {
        return jdbc.sql("SELECT code, libelle, regime FROM services_ordonnateurs WHERE code = :code")
                .param("code", code)
                .query((rs, n) -> new Service(rs.getString("code"), rs.getString("libelle"),
                        RegimeControle.valueOf(rs.getString("regime"))))
                .optional();
    }

    public List<Service> lister() {
        return jdbc.sql("SELECT code, libelle, regime FROM services_ordonnateurs ORDER BY code")
                .query((rs, n) -> new Service(rs.getString("code"), rs.getString("libelle"),
                        RegimeControle.valueOf(rs.getString("regime"))))
                .list();
    }

    public boolean estAccredite(String utilisateur, String serviceCode) {
        return jdbc.sql("SELECT count(*) FROM accreditations WHERE utilisateur = :utilisateur AND service_code = :service")
                .param("utilisateur", utilisateur)
                .param("service", serviceCode)
                .query(Integer.class)
                .single() > 0;
    }

    public List<AccreditationVue> accreditations() {
        return jdbc.sql("SELECT utilisateur, service_code FROM accreditations ORDER BY service_code, utilisateur")
                .query((rs, n) -> new AccreditationVue(rs.getString("utilisateur"), rs.getString("service_code")))
                .list();
    }

    public void accrediter(String utilisateur, String serviceCode) {
        jdbc.sql("""
                INSERT INTO accreditations (utilisateur, service_code) VALUES (:utilisateur, :service)
                ON CONFLICT DO NOTHING
                """)
                .param("utilisateur", utilisateur)
                .param("service", serviceCode)
                .update();
    }
}
