package ma.sarf.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import ma.sarf.audit.domaine.EntreeJournal;
import ma.sarf.audit.persistance.Journal;
import ma.sarf.commun.domaine.EvenementMetier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consigne chaque fait métier dans le journal, dans la même transaction que l'opération : si
 * l'opération est annulée, son entrée l'est aussi, et une opération validée est toujours tracée.
 */
@Service
public class JournalAuditService {

    public record Integrite(boolean intacte, long entreesVerifiees, Long premiereEntreeAlteree) {
    }

    private final Journal journal;
    private final Clock horloge;

    JournalAuditService(Journal journal, Clock horloge) {
        this.journal = journal;
        this.horloge = horloge;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    void consigner(EvenementMetier evenement) {
        // PostgreSQL stocke les instants à la microseconde : on tronque avant de calculer l'empreinte.
        Instant horodatage = Instant.now(horloge).truncatedTo(ChronoUnit.MICROS);
        journal.ajouter(horodatage, evenement.acteur(), evenement.action(), evenement.typeObjet(), evenement.idObjet(),
                evenement.details());
    }

    @Transactional(readOnly = true)
    public List<EntreeJournal> dernieres(int limite) {
        return journal.dernieres(Math.clamp(limite, 1, 500));
    }

    @Transactional(readOnly = true)
    public Integrite verifier() {
        List<EntreeJournal> entrees = journal.toutes();
        var rupture = EntreeJournal.premiereRupture(entrees);
        return new Integrite(rupture.isEmpty(), entrees.size(), rupture.orElse(null));
    }
}
