package ma.sarf.commun.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import ma.sarf.commun.config.JoursFeries;
import ma.sarf.commun.config.JoursFeries.JourFerie;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jours fériés : les fêtes religieuses (Aïd Al Fitr, Aïd Al Adha, 1er Moharram, Aïd Al Mawlid) sont
 * ajoutées chaque année par l'administrateur, car leur date suit le calendrier hégirien.
 */
@RestController
@RequestMapping("/api/jours-feries")
class JoursFeriesController {

    private final JoursFeries joursFeries;

    JoursFeriesController(JoursFeries joursFeries) {
        this.joursFeries = joursFeries;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<JourFerie> lister(@RequestParam int annee) {
        return joursFeries.lister(annee);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    @Transactional
    void enregistrer(@RequestBody @Valid Saisie saisie) {
        joursFeries.enregistrer(new JourFerie(saisie.jour(), saisie.libelle().strip()));
    }

    record Saisie(@NotNull LocalDate jour, @NotBlank @Size(max = 120) String libelle) {
    }
}
