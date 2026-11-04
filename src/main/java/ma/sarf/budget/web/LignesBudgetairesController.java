package ma.sarf.budget.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import ma.sarf.budget.BudgetService;
import ma.sarf.budget.LigneBudgetaireVue;
import ma.sarf.budget.domaine.NatureCredit;
import ma.sarf.commun.securite.ActeurCourant;
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
@RequestMapping("/api/lignes-budgetaires")
class LignesBudgetairesController {

    private final BudgetService budget;

    LignesBudgetairesController(BudgetService budget) {
        this.budget = budget;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    LigneBudgetaireVue ouvrir(@RequestBody @Valid OuvertureLigne demande) {
        return budget.ouvrirLigne(demande.exercice(), demande.imputation(), demande.libelle(), demande.nature(),
                demande.creditsOuverts(), ActeurCourant.nom());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<LigneBudgetaireVue> lister(@RequestParam int exercice) {
        return budget.lister(exercice);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    LigneBudgetaireVue consulter(@PathVariable long id) {
        return budget.consulter(id);
    }

    record OuvertureLigne(
            @NotNull Integer exercice,
            @NotBlank String imputation,
            @NotBlank String libelle,
            @NotNull NatureCredit nature,
            @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal creditsOuverts) {
    }
}
