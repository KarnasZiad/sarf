package ma.sarf.audit.web;

import java.util.List;
import ma.sarf.audit.JournalAuditService;
import ma.sarf.audit.domaine.EntreeJournal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('AUDITEUR', 'ADMINISTRATEUR')")
class AuditController {

    private final JournalAuditService audit;

    AuditController(JournalAuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    List<EntreeJournal> dernieres(@RequestParam(defaultValue = "100") int limite) {
        return audit.dernieres(limite);
    }

    /** Rejoue toute la chaîne d'empreintes et signale la première entrée altérée, s'il y en a une. */
    @GetMapping("/integrite")
    JournalAuditService.Integrite verifier() {
        return audit.verifier();
    }
}
