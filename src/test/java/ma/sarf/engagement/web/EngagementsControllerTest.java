package ma.sarf.engagement.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import ma.sarf.commun.domaine.RegleMetierException;
import ma.sarf.commun.securite.SecuriteConfig;
import ma.sarf.engagement.EngagementService;
import ma.sarf.engagement.domaine.TypeDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EngagementsController.class)
@Import(SecuriteConfig.class)
class EngagementsControllerTest {

    private static final String PROPOSITION = """
            {"ligneBudgetaireId": 1, "serviceOrdonnateur": "SRV-DSI", "proposition": {
              "objet": "Ordinateurs", "typeCommande": "BON_DE_COMMANDE", "referencePiece": "BC-1",
              "fournisseurNom": "Atlas", "fournisseurIdentifiant": "0012",
              "lignes": [{"designation": "PC", "quantite": 2, "prixUnitaire": 5000}], "montantPropose": 10000}}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EngagementService engagements;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static JwtRequestPostProcessor jeton(String utilisateur, String... roles) {
        return jwt().jwt(j -> j.subject(utilisateur).claim("preferred_username", utilisateur))
                .authorities(Arrays.stream(roles).map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toArray(GrantedAuthority[]::new));
    }

    @Test
    @DisplayName("Sans jeton, l'API répond 401")
    void sansJeton() throws Exception {
        mvc.perform(post("/api/engagements").contentType(MediaType.APPLICATION_JSON).content(PROPOSITION))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un comptable ne peut pas proposer d'engagement")
    void roleInsuffisant() throws Exception {
        mvc.perform(post("/api/engagements").with(jeton("karim", "COMPTABLE"))
                        .contentType(MediaType.APPLICATION_JSON).content(PROPOSITION))
                .andExpect(status().isForbidden());
        verifyNoInteractions(engagements);
    }

    @Test
    @DisplayName("RGCP, art. 4 : un jeton qui cumule ordonnateur et comptable est refusé")
    void cumulDesFonctionsRefuse() throws Exception {
        mvc.perform(post("/api/engagements").with(jeton("double", "ORDONNATEUR", "COMPTABLE"))
                        .contentType(MediaType.APPLICATION_JSON).content(PROPOSITION))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INCOMPATIBILITE_FONCTIONS"))
                .andExpect(jsonPath("$.reference").value("Décret royal n° 330-66 (RGCP), art. 4"));
        verifyNoInteractions(engagements);
    }

    @Test
    @DisplayName("Une proposition incomplète est rejetée avant d'atteindre le métier")
    void validation() throws Exception {
        mvc.perform(post("/api/engagements").with(jeton("amal", "ORDONNATEUR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ligneBudgetaireId\": 1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(engagements);
    }

    @Test
    @DisplayName("Une règle violée renvoie son code et l'article qui la fonde")
    void regleMetier() throws Exception {
        given(engagements.decider(anyLong(), eq(TypeDecision.VISA), any(), anyString()))
                .willThrow(new RegleMetierException("VISA_IMPOSSIBLE", "Décret n° 2-07-1235, art. 3", "Anomalies"));

        mvc.perform(post("/api/engagements/7/decision").with(jeton("karim", "COMPTABLE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"VISA\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VISA_IMPOSSIBLE"))
                .andExpect(jsonPath("$.reference").value("Décret n° 2-07-1235, art. 3"));
    }
}
