package ma.sarf.depense.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Numérotation des ordonnances « par ordonnateur, en série unique et ininterrompue par année
 * budgétaire » (RGCP, art. 36).
 *
 * <p>Une séquence PostgreSQL ne convient pas : elle laisse des trous quand une transaction est
 * annulée. Ici, le compteur est une ligne de table incrémentée dans la transaction de
 * l'ordonnancement : la ligne reste verrouillée jusqu'à la validation, et une annulation remet le
 * compteur à sa valeur précédente. Aucun numéro n'est perdu.
 */
@Repository
public class CompteurOrdonnances {

    private final JdbcClient jdbc;

    CompteurOrdonnances(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String prochainNumero(String serviceOrdonnateur, int exercice) {
        int numero = jdbc.sql("""
                INSERT INTO compteurs_ordonnances (service_code, exercice, dernier) VALUES (:service, :exercice, 1)
                ON CONFLICT (service_code, exercice) DO UPDATE SET dernier = compteurs_ordonnances.dernier + 1
                RETURNING dernier
                """)
                .param("service", serviceOrdonnateur)
                .param("exercice", exercice)
                .query(Integer.class)
                .single();
        return String.format("%s-%d-%06d", serviceOrdonnateur, exercice, numero);
    }
}
