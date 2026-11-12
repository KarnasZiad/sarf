package ma.sarf.engagement.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.engagement.CompteFournisseurVue;
import ma.sarf.engagement.EngagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rattachement des comptes de connexion aux fournisseurs qu'ils représentent. */
@RestController
@RequestMapping("/api/comptes-fournisseurs")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
class ComptesFournisseursController {

    private final EngagementService engagements;

    ComptesFournisseursController(EngagementService engagements) {
        this.engagements = engagements;
    }

    @GetMapping
    List<CompteFournisseurVue> lister() {
        return engagements.comptesFournisseurs();
    }

    @PostMapping
    CompteFournisseurVue rattacher(@RequestBody @Valid Rattachement demande) {
        return engagements.rattacherCompte(demande.utilisateur(), demande.fournisseurIdentifiant(),
                demande.fournisseurNom(), ActeurCourant.nom());
    }

    record Rattachement(
            @NotBlank @Size(max = 100) String utilisateur,
            @NotBlank @Size(max = 30) String fournisseurIdentifiant,
            @NotBlank @Size(max = 200) String fournisseurNom) {
    }
}
