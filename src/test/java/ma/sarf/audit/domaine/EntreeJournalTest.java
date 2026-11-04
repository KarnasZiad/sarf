package ma.sarf.audit.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntreeJournalTest {

    private static List<EntreeJournal> chaine(int taille) {
        List<EntreeJournal> journal = new ArrayList<>();
        String precedente = EntreeJournal.ORIGINE;
        for (int i = 1; i <= taille; i++) {
            Instant instant = Instant.parse("2026-03-02T09:00:00Z").plusSeconds(i);
            String empreinte = EntreeJournal.empreinte(precedente, instant, "karim", "ACTION_" + i, "DOSSIER", "" + i,
                    "détail " + i);
            journal.add(new EntreeJournal(i, instant, "karim", "ACTION_" + i, "DOSSIER", "" + i, "détail " + i,
                    precedente, empreinte));
            precedente = empreinte;
        }
        return journal;
    }

    @Test
    @DisplayName("Une chaîne intacte est validée de bout en bout")
    void chaineIntacte() {
        assertThat(EntreeJournal.premiereRupture(chaine(5))).isEmpty();
    }

    @Test
    @DisplayName("Modifier le détail d'une entrée est détecté")
    void modificationDetectee() {
        List<EntreeJournal> journal = chaine(5);
        EntreeJournal e = journal.get(2);
        journal.set(2, new EntreeJournal(e.id(), e.horodatage(), e.acteur(), e.action(), e.typeObjet(), e.idObjet(),
                "montant modifié", e.empreintePrecedente(), e.empreinte()));

        assertThat(EntreeJournal.premiereRupture(journal)).contains(3L);
    }

    @Test
    @DisplayName("Supprimer une entrée est détecté")
    void suppressionDetectee() {
        List<EntreeJournal> journal = chaine(5);
        journal.remove(1);

        assertThat(EntreeJournal.premiereRupture(journal)).contains(3L);
    }
}
