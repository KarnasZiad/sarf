package ma.sarf.commun.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalendrierOuvrableTest {

    private static final LocalDate LUNDI_2_MARS_2026 = LocalDate.of(2026, 3, 2);

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 6 : 5 jours ouvrables francs, le jour du dépôt n'est pas compté")
    void cinqJoursOuvrablesFrancs() {
        var calendrier = new CalendrierOuvrable(Set.of());
        assertThat(calendrier.ajouterJoursOuvrables(LUNDI_2_MARS_2026, 5)).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("Décret n° 2-07-1235, art. 6 : 12 jours ouvrables pour un marché, week-ends exclus")
    void douzeJoursPourUnMarche() {
        var calendrier = new CalendrierOuvrable(Set.of());
        assertThat(calendrier.ajouterJoursOuvrables(LUNDI_2_MARS_2026, 12)).isEqualTo(LocalDate.of(2026, 3, 18));
    }

    @Test
    @DisplayName("Un jour férié ne compte pas comme jour ouvrable")
    void jourFerieExclu() {
        var calendrier = new CalendrierOuvrable(Set.of(LocalDate.of(2026, 3, 3)));
        assertThat(calendrier.ajouterJoursOuvrables(LUNDI_2_MARS_2026, 5)).isEqualTo(LocalDate.of(2026, 3, 10));
        assertThat(calendrier.estOuvrable(LocalDate.of(2026, 3, 7))).isFalse();
    }
}
