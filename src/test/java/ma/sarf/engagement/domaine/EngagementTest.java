package ma.sarf.engagement.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import ma.sarf.commun.domaine.CalendrierOuvrable;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.Observation;
import ma.sarf.commun.domaine.Reglementation;
import ma.sarf.commun.domaine.RegleMetierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EngagementTest {

    private static final Reglementation TEXTES = Reglementation.textesEnVigueur();
    private static final CalendrierOuvrable CALENDRIER = new CalendrierOuvrable(Set.of());
    private static final LocalDate LUNDI = LocalDate.of(2026, 3, 2);
    private static final Montant LARGEMENT = Montant.de(10_000_000);

    static Proposition proposition(TypeCommande type, long montant, String reference) {
        return new Proposition("Ordinateurs portables", type, reference, "Atlas Informatique SARL", "001234567000089",
                List.of(new LigneDetail("Ordinateur portable", BigDecimal.valueOf(10), Montant.de(montant / 10))),
                Montant.de(montant));
    }

    static Engagement soumis(RegimeControle regime, TypeCommande type, long montant, String reference) {
        return Engagement.soumettre(2026, "SRV-DSI", regime, 1L, "1.2.1.10.31", "amal",
                proposition(type, montant, reference), LUNDI, TEXTES, CALENDRIER);
    }

    static Engagement soumis(long montant) {
        return soumis(RegimeControle.NORMAL, TypeCommande.BON_DE_COMMANDE, montant, "BC-2026-001");
    }

    @Nested
    class Soumission {

        @Test
        @DisplayName("Décret n° 2-16-344, art. 12 : l'engagement est majoré d'une somme à valoir de 1 %")
        void sommeAValoir() {
            Engagement engagement = soumis(100_000);

            assertThat(engagement.sommeAValoir()).isEqualTo(Montant.de(1_000));
            assertThat(engagement.montantAEngager()).isEqualTo(Montant.de(101_000));
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 6 : le comptable a 5 jours ouvrables, 12 pour un marché")
        void delaiDeVisaRegimeNormal() {
            assertThat(soumis(100_000).echeanceVisa()).isEqualTo(LocalDate.of(2026, 3, 9));
            assertThat(soumis(RegimeControle.NORMAL, TypeCommande.MARCHE, 500_000, "M-01").echeanceVisa())
                    .isEqualTo(LocalDate.of(2026, 3, 18));
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 13 : en contrôle modulé, 4 jours, et 10 pour un marché")
        void delaiDeVisaControleModule() {
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.BON_DE_COMMANDE, 50_000, "BC").echeanceVisa())
                    .isEqualTo(LocalDate.of(2026, 3, 6));
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.MARCHE, 500_000, "M").echeanceVisa())
                    .isEqualTo(LocalDate.of(2026, 3, 16));
        }
    }

    @Nested
    class ControleDeRegularite {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 10 : jusqu'à 20 000 DH, seul le contrôle budgétaire s'exerce")
        void seuilDuRegimeNormal() {
            assertThat(soumis(20_000).exigeControleDeRegularite(TEXTES)).isFalse();
            assertThat(soumis(20_010).exigeControleDeRegularite(TEXTES)).isTrue();
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 13 : en contrôle modulé, seuils de 100 000, 200 000 et 400 000 DH")
        void seuilsDuControleModule() {
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.BON_DE_COMMANDE, 100_000, "BC")
                    .exigeControleDeRegularite(TEXTES)).isFalse();
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.BON_DE_COMMANDE, 100_010, "BC")
                    .exigeControleDeRegularite(TEXTES)).isTrue();
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.CONTRAT_DROIT_COMMUN, 200_010, "C")
                    .exigeControleDeRegularite(TEXTES)).isTrue();
            assertThat(soumis(RegimeControle.MODULE, TypeCommande.MARCHE, 400_000, "M")
                    .exigeControleDeRegularite(TEXTES)).isFalse();
        }

        @Test
        @DisplayName("Sans pièce d'engagement référencée, le contrôle de régularité relève une anomalie")
        void pieceManquante() {
            Engagement engagement = soumis(RegimeControle.NORMAL, TypeCommande.BON_DE_COMMANDE, 50_000, null);

            assertThat(codes(engagement.controlesAutomatiques(LARGEMENT, true, TEXTES)))
                    .containsExactly("PIECE_ENGAGEMENT_MANQUANTE");
        }

        @Test
        @DisplayName("Sous le seuil, l'absence de pièce n'est pas relevée")
        void sousLeSeuilPasDePieceExigee() {
            Engagement engagement = soumis(RegimeControle.NORMAL, TypeCommande.BON_DE_COMMANDE, 15_000, null);

            assertThat(engagement.controlesAutomatiques(LARGEMENT, true, TEXTES)).isEmpty();
        }
    }

    @Nested
    class ControleBudgetaire {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 3 : crédits insuffisants, somme à valoir comprise")
        void creditsInsuffisants() {
            Engagement engagement = soumis(100_000);

            assertThat(codes(engagement.controlesAutomatiques(Montant.de(100_999), true, TEXTES)))
                    .containsExactly("CREDITS_INSUFFISANTS");
            assertThat(engagement.controlesAutomatiques(Montant.de(101_000), true, TEXTES)).isEmpty();
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 3 : le total des lignes doit correspondre au montant proposé")
        void exactitudeDesCalculs() {
            var fausse = new Proposition("Imprimantes", TypeCommande.BON_DE_COMMANDE, "BC-7", "Atlas", "0012",
                    List.of(new LigneDetail("Imprimante", BigDecimal.valueOf(3), Montant.de(1_000))), Montant.de(3_500));
            Engagement engagement = Engagement.soumettre(2026, "SRV-DSI", RegimeControle.NORMAL, 1L, "1.2", "amal",
                    fausse, LUNDI, TEXTES, CALENDRIER);

            assertThat(codes(engagement.controlesAutomatiques(LARGEMENT, true, TEXTES))).containsExactly("CALCUL_INEXACT");
        }

        @Test
        @DisplayName("RGCP, art. 5 : l'ordonnateur doit être accrédité auprès du comptable")
        void ordonnateurNonAccredite() {
            assertThat(codes(soumis(10_000).controlesAutomatiques(LARGEMENT, false, TEXTES)))
                    .containsExactly("ORDONNATEUR_NON_ACCREDITE");
        }
    }

    @Nested
    class DecisionDuComptable {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 5 : visa d'un engagement sans anomalie")
        void visa() {
            Engagement engagement = soumis(100_000);
            engagement.decider(TypeDecision.VISA, "karim", LUNDI, List.of(), List.of());

            assertThat(engagement.statut()).isEqualTo(StatutEngagement.VISE);
        }

        @Test
        @DisplayName("Le visa est impossible tant qu'une anomalie automatique subsiste")
        void visaImpossibleAvecAnomalie() {
            Engagement engagement = soumis(100_000);
            var anomalies = engagement.controlesAutomatiques(Montant.de(1_000), true, TEXTES);

            assertThatThrownBy(() -> engagement.decider(TypeDecision.VISA, "karim", LUNDI, List.of(), anomalies))
                    .hasFieldOrPropertyWithValue("code", "VISA_IMPOSSIBLE");
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 5 : une suspension doit être motivée")
        void suspensionMotivee() {
            Engagement engagement = soumis(100_000);

            assertThatThrownBy(() -> engagement.decider(TypeDecision.SUSPENSION, "karim", LUNDI, List.of(" "), List.of()))
                    .hasFieldOrPropertyWithValue("code", "DECISION_NON_MOTIVEE");
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 5 : toutes les observations sont groupées en une seule communication")
        void observationsGroupees() {
            Engagement engagement = soumis(100_000);
            var anomalies = engagement.controlesAutomatiques(Montant.de(1_000), true, TEXTES);
            engagement.decider(TypeDecision.SUSPENSION, "karim", LUNDI, List.of("Joindre le bon de commande signé"),
                    anomalies);

            assertThat(engagement.statut()).isEqualTo(StatutEngagement.SUSPENDU);
            assertThat(engagement.decisions()).singleElement()
                    .satisfies(d -> assertThat(d.observations()).hasSize(2)
                            .anySatisfy(o -> assertThat(o).contains("Crédits insuffisants"))
                            .contains("Joindre le bon de commande signé"));
        }

        @Test
        @DisplayName("RGCP, art. 4 : le comptable ne peut pas viser son propre engagement")
        void incompatibilite() {
            Engagement engagement = soumis(100_000);

            assertThatThrownBy(() -> engagement.decider(TypeDecision.VISA, "amal", LUNDI, List.of(), List.of()))
                    .isInstanceOf(RegleMetierException.class)
                    .hasFieldOrPropertyWithValue("code", "INCOMPATIBILITE_FONCTIONS");
        }

        @Test
        @DisplayName("Un engagement déjà décidé ne peut pas l'être une seconde fois")
        void pasDeDoubleDecision() {
            Engagement engagement = soumis(100_000);
            engagement.decider(TypeDecision.REFUS, "karim", LUNDI, List.of("Dépense non prévue"), List.of());

            assertThatThrownBy(() -> engagement.decider(TypeDecision.VISA, "karim", LUNDI, List.of(), List.of()))
                    .hasFieldOrPropertyWithValue("code", "ENGAGEMENT_DEJA_TRAITE");
        }
    }

    @Nested
    class Regularisation {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 5 : après suspension, l'ordonnateur représente sa proposition corrigée")
        void regularisation() {
            Engagement engagement = soumis(RegimeControle.NORMAL, TypeCommande.BON_DE_COMMANDE, 50_000, null);
            engagement.decider(TypeDecision.SUSPENSION, "karim", LUNDI, List.of("Pièce manquante"), List.of());
            LocalDate jeudi = LocalDate.of(2026, 3, 5);

            engagement.regulariser("amal", proposition(TypeCommande.BON_DE_COMMANDE, 50_000, "BC-12"), jeudi, TEXTES,
                    CALENDRIER);

            assertThat(engagement.statut()).isEqualTo(StatutEngagement.SOUMIS);
            assertThat(engagement.echeanceVisa()).isEqualTo(LocalDate.of(2026, 3, 12));
            assertThat(engagement.controlesAutomatiques(LARGEMENT, true, TEXTES)).isEmpty();
        }

        @Test
        @DisplayName("Seul l'ordonnateur qui a proposé l'engagement peut le régulariser")
        void reserveeALOrdonnateur() {
            Engagement engagement = soumis(50_000);
            engagement.decider(TypeDecision.SUSPENSION, "karim", LUNDI, List.of("Pièce manquante"), List.of());

            assertThatThrownBy(() -> engagement.regulariser("autre", proposition(TypeCommande.BON_DE_COMMANDE, 50_000, "BC"),
                    LUNDI, TEXTES, CALENDRIER))
                    .hasFieldOrPropertyWithValue("code", "ACTE_RESERVE_A_L_ORDONNATEUR");
        }
    }

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 6, al. 2 : faute de réponse dans le délai, le visa d'un marché est dû")
    void visaTaciteDesMarches() {
        Engagement marche = soumis(RegimeControle.NORMAL, TypeCommande.MARCHE, 500_000, "M-01");

        assertThat(marche.visaTacitementDu(LocalDate.of(2026, 3, 18))).isFalse();
        assertThat(marche.visaTacitementDu(LocalDate.of(2026, 3, 19))).isTrue();
        assertThat(soumis(100_000).visaTacitementDu(LocalDate.of(2026, 4, 30))).isFalse();
    }

    @Test
    @DisplayName("RGCP, art. 33 : on ne liquide pas plus que le principal engagé")
    void liquidationPlafonnee() {
        Engagement engagement = soumis(100_000);
        assertThatThrownBy(() -> engagement.liquider(Montant.de(10)))
                .hasFieldOrPropertyWithValue("code", "ENGAGEMENT_NON_VISE");

        engagement.decider(TypeDecision.VISA, "karim", LUNDI, List.of(), List.of());
        engagement.liquider(Montant.de(60_000));

        assertThat(engagement.resteALiquider()).isEqualTo(Montant.de(40_000));
        assertThatThrownBy(() -> engagement.liquider(Montant.de(40_001)))
                .hasFieldOrPropertyWithValue("code", "LIQUIDATION_DEPASSE_ENGAGEMENT");
    }

    private static List<String> codes(List<Observation> observations) {
        return observations.stream().map(Observation::code).toList();
    }
}
