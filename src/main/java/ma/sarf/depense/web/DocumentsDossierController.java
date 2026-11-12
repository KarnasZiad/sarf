package ma.sarf.depense.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.depense.DepenseService;
import ma.sarf.documents.ContenuDocument;
import ma.sarf.documents.DocumentService;
import ma.sarf.documents.DocumentVue;
import ma.sarf.documents.NatureDocument;
import ma.sarf.documents.TypeObjet;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Pièces d'un dossier de paiement. Un fournisseur n'accède qu'aux dossiers qu'il a déposés. */
@RestController
@RequestMapping("/api/dossiers/{id}/documents")
class DocumentsDossierController {

    private final DepenseService depense;
    private final DocumentService documents;

    DocumentsDossierController(DepenseService depense, DocumentService documents) {
        this.depense = depense;
        this.documents = documents;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    List<DocumentVue> lister(@PathVariable long id, Authentication authentification) {
        exigerAcces(id, authentification);
        return documents.lister(TypeObjet.DOSSIER, id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('FOURNISSEUR', 'ORDONNATEUR')")
    DocumentVue ajouter(@PathVariable long id, @RequestParam NatureDocument nature,
            @RequestParam MultipartFile fichier, Authentication authentification) {
        exigerAcces(id, authentification);
        try {
            return documents.ajouter(TypeObjet.DOSSIER, id, nature, fichier.getOriginalFilename(), fichier.getBytes(),
                    ActeurCourant.nom());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @GetMapping("/{documentId}/contenu")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<byte[]> telecharger(@PathVariable long id, @PathVariable long documentId,
            Authentication authentification) {
        exigerAcces(id, authentification);
        ContenuDocument document = documents.contenu(TypeObjet.DOSSIER, id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.typeContenu()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(document.contenu());
    }

    private void exigerAcces(long id, Authentication authentification) {
        var dossier = depense.consulter(id);
        if (DossiersController.estSeulementFournisseur(authentification)
                && !ActeurCourant.nom().equals(dossier.deposant())) {
            throw new ObjetIntrouvableException("Dossier de paiement", id);
        }
    }
}
