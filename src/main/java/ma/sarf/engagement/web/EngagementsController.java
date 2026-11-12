package ma.sarf.engagement.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import ma.sarf.commun.domaine.Montant;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.engagement.CommandeVue;
import ma.sarf.engagement.EngagementService;
import ma.sarf.engagement.EngagementVue;
import ma.sarf.engagement.PreControleVue;
import ma.sarf.engagement.domaine.LigneDetail;
import ma.sarf.engagement.domaine.Proposition;
import ma.sarf.engagement.domaine.StatutEngagement;
import ma.sarf.engagement.domaine.TypeCommande;
import ma.sarf.engagement.domaine.TypeDecision;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/engagements")
class EngagementsController {

    private final EngagementService engagements;

    EngagementsController(EngagementService engagements) {
        this.engagements = engagements;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORDONNATEUR')")
    EngagementVue soumettre(@RequestBody @Valid NouvelEngagement demande) {
        return engagements.soumettre(demande.ligneBudgetaireId(), demande.serviceOrdonnateur(),
                demande.proposition().versDomaine(), ActeurCourant.nom());
    }

    /** Les contrôles du comptable, avant l'envoi : rien n'est enregistré. */
    @PostMapping("/pre-controle")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    PreControleVue preControler(@RequestBody @Valid NouvelEngagement demande) {
        return engagements.preControler(demande.ligneBudgetaireId(), demande.serviceOrdonnateur(),
                demande.proposition().versDomaine(), ActeurCourant.nom());
    }

    /** Commandes visées que le fournisseur connecté peut facturer. */
    @GetMapping("/commandes")
    @PreAuthorize("hasRole('FOURNISSEUR')")
    List<CommandeVue> commandes() {
        return engagements.commandesDe(ActeurCourant.nom());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORDONNATEUR', 'COMPTABLE', 'AUDITEUR', 'ADMINISTRATEUR')")
    List<EngagementVue> lister(@RequestParam(required = false) StatutEngagement statut) {
        return engagements.lister(statut);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORDONNATEUR', 'COMPTABLE', 'AUDITEUR', 'ADMINISTRATEUR')")
    EngagementVue consulter(@PathVariable long id) {
        return engagements.consulter(id);
    }

    @PostMapping("/{id}/decision")
    @PreAuthorize("hasRole('COMPTABLE')")
    EngagementVue decider(@PathVariable long id, @RequestBody @Valid Decision decision) {
        return engagements.decider(id, decision.type(),
                decision.observations() == null ? List.of() : decision.observations(), ActeurCourant.nom());
    }

    @PostMapping("/{id}/regularisation")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    EngagementVue regulariser(@PathVariable long id, @RequestBody @Valid PropositionDto proposition) {
        return engagements.regulariser(id, proposition.versDomaine(), ActeurCourant.nom());
    }

    record NouvelEngagement(
            @NotNull Long ligneBudgetaireId,
            @NotBlank String serviceOrdonnateur,
            @NotNull @Valid PropositionDto proposition) {
    }

    record PropositionDto(
            @NotBlank @Size(max = 300) String objet,
            @NotNull TypeCommande typeCommande,
            @Size(max = 60) String referencePiece,
            @NotBlank @Size(max = 200) String fournisseurNom,
            @NotBlank @Size(max = 30) String fournisseurIdentifiant,
            @NotEmpty List<@Valid LigneDto> lignes,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal montantPropose) {

        Proposition versDomaine() {
            return new Proposition(objet, typeCommande, referencePiece, fournisseurNom, fournisseurIdentifiant,
                    lignes.stream().map(LigneDto::versDomaine).toList(), Montant.de(montantPropose));
        }
    }

    record LigneDto(
            @NotBlank @Size(max = 200) String designation,
            @NotNull @DecimalMin("0.001") BigDecimal quantite,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal prixUnitaire) {

        LigneDetail versDomaine() {
            return new LigneDetail(designation, quantite, Montant.de(prixUnitaire));
        }
    }

    record Decision(@NotNull TypeDecision type, List<@Size(max = 1000) String> observations) {
    }
}
