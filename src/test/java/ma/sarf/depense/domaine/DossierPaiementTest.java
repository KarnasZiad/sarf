package ma.sarf.depense.domaine;

import static ma.sarf.depense.domaine.Fixtures.COMPTABLE;
import static ma.sarf.depense.domaine.Fixtures.DEPOT;
import static ma.sarf.depense.domaine.Fixtures.ORDONNATEUR;
import static ma.sarf.depense.domaine.Fixtures.TEXTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import ma.sarf.commun.domaine.Montant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DossierPaiementTest {

    private static final LocalDate PAIEMENT = LocalDate.of(2026, 3, 25);

    @Nested
    class ServiceFait {

        @Test
        @DisplayName("Décret n° 2-16-344, art. 5 : le service fait certifié dans les 30 jours garde sa date")
        void dansLeDelai() {
            DossierPaiement dossier = Fixtures.depose();
            dossier.certifierServiceFait(ORDONNATEUR, DEPOT.plusDays(30), TEXTES);

            assertThat(dossier.dateServiceFait()).isEqualTo(DEPOT.plusDays(30));
        }

        @Test
        @DisplayName("Décret n° 2-16-344, art. 6 : au-delà de 30 jours, la date retenue est le 31e jour")
        void horsDelaiLe31eJour() {
            DossierPaiement dossier = Fixtures.depose();
            dossier.certifierServiceFait(ORDONNATEUR, DEPOT.plusDays(50), TEXTES);

            assertThat(dossier.dateServiceFait()).isEqualTo(DEPOT.plusDays(31));
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 8 : seul l'ordonnateur certifie le service fait")
        void reserveeALOrdonnateur() {
            assertThatThrownBy(() -> Fixtures.depose().certifierServiceFait("fournisseur.atlas", DEPOT, TEXTES))
                    .hasFieldOrPropertyWithValue("code", "ACTE_RESERVE_A_L_ORDONNATEUR");
        }
    }

    @Test
    @DisplayName("RGCP, art. 32 : engager, liquider, ordonnancer puis payer, dans cet ordre")
    void ordreDesEtapes() {
        DossierPaiement dossier = Fixtures.depose();

        assertThatThrownBy(() -> dossier.liquider(ORDONNATEUR, Montant.de(100)))
                .hasFieldOrPropertyWithValue("code", "ETAPE_INCORRECTE");
        assertThatThrownBy(() -> dossier.ordonnancer(ORDONNATEUR, "N", DEPOT))
                .hasFieldOrPropertyWithValue("code", "ETAPE_INCORRECTE");
    }

    @Test
    @DisplayName("RGCP, art. 34 : le montant liquidé ne dépasse pas la facture")
    void liquidationPlafonnee() {
        DossierPaiement dossier = Fixtures.depose();
        dossier.certifierServiceFait(ORDONNATEUR, DEPOT, TEXTES);

        assertThatThrownBy(() -> dossier.liquider(ORDONNATEUR, Montant.de(100_001)))
                .hasFieldOrPropertyWithValue("code", "LIQUIDATION_INVALIDE");
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 3 : pas d'ordonnancement tant que des pièces sont attendues")
    void piecesEnAttente() {
        DossierPaiement dossier = Fixtures.depose();
        dossier.certifierServiceFait(ORDONNATEUR, DEPOT, TEXTES);
        dossier.liquider(ORDONNATEUR, Montant.de(100_000));
        dossier.signalerPiecesManquantes(ORDONNATEUR, DEPOT.plusDays(2), "Attestation de régularité fiscale");

        assertThatThrownBy(() -> dossier.ordonnancer(ORDONNATEUR, "N-1", DEPOT.plusDays(3)))
                .hasFieldOrPropertyWithValue("code", "PIECES_EN_ATTENTE");

        dossier.recevoirPieces(ORDONNATEUR, DEPOT.plusDays(9));
        dossier.ordonnancer(ORDONNATEUR, "N-1", DEPOT.plusDays(10));
        assertThat(dossier.statut()).isEqualTo(StatutDossier.ORDONNANCE);
    }

    @Nested
    class Paiement {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 8 : visa et règlement quand le contrôle de validité est sans anomalie")
        void visaEtReglement() {
            DossierPaiement dossier = Fixtures.ordonnance();
            dossier.viserEtPayer(COMPTABLE, PAIEMENT, ModeReglement.VIREMENT, Set.of(), Montant.ZERO, TEXTES);

            assertThat(dossier.statut()).isEqualTo(StatutDossier.PAYE);
            assertThat(dossier.datePaiement()).isEqualTo(PAIEMENT);
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 8 : pas de visa si le contrôle de validité relève une anomalie")
        void visaImpossible() {
            DossierPaiement dossier = Fixtures.ordonnance();

            assertThatThrownBy(() -> dossier.viserEtPayer(COMPTABLE, PAIEMENT, ModeReglement.VIREMENT,
                    Set.of(MotifSuspension.DEFAUT_CARACTERE_LIBERATOIRE), Montant.ZERO, TEXTES))
                    .hasFieldOrPropertyWithValue("code", "VISA_PAIEMENT_IMPOSSIBLE");
        }

        @Test
        @DisplayName("RGCP, art. 45 : au-delà de 1 500 DH, paiement par virement uniquement")
        void virementObligatoire() {
            DossierPaiement dossier = Fixtures.ordonnance();

            assertThatThrownBy(() -> dossier.viserEtPayer(COMPTABLE, PAIEMENT, ModeReglement.ESPECES, Set.of(),
                    Montant.ZERO, TEXTES))
                    .hasFieldOrPropertyWithValue("code", "VIREMENT_OBLIGATOIRE");
        }

        @Test
        @DisplayName("RGCP, art. 4 : celui qui a ordonnancé ne peut pas payer")
        void incompatibilite() {
            DossierPaiement dossier = Fixtures.ordonnance();

            assertThatThrownBy(() -> dossier.viserEtPayer(ORDONNATEUR, PAIEMENT, ModeReglement.VIREMENT, Set.of(),
                    Montant.ZERO, TEXTES))
                    .hasFieldOrPropertyWithValue("code", "INCOMPATIBILITE_FONCTIONS");
        }
    }

    @Nested
    class SuspensionEtRequisition {

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 8 : une suspension est appuyée d'une note motivée")
        void noteMotivee() {
            DossierPaiement dossier = Fixtures.ordonnance();

            assertThatThrownBy(() -> dossier.suspendre(COMPTABLE, PAIEMENT,
                    Set.of(MotifSuspension.SIGNATURE_NON_QUALIFIEE), Set.of(), ""))
                    .hasFieldOrPropertyWithValue("code", "SUSPENSION_NON_MOTIVEE");
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 29 : sur réquisition écrite, le comptable paie et sa responsabilité est dégagée")
        void requisitionAcceptee() {
            DossierPaiement dossier = Fixtures.ordonnance();
            dossier.suspendre(COMPTABLE, PAIEMENT, Set.of(MotifSuspension.SIGNATURE_NON_QUALIFIEE), Set.of(),
                    "Délégation de signature non transmise");
            dossier.requerir(ORDONNATEUR, PAIEMENT.plusDays(1), "Urgence : maintenance du centre de données");
            dossier.payerSurRequisition(COMPTABLE, PAIEMENT.plusDays(2), ModeReglement.VIREMENT, Set.of(),
                    Montant.ZERO, TEXTES);

            assertThat(dossier.statut()).isEqualTo(StatutDossier.PAYE);
            assertThat(dossier.payeSurRequisition()).isTrue();
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 30 : pas de réquisition contre une insuffisance de crédits")
        void requisitionIrrecevable() {
            DossierPaiement dossier = Fixtures.ordonnance();
            dossier.suspendre(COMPTABLE, PAIEMENT, Set.of(), Set.of(MotifSuspension.CREDITS_PAIEMENT_INSUFFISANTS),
                    "Crédits épuisés sur la ligne");

            assertThatThrownBy(() -> dossier.requerir(ORDONNATEUR, PAIEMENT, "Urgence"))
                    .hasFieldOrPropertyWithValue("code", "REQUISITION_IRRECEVABLE")
                    .hasMessageContaining("Crédits de paiement");
        }

        @Test
        @DisplayName("Décret n° 2-07-1235, art. 30 : les motifs bloquants sont revérifiés au moment de payer")
        void motifsBloquantsReverifies() {
            DossierPaiement dossier = Fixtures.ordonnance();
            dossier.suspendre(COMPTABLE, PAIEMENT, Set.of(MotifSuspension.SIGNATURE_NON_QUALIFIEE), Set.of(), "Note");
            dossier.requerir(ORDONNATEUR, PAIEMENT, "Urgence");

            assertThatThrownBy(() -> dossier.payerSurRequisition(COMPTABLE, PAIEMENT, ModeReglement.VIREMENT,
                    Set.of(MotifSuspension.ABSENCE_VISA_ENGAGEMENT), Montant.ZERO, TEXTES))
                    .hasFieldOrPropertyWithValue("code", "REQUISITION_IRRECEVABLE");
        }

        @Test
        @DisplayName("Après régularisation, l'ordonnance revient au comptable")
        void regularisation() {
            DossierPaiement dossier = Fixtures.ordonnance();
            dossier.suspendre(COMPTABLE, LocalDate.of(2026, 3, 27), Set.of(MotifSuspension.PIECES_JUSTIFICATIVES_INCOMPLETES),
                    Set.of(), "Procès-verbal de réception manquant");
            dossier.regulariser(ORDONNATEUR, LocalDate.of(2026, 3, 30));

            assertThat(dossier.statut()).isEqualTo(StatutDossier.ORDONNANCE);
            assertThat(dossier.joursComptableConsommes()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("Décret n° 2-16-344, art. 10 : la base des intérêts est le principal diminué de la retenue de garantie")
    void baseDesInterets() {
        DossierPaiement dossier = DossierPaiement.deposer(Fixtures.depose().origine(), "F-9", Montant.de(100_000),
                Montant.de(7_000), Fixtures.RIB, DEPOT, "f");
        dossier.certifierServiceFait(ORDONNATEUR, DEPOT, TEXTES);
        dossier.liquider(ORDONNATEUR, Montant.de(100_000));

        assertThat(dossier.baseInteretsMoratoires()).isEqualTo(Montant.de(93_000));
    }
}
