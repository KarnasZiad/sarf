package ma.sarf.engagement.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.engagement.EngagementService;
import ma.sarf.engagement.ServiceOrdonnateurVue;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/services-ordonnateurs")
class ServicesOrdonnateursController {

    private final EngagementService engagements;

    ServicesOrdonnateursController(EngagementService engagements) {
        this.engagements = engagements;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<ServiceOrdonnateurVue> lister() {
        return engagements.servicesOrdonnateurs();
    }

    /** Accréditation d'un ordonnateur auprès du comptable assignataire (RGCP, art. 5). */
    @PostMapping("/{code}/accreditations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    void accrediter(@PathVariable String code, @RequestBody @Valid Accreditation accreditation) {
        engagements.accrediter(accreditation.utilisateur(), code, ActeurCourant.nom());
    }

    record Accreditation(@NotBlank String utilisateur) {
    }
}
