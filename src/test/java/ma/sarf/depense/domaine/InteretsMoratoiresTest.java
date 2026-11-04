package ma.sarf.depense.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import ma.sarf.commun.domaine.Montant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InteretsMoratoiresTest {

    private static final Montant CREANCE = Montant.de(100_000);

    private static InteretsMoratoires.TableDesTaux table(Map<String, String> taux) {
        return (annee, trimestre) -> Optional.ofNullable(taux.get(annee + "T" + trimestre)).map(BigDecimal::new);
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 11 : intérêts = créance × jours × taux / 365")
    void formule() {
        var calcul = InteretsMoratoires.calculer(CREANCE, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 26),
                table(Map.of("2026T2", "3.4")));

        assertThat(calcul.jours()).isEqualTo(15);
        // 100 000 × 15 × 3,4 % / 365 = 139,726...
        assertThat(calcul.total()).isEqualTo(Montant.de("139.73"));
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 9 : rien n'est dû si le paiement intervient au plus tard à l'échéance")
    void aucunInteretDansLeDelai() {
        var calcul = InteretsMoratoires.calculer(CREANCE, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 11),
                table(Map.of()));

        assertThat(calcul.total()).isEqualTo(Montant.ZERO);
    }

    @Test
    @DisplayName("Un retard à cheval sur deux trimestres applique à chaque jour le taux de son trimestre")
    void decoupageParTrimestre() {
        var calcul = InteretsMoratoires.calculer(CREANCE, LocalDate.of(2026, 6, 25), LocalDate.of(2026, 7, 5),
                table(Map.of("2026T2", "3.4", "2026T3", "3.0")));

        assertThat(calcul.tranches()).extracting(InteretsMoratoires.Tranche::jours).containsExactly(5L, 5L);
        // 100 000 × 5 × 3,4 % / 365 + 100 000 × 5 × 3,0 % / 365 = 46,575 + 41,096
        assertThat(calcul.total()).isEqualTo(Montant.de("87.67"));
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 11, al. 2 : faute de taux publié, celui du trimestre précédent s'applique")
    void tauxDuTrimestrePrecedent() {
        var calcul = InteretsMoratoires.calculer(CREANCE, LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 10),
                table(Map.of("2026T4", "3.4")));

        assertThat(calcul.tranches()).singleElement()
                .satisfies(t -> assertThat(t.taux()).isEqualByComparingTo("3.4"));
    }

    @Test
    @DisplayName("Sans aucun taux enregistré, le calcul est refusé plutôt que faux")
    void tauxInconnu() {
        assertThatThrownBy(() -> InteretsMoratoires.calculer(CREANCE, LocalDate.of(2026, 5, 11),
                LocalDate.of(2026, 5, 26), table(Map.of())))
                .hasFieldOrPropertyWithValue("code", "TAUX_INCONNU");
    }

    @ParameterizedTest(name = "bons du Trésor à {0} % : taux de {1} %")
    @CsvSource({"2.21, 3.3", "2.30, 3.3", "2.31, 3.4", "1.95, 3.0"})
    @DisplayName("Décret n° 2-16-344, art. 11 : taux des bons du Trésor à 3 mois + 1 point, arrondi au dixième supérieur")
    void tauxDepuisLesBonsDuTresor(String tauxMoyen, String attendu) {
        assertThat(InteretsMoratoires.tauxDepuisBonsDuTresor(new BigDecimal(tauxMoyen))).isEqualByComparingTo(attendu);
    }
}
