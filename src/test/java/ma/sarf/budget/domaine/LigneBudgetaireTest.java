package ma.sarf.budget.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.RegleMetierException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LigneBudgetaireTest {

    private static LigneBudgetaire ligne(NatureCredit nature) {
        return LigneBudgetaire.ouvrir(2026, "1.2.1.10.31", "Matériel informatique", nature, Montant.de(100_000));
    }

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 3 : un engagement ne peut pas dépasser les crédits disponibles")
    void engagementDansLaLimiteDesCredits() {
        LigneBudgetaire ligne = ligne(NatureCredit.FONCTIONNEMENT);
        ligne.engager(Montant.de(60_000));

        assertThat(ligne.disponibleAEngager()).isEqualTo(Montant.de(40_000));
        assertThatThrownBy(() -> ligne.engager(Montant.de(40_001)))
                .isInstanceOf(RegleMetierException.class)
                .hasFieldOrPropertyWithValue("code", "CREDITS_INSUFFISANTS");
    }

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 8 : un paiement ne consomme que des crédits engagés")
    void paiementDansLaLimiteDeLEngage() {
        LigneBudgetaire ligne = ligne(NatureCredit.FONCTIONNEMENT);
        ligne.engager(Montant.de(10_000));
        ligne.payer(Montant.de(10_000));

        assertThatThrownBy(() -> ligne.payer(Montant.de("0.01")))
                .isInstanceOf(RegleMetierException.class)
                .hasFieldOrPropertyWithValue("code", "CREDITS_PAIEMENT_INSUFFISANTS");
    }

    @Test
    @DisplayName("RGCP, art. 87 : les crédits de fonctionnement non consommés tombent en annulation")
    void fonctionnementAnnule() {
        LigneBudgetaire ligne = ligne(NatureCredit.FONCTIONNEMENT);
        ligne.engager(Montant.de(30_000));
        ligne.payer(Montant.de(30_000));

        assertThat(ligne.soldeFinExercice()).isEqualTo(
                new LigneBudgetaire.SoldeFinExercice(Montant.de(70_000), Montant.ZERO));
    }

    @Test
    @DisplayName("RGCP, art. 87 : les crédits d'investissement disponibles sont reportés")
    void investissementReporte() {
        LigneBudgetaire ligne = ligne(NatureCredit.INVESTISSEMENT);

        assertThat(ligne.soldeFinExercice().reporte()).isEqualTo(Montant.de(100_000));
    }
}
