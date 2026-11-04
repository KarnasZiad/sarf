package ma.sarf.depense.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.depense.DepenseService;
import ma.sarf.depense.TauxTrimestriel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saisie du taux trimestriel. On saisit le taux moyen pondéré des bons du Trésor à 3 mois du
 * trimestre précédent ; le taux des intérêts moratoires en est déduit (décret n° 2-16-344, art. 11).
 */
@RestController
@RequestMapping("/api/taux-interets-moratoires")
class TauxController {

    private final DepenseService depense;

    TauxController(DepenseService depense) {
        this.depense = depense;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<TauxTrimestriel> lister() {
        return depense.taux();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    TauxTrimestriel enregistrer(@RequestBody @Valid Saisie saisie) {
        return depense.enregistrerTaux(saisie.annee(), saisie.trimestre(), saisie.tauxMoyenBonsDuTresor(),
                saisie.source(), ActeurCourant.nom());
    }

    record Saisie(
            @NotNull @Min(2000) @Max(2100) Integer annee,
            @NotNull @Min(1) @Max(4) Integer trimestre,
            @NotNull @DecimalMin("0.00") @DecimalMax("50.00") BigDecimal tauxMoyenBonsDuTresor,
            @NotBlank @Size(max = 300) String source) {
    }
}
