package ma.sarf.depense.domaine;

import java.time.LocalDate;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.domaine.Reglementation;

final class Fixtures {

    static final Reglementation TEXTES = Reglementation.textesEnVigueur();
    static final String ORDONNATEUR = "amal";
    static final String COMPTABLE = "karim";
    static final String RIB = "011780000012345678901234";
    static final LocalDate DEPOT = LocalDate.of(2026, 3, 2);

    private Fixtures() {
    }

    static DossierPaiement depose() {
        return DossierPaiement.deposer(
                new DossierPaiement.Origine(1L, 2026, "SRV-DSI", ORDONNATEUR, 1L, "1.2.1.10.31",
                        "Atlas Informatique SARL", "001234567000089"),
                "F-2026-0042", Montant.de(100_000), Montant.ZERO, RIB, DEPOT, "fournisseur.atlas");
    }

    /** Dossier ordonnancé le 20 mars, service fait constaté le 12 mars. */
    static DossierPaiement ordonnance() {
        DossierPaiement dossier = depose();
        dossier.certifierServiceFait(ORDONNATEUR, LocalDate.of(2026, 3, 12), TEXTES);
        dossier.liquider(ORDONNATEUR, Montant.de(100_000));
        dossier.ordonnancer(ORDONNATEUR, "SRV-DSI-2026-000001", LocalDate.of(2026, 3, 20));
        return dossier;
    }
}
