package ma.sarf.depense.domaine;

import static ma.sarf.depense.domaine.Fixtures.COMPTABLE;
import static ma.sarf.depense.domaine.Fixtures.DEPOT;
import static ma.sarf.depense.domaine.Fixtures.ORDONNATEUR;
import static ma.sarf.depense.domaine.Fixtures.TEXTES;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import ma.sarf.commun.domaine.Montant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChronometreTest {

    private static final LocalDate SERVICE_FAIT = LocalDate.of(2026, 3, 12);

    @Test
    @DisplayName("Tant que le service fait n'est pas constaté, aucun délai ne court")
    void nonDemarre() {
        var etat = Chronometre.calculer(Fixtures.depose(), DEPOT.plusDays(20), TEXTES);

        assertThat(etat.echeancePaiement()).isNull();
        assertThat(etat.enRetard()).isFalse();
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 2 : 45 jours pour ordonnancer, 60 jours au total")
    void echeancesLegales() {
        var etat = Chronometre.calculer(Fixtures.ordonnance(), LocalDate.of(2026, 3, 20), TEXTES);

        assertThat(etat.echeanceOrdonnancement()).isEqualTo(LocalDate.of(2026, 4, 26));
        assertThat(etat.echeancePaiement()).isEqualTo(LocalDate.of(2026, 5, 11));
        assertThat(etat.echeanceVisaComptable()).isEqualTo(LocalDate.of(2026, 4, 4));
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 3 : les pièces manquantes du bénéficiaire prolongent les délais")
    void suspensionImputableAuBeneficiaire() {
        DossierPaiement dossier = Fixtures.depose();
        dossier.certifierServiceFait(ORDONNATEUR, SERVICE_FAIT, TEXTES);
        dossier.signalerPiecesManquantes(ORDONNATEUR, LocalDate.of(2026, 3, 15), "Attestation fiscale");
        dossier.recevoirPieces(ORDONNATEUR, LocalDate.of(2026, 3, 25));

        var etat = Chronometre.calculer(dossier, LocalDate.of(2026, 4, 1), TEXTES);

        assertThat(etat.joursSuspendus()).isEqualTo(10);
        assertThat(etat.echeancePaiement()).isEqualTo(LocalDate.of(2026, 5, 21));
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 4 : après une suspension du visa, le comptable garde au moins 5 jours")
    void reliquatMinimumDuComptable() {
        DossierPaiement dossier = Fixtures.ordonnance();
        dossier.suspendre(COMPTABLE, LocalDate.of(2026, 4, 1), Set.of(MotifSuspension.SIGNATURE_NON_QUALIFIEE), Set.of(),
                "Délégation de signature absente");
        dossier.regulariser(ORDONNATEUR, LocalDate.of(2026, 4, 6));

        var etat = Chronometre.calculer(dossier, LocalDate.of(2026, 4, 6), TEXTES);

        assertThat(dossier.joursComptableConsommes()).isEqualTo(12);
        assertThat(etat.echeanceVisaComptable()).isEqualTo(LocalDate.of(2026, 4, 11));
    }

    @Test
    @DisplayName("Un paiement après le 60e jour est en retard du nombre de jours écoulés depuis l'échéance")
    void retard() {
        DossierPaiement dossier = Fixtures.ordonnance();
        dossier.viserEtPayer(COMPTABLE, LocalDate.of(2026, 5, 26), ModeReglement.VIREMENT, Set.of(), Montant.ZERO, TEXTES);

        assertThat(Chronometre.calculer(dossier, LocalDate.of(2026, 6, 30), TEXTES).joursDeRetard()).isEqualTo(15);
    }
}
