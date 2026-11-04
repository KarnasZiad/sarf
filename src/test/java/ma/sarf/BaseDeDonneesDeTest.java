package ma.sarf;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Vrai PostgreSQL pour les tests d'intégration : un conteneur quand Docker est disponible (CI), sinon
 * un PostgreSQL embarqué (une cinquantaine de Mo de mémoire au lieu d'une machine virtuelle Docker).
 * Démarré une fois pour toute la suite de tests.
 */
final class BaseDeDonneesDeTest {

    private record Connexion(String url, String utilisateur, String motDePasse) {
    }

    private static final Connexion CONNEXION = demarrer();

    private BaseDeDonneesDeTest() {
    }

    static void enregistrer(DynamicPropertyRegistry proprietes) {
        proprietes.add("spring.datasource.url", CONNEXION::url);
        proprietes.add("spring.datasource.username", CONNEXION::utilisateur);
        proprietes.add("spring.datasource.password", CONNEXION::motDePasse);
    }

    private static Connexion demarrer() {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            var conteneur = new PostgreSQLContainer("postgres:18.6-alpine");
            conteneur.start();
            return new Connexion(conteneur.getJdbcUrl(), conteneur.getUsername(), conteneur.getPassword());
        }
        try {
            EmbeddedPostgres embarque = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    embarque.close();
                } catch (IOException ignoree) {
                    // Arrêt de la JVM : rien de plus à faire.
                }
            }));
            return new Connexion(embarque.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
        } catch (IOException exception) {
            throw new UncheckedIOException("Impossible de démarrer PostgreSQL embarqué", exception);
        }
    }
}
