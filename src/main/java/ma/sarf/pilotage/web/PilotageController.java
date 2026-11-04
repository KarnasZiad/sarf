package ma.sarf.pilotage.web;

import ma.sarf.pilotage.PilotageService;
import ma.sarf.pilotage.TableauDeBord;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pilotage")
class PilotageController {

    private final PilotageService pilotage;

    PilotageController(PilotageService pilotage) {
        this.pilotage = pilotage;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORDONNATEUR', 'COMPTABLE', 'AUDITEUR', 'ADMINISTRATEUR')")
    TableauDeBord tableauDeBord(@RequestParam int exercice) {
        return pilotage.tableauDeBord(exercice);
    }
}
