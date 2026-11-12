package ma.sarf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import ma.sarf.commun.config.HorlogeSimulee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Toute la chaîne sur un vrai PostgreSQL : engagement, visa, facture, service fait, liquidation,
 * ordonnancement, paiement en retard avec intérêts moratoires, puis vérification du journal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ChaineDepenseIT {

    @DynamicPropertySource
    static void baseDeDonnees(DynamicPropertyRegistry proprietes) {
        BaseDeDonneesDeTest.enregistrer(proprietes);
    }

    private static final String RIB = "011780000012345678901234";

    @Autowired
    MockMvc mvc;

    @Autowired
    HorlogeSimulee horloge;

    @Autowired
    JdbcClient jdbc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor jeton(String utilisateur, String role) {
        return jwt().jwt(j -> j.subject(utilisateur).claim("preferred_username", utilisateur))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static final JwtRequestPostProcessor ORDONNATEUR = jeton("ordonnateur.amal", "ORDONNATEUR");
    private static final JwtRequestPostProcessor COMPTABLE = jeton("comptable.karim", "COMPTABLE");
    private static final JwtRequestPostProcessor FOURNISSEUR = jeton("fournisseur.atlas", "FOURNISSEUR");
    private static final JwtRequestPostProcessor AUDITEUR = jeton("auditeur.salma", "AUDITEUR");

    private ResultActions poster(String url, JwtRequestPostProcessor jeton, String corps) throws Exception {
        return mvc.perform(post(url).with(jeton).contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    private static String corps(ResultActions resultat) throws Exception {
        return resultat.andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("De l'engagement au paiement en retard : visa, double facture refusée, intérêts, audit intact")
    void chaineComplete() throws Exception {
        horloge.allerAu(LocalDate.of(2026, 3, 2));
        List<Integer> lignes = JsonPath.read(corps(mvc.perform(get("/api/lignes-budgetaires?exercice=2026")
                .with(ORDONNATEUR))), "$[?(@.imputation == '1.2.1.10.31')].id");
        int ligne = lignes.getFirst();

        // 1. L'ordonnateur propose un engagement de 100 000 DH.
        String engagement = corps(poster("/api/engagements", ORDONNATEUR, """
                {"ligneBudgetaireId": %d, "serviceOrdonnateur": "SRV-DSI", "proposition": {
                  "objet": "Vingt ordinateurs portables", "typeCommande": "BON_DE_COMMANDE",
                  "referencePiece": "BC-2026-014", "fournisseurNom": "Atlas Informatique SARL",
                  "fournisseurIdentifiant": "001234567000089",
                  "lignes": [{"designation": "Ordinateur portable", "quantite": 20, "prixUnitaire": 5000}],
                  "montantPropose": 100000}}
                """.formatted(ligne))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("SOUMIS"))
                .andExpect(jsonPath("$.sommeAValoir").value(1000.00))
                .andExpect(jsonPath("$.echeanceVisa").value("2026-03-09"))
                .andExpect(jsonPath("$.anomalies").isEmpty()));
        int engagementId = JsonPath.read(engagement, "$.id");

        // 2. Le comptable vise : 101 000 DH sont réservés sur la ligne (somme à valoir comprise).
        poster("/api/engagements/" + engagementId + "/decision", COMPTABLE, "{\"type\": \"VISA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("VISE"));
        mvc.perform(get("/api/lignes-budgetaires/" + ligne).with(COMPTABLE))
                .andExpect(jsonPath("$.montantEngage").value(101000.00));

        // 3. Le fournisseur dépose sa facture, puis tente de la déposer une seconde fois.
        String depot = """
                {"engagementId": %d, "numeroFacture": "F-2026-0042", "montantFacture": 100000, "rib": "%s"}
                """.formatted(engagementId, RIB);
        int dossierId = JsonPath.read(corps(poster("/api/dossiers", FOURNISSEUR, depot)
                .andExpect(status().isCreated())), "$.id");
        poster("/api/dossiers", FOURNISSEUR, depot)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DOUBLE_PAIEMENT"));

        // 4. Service fait, liquidation et ordonnancement numéroté sans trou.
        horloge.allerAu(LocalDate.of(2026, 3, 12));
        String dossier = "/api/dossiers/" + dossierId;
        mvc.perform(post(dossier + "/service-fait").with(ORDONNATEUR))
                .andExpect(jsonPath("$.dateServiceFait").value("2026-03-12"));
        poster(dossier + "/liquidation", ORDONNATEUR, "{\"montant\": 100000}")
                .andExpect(jsonPath("$.statut").value("LIQUIDE"));
        mvc.perform(post(dossier + "/ordonnancement").with(ORDONNATEUR))
                .andExpect(jsonPath("$.numeroOrdonnance").value("SRV-DSI-2026-000001"))
                .andExpect(jsonPath("$.delais.echeancePaiement").value("2026-05-11"));

        // 5. Le comptable ne vise que 15 jours après l'échéance : les intérêts courent.
        horloge.allerAu(LocalDate.of(2026, 5, 26));
        mvc.perform(get(dossier).with(COMPTABLE))
                .andExpect(jsonPath("$.delais.joursDeRetard").value(15))
                .andExpect(jsonPath("$.interetsMoratoires").value(139.73));
        poster(dossier + "/controle", COMPTABLE, "{\"decision\": \"VISER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("PAYE"))
                .andExpect(jsonPath("$.interetsMoratoires").value(139.73));

        // 6. Le tableau de bord reflète le paiement et son retard.
        mvc.perform(get("/api/pilotage?exercice=2026").with(AUDITEUR))
                .andExpect(jsonPath("$.delaiMoyenPaiementJours").value(75.0))
                .andExpect(jsonPath("$.partPayesDansLeDelai").value(0.0))
                .andExpect(jsonPath("$.interetsMoratoiresPayes").value(139.73));

        // 7. Le journal d'audit a tout consigné et sa chaîne d'empreintes est intacte.
        mvc.perform(get("/api/audit/integrite").with(AUDITEUR))
                .andExpect(jsonPath("$.intacte").value(true))
                .andExpect(jsonPath("$.entreesVerifiees").value(org.hamcrest.Matchers.greaterThanOrEqualTo(7)));
    }

    @Test
    @DisplayName("Pré-contrôle sans effet, commandes du seul fournisseur concerné, pièce jointe contrôlée")
    void parcoursAvecPiecesJointes() throws Exception {
        horloge.allerAu(LocalDate.of(2026, 3, 2));
        List<Integer> lignes = JsonPath.read(corps(mvc.perform(get("/api/lignes-budgetaires?exercice=2026")
                .with(ORDONNATEUR))), "$[?(@.imputation == '1.2.1.10.45')].id");
        int ligne = lignes.getFirst();
        String proposition = """
                {"ligneBudgetaireId": %d, "serviceOrdonnateur": "SRV-DSI", "proposition": {
                  "objet": "Maintenance annuelle", "typeCommande": "BON_DE_COMMANDE",
                  "fournisseurNom": "Atlas Informatique SARL", "fournisseurIdentifiant": "001234567000089",
                  "lignes": [{"designation": "Forfait", "quantite": 1, "prixUnitaire": 30000}],
                  "montantPropose": 30000}}
                """.formatted(ligne);

        // Le pré-contrôle signale la pièce manquante sans rien réserver ni enregistrer.
        poster("/api/engagements/pre-controle", ORDONNATEUR, proposition)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sommeAValoir").value(300.00))
                .andExpect(jsonPath("$.anomalies[0].code").value("PIECE_ENGAGEMENT_MANQUANTE"));
        mvc.perform(get("/api/lignes-budgetaires/" + ligne).with(ORDONNATEUR))
                .andExpect(jsonPath("$.montantEngage").value(0.0));

        int engagementId = JsonPath.read(corps(poster("/api/engagements", ORDONNATEUR,
                proposition.replace("\"typeCommande\": \"BON_DE_COMMANDE\",",
                        "\"typeCommande\": \"BON_DE_COMMANDE\", \"referencePiece\": \"BC-77\","))
                .andExpect(status().isCreated())), "$.id");
        poster("/api/engagements/" + engagementId + "/decision", COMPTABLE, "{\"type\": \"VISA\"}")
                .andExpect(status().isOk());

        // Le fournisseur retrouve sa commande ; un autre compte ne peut pas la facturer.
        mvc.perform(get("/api/engagements/commandes").with(FOURNISSEUR))
                .andExpect(jsonPath("$[?(@.id == " + engagementId + ")].resteALiquider").value(30000.00));
        String depot = """
                {"engagementId": %d, "numeroFacture": "F-PJ-1", "montantFacture": 30000, "rib": "%s"}
                """.formatted(engagementId, RIB);
        poster("/api/dossiers", jeton("fournisseur.inconnu", "FOURNISSEUR"), depot).andExpect(status().isNotFound());
        int dossierId = JsonPath.read(corps(poster("/api/dossiers", FOURNISSEUR, depot)
                .andExpect(status().isCreated())), "$.id");

        // Seul un vrai PDF est accepté, et le dossier d'un autre fournisseur reste invisible.
        String documents = "/api/dossiers/" + dossierId + "/documents";
        mvc.perform(multipart(documents).file(new MockMultipartFile("fichier", "facture.pdf", "application/pdf",
                        "<html>pas un pdf</html>".getBytes(StandardCharsets.UTF_8)))
                        .param("nature", "FACTURE").with(FOURNISSEUR))
                .andExpect(status().isBadRequest());
        byte[] pdf = "%PDF-1.7 facture".getBytes(StandardCharsets.US_ASCII);
        int documentId = JsonPath.read(mvc.perform(multipart(documents)
                        .file(new MockMultipartFile("fichier", "facture.pdf", "application/pdf", pdf))
                        .param("nature", "FACTURE").with(FOURNISSEUR))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeContenu").value("application/pdf"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(get(documents + "/" + documentId + "/contenu").with(COMPTABLE))
                .andExpect(status().isOk())
                .andExpect(content().bytes(pdf));
        mvc.perform(get(documents).with(jeton("fournisseur.inconnu", "FOURNISSEUR")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RGCP, art. 4 : le comptable ne peut pas proposer d'engagement")
    void comptableNePeutPasEngager() throws Exception {
        poster("/api/engagements", COMPTABLE, """
                {"ligneBudgetaireId": 1, "serviceOrdonnateur": "SRV-DSI", "proposition": {
                  "objet": "Écrans", "typeCommande": "BON_DE_COMMANDE", "referencePiece": "BC-9",
                  "fournisseurNom": "Atlas Informatique SARL", "fournisseurIdentifiant": "001234567000089",
                  "lignes": [{"designation": "Écran", "quantite": 2, "prixUnitaire": 1500}], "montantPropose": 3000}}
                """).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("La documentation OpenAPI est publique et décrit l'authentification Keycloak")
    void documentationOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/engagements/{id}/decision']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.keycloak.type").value("oauth2"));
    }

    @Test
    @DisplayName("La base refuse toute modification ou suppression du journal d'audit")
    void journalEnAjoutSeul() {
        jdbc.sql("""
                INSERT INTO journal_audit (horodatage, acteur, action, type_objet, id_objet, details,
                    empreinte_precedente, empreinte)
                VALUES (now(), 'test', 'TEST', 'TEST', '0', 'test', repeat('0', 64), repeat('f', 64))
                ON CONFLICT DO NOTHING
                """).update();

        assertThatThrownBy(() -> jdbc.sql("UPDATE journal_audit SET details = 'falsifié'").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ajout seul");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_audit").update())
                .isInstanceOf(DataAccessException.class);
    }
}
