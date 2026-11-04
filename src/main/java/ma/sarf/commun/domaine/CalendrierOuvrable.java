package ma.sarf.commun.domaine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Calendrier des jours ouvrables : du lundi au vendredi, hors jours fériés.
 *
 * <p>Les jours fériés sont fournis de l'extérieur, car les fêtes religieuses changent de date
 * chaque année et de nouveaux jours fériés peuvent être créés.
 */
public final class CalendrierOuvrable {

    private final Set<LocalDate> joursFeries;

    public CalendrierOuvrable(Set<LocalDate> joursFeries) {
        this.joursFeries = Set.copyOf(Objects.requireNonNull(joursFeries));
    }

    public boolean estOuvrable(LocalDate jour) {
        DayOfWeek jourDeSemaine = jour.getDayOfWeek();
        return jourDeSemaine != DayOfWeek.SATURDAY
                && jourDeSemaine != DayOfWeek.SUNDAY
                && !joursFeries.contains(jour);
    }

    /**
     * Date d'expiration d'un délai de {@code nombre} jours ouvrables francs : le jour de départ
     * n'est pas compté, seuls les jours ouvrables qui le suivent le sont.
     */
    public LocalDate ajouterJoursOuvrables(LocalDate depart, int nombre) {
        if (nombre < 0) {
            throw new IllegalArgumentException("Nombre de jours négatif : " + nombre);
        }
        LocalDate jour = depart;
        int comptes = 0;
        while (comptes < nombre) {
            jour = jour.plusDays(1);
            if (estOuvrable(jour)) {
                comptes++;
            }
        }
        return jour;
    }
}
