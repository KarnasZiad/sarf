package ma.sarf.engagement.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import ma.sarf.commun.securite.ActeurCourant;
import ma.sarf.documents.ContenuDocument;
import ma.sarf.documents.DocumentService;
import ma.sarf.documents.DocumentVue;
import ma.sarf.documents.NatureDocument;
import ma.sarf.documents.TypeObjet;
import ma.sarf.engagement.EngagementService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Pièces qui fondent un engagement : bon de commande, marché, contrat. */
@RestController
@RequestMapping("/api/engagements/{id}/documents")
@PreAuthorize("hasAnyRole('ORDONNATEUR', 'COMPTABLE', 'AUDITEUR', 'ADMINISTRATEUR')")
class DocumentsEngagementController {

    private final EngagementService engagements;
    private final DocumentService documents;

    DocumentsEngagementController(EngagementService engagements, DocumentService documents) {
        this.engagements = engagements;
        this.documents = documents;
    }

    @GetMapping
    List<DocumentVue> lister(@PathVariable long id) {
        engagements.consulter(id);
        return documents.lister(TypeObjet.ENGAGEMENT, id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORDONNATEUR')")
    DocumentVue ajouter(@PathVariable long id, @RequestParam NatureDocument nature,
            @RequestParam MultipartFile fichier) {
        engagements.consulter(id);
        try {
            return documents.ajouter(TypeObjet.ENGAGEMENT, id, nature, fichier.getOriginalFilename(), fichier.getBytes(),
                    ActeurCourant.nom());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @GetMapping("/{documentId}/contenu")
    ResponseEntity<byte[]> telecharger(@PathVariable long id, @PathVariable long documentId) {
        ContenuDocument document = documents.contenu(TypeObjet.ENGAGEMENT, id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.typeContenu()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(document.contenu());
    }
}
