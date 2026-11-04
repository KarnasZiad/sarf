package ma.sarf.commun.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Horloge de démonstration : la date courante peut être déplacée pour montrer, en quelques
 * secondes, ce qui se passe quand un délai de 60 jours expire. Uniquement en profil « demo ».
 */
public final class HorlogeSimulee extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> instant;

    public HorlogeSimulee(Instant depart, ZoneId zone) {
        this.zone = zone;
        this.instant = new AtomicReference<>(depart);
    }

    public void allerAu(LocalDate date) {
        instant.set(date.atStartOfDay(zone).plusHours(9).toInstant());
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId autreZone) {
        return new HorlogeSimulee(instant.get(), autreZone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
