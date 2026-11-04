package ma.sarf.depense.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import ma.sarf.commun.domaine.Montant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ControleValiditeTest {

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 8 : un dossier complet ne présente aucune anomalie")
    void dossierComplet() {
        assertThat(ControleValidite.anomalies(Fixtures.ordonnance(), true, true, true)).isEmpty();
    }

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 8 : chaque point du contrôle de validité est vérifié")
    void chaquePointVerifie() {
        assertThat(ControleValidite.anomalies(Fixtures.ordonnance(), false, false, false)).containsExactlyInAnyOrder(
                MotifSuspension.ABSENCE_VISA_ENGAGEMENT,
                MotifSuspension.CREDITS_PAIEMENT_INSUFFISANTS,
                MotifSuspension.SIGNATURE_NON_QUALIFIEE);
    }

    @Test
    @DisplayName("Un RIB marocain invalide rend le règlement non libératoire")
    void ribInvalide() {
        DossierPaiement dossier = DossierPaiement.deposer(Fixtures.depose().origine(), "F-1", Montant.de(5_000),
                Montant.ZERO, "0117800000", LocalDate.of(2026, 3, 2), "f");
        dossier.certifierServiceFait(Fixtures.ORDONNATEUR, LocalDate.of(2026, 3, 2), Fixtures.TEXTES);
        dossier.liquider(Fixtures.ORDONNATEUR, Montant.de(5_000));

        assertThat(ControleValidite.anomalies(dossier, true, true, true))
                .containsExactly(MotifSuspension.DEFAUT_CARACTERE_LIBERATOIRE);
    }
}
