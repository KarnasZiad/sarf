package ma.sarf.depense.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.commun.securite.Roles;
import ma.sarf.depense.DepenseService;
import ma.sarf.depense.DepenseService.DecisionDuComptable;
import ma.sarf.depense.DossierVue;
import ma.sarf.depense.domaine.InteretsMoratoires;
import ma.sarf.depense.domaine.ModeReglement;
import ma.sarf.depense.domaine.MotifSuspension;
import ma.sarf.depense.domaine.StatutDossier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossiers")
class DossiersController {

    private static final String AGENTS = "hasAnyRole('ORDONNATEUR', 'COMPTABLE', 'AUDITEUR', 'ADMINISTRATEUR')";

    private final DepenseService depense;

    DossiersController(DepenseService depense) {
        this.depense = depense;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FOURNISSEUR', 'ORDONNATEUR')")
    DossierVue deposer(@RequestBody @Valid DepotFacture depot) {
        return depense.deposer(depot.engagementId(), depot.numeroFacture(), depot.montantFacture(),
                depot.retenueGarantie(), depot.rib(), ActeurCourant.nom());
    }

    /** Un fournisseur ne voit que les factures qu'il a déposées. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<DossierVue> lister(@RequestParam(required = false) StatutDossier statut, Authentication authentification) {
        List<DossierVue> dossiers = depense.lister(statut);
        if (estSeulementFournisseur(authentification)) {
            String nom = ActeurCourant.nom();
            return dossiers.stream().filter(d -> nom.equals(d.deposant())).toList();
        }
        return dossiers;
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    DossierVue consulter(@PathVariable long id, Authentication authentification) {
        DossierVue dossier = depense.consulter(id);
        if (estSeulementFournisseur(authentification) && !ActeurCourant.nom().equals(dossier.deposant())) {
            throw new ObjetIntrouvableException("Dossier de paiement", id);
        }
        return dossier;
    }

    @GetMapping("/{id}/interets")
    @PreAuthorize(AGENTS)
    InteretsMoratoires.Calcul interets(@PathVariable long id) {
        return depense.detailDesInterets(id);
    }

    @PostMapping("/{id}/service-fait")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue certifierServiceFait(@PathVariable long id) {
        return depense.certifierServiceFait(id, ActeurCourant.nom());
    }

    @PostMapping("/{id}/pieces-manquantes")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue signalerPiecesManquantes(@PathVariable long id, @RequestBody @Valid Motif motif) {
        return depense.signalerPiecesManquantes(id, motif.motif(), ActeurCourant.nom());
    }

    @PostMapping("/{id}/pieces-recues")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue recevoirPieces(@PathVariable long id) {
        return depense.recevoirPieces(id, ActeurCourant.nom());
    }

    @PostMapping("/{id}/liquidation")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue liquider(@PathVariable long id, @RequestBody @Valid Liquidation liquidation) {
        return depense.liquider(id, liquidation.montant(), ActeurCourant.nom());
    }

    @PostMapping("/{id}/ordonnancement")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue ordonnancer(@PathVariable long id) {
        return depense.ordonnancer(id, ActeurCourant.nom());
    }

    @PostMapping("/{id}/controle")
    @PreAuthorize("hasRole('COMPTABLE')")
    DossierVue controler(@PathVariable long id, @RequestBody @Valid Controle controle) {
        return depense.controler(id, controle.decision(), controle.motifs(), controle.note(),
                controle.modeReglement() == null ? ModeReglement.VIREMENT : controle.modeReglement(), ActeurCourant.nom());
    }

    @PostMapping("/{id}/regularisation")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue regulariser(@PathVariable long id) {
        return depense.regulariser(id, ActeurCourant.nom());
    }

    @PostMapping("/{id}/requisition")
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DossierVue requerir(@PathVariable long id, @RequestBody @Valid Motif motif) {
        return depense.requerir(id, motif.motif(), ActeurCourant.nom());
    }

    @PostMapping("/{id}/paiement-sur-requisition")
    @PreAuthorize("hasRole('COMPTABLE')")
    DossierVue payerSurRequisition(@PathVariable long id, @RequestBody(required = false) Reglement reglement) {
        ModeReglement mode = reglement == null || reglement.modeReglement() == null
                ? ModeReglement.VIREMENT : reglement.modeReglement();
        return depense.payerSurRequisition(id, mode, ActeurCourant.nom());
    }

    private static boolean estSeulementFournisseur(Authentication authentification) {
        var autorites = authentification.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        return autorites.contains("ROLE_" + Roles.FOURNISSEUR)
                && autorites.stream().noneMatch(a -> a.equals("ROLE_" + Roles.ORDONNATEUR)
                        || a.equals("ROLE_" + Roles.COMPTABLE) || a.equals("ROLE_" + Roles.AUDITEUR)
                        || a.equals("ROLE_" + Roles.ADMINISTRATEUR));
    }

    record DepotFacture(
            @NotNull Long engagementId,
            @NotBlank @Size(max = 60) String numeroFacture,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal montantFacture,
            @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal retenueGarantie,
            @Size(max = 40) String rib) {
    }

    record Motif(@NotBlank @Size(max = 1000) String motif) {
    }

    record Liquidation(@NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal montant) {
    }

    record Controle(@NotNull DecisionDuComptable decision, Set<MotifSuspension> motifs, @Size(max = 2000) String note,
            ModeReglement modeReglement) {
    }

    record Reglement(ModeReglement modeReglement) {
    }
}
