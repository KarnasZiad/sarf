package ma.sarf.documents;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import ma.sarf.commun.domaine.ObjetIntrouvableException;
import ma.sarf.documents.domaine.FormatFichier;
import ma.sarf.documents.persistance.Documents;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface publique du module documents : dépôt et consultation des pièces justificatives. Le
 * contrôle d'accès à l'objet concerné reste à la charge du module qui expose la pièce.
 */
@Service
public class DocumentService {

    /** Limite alignée sur {@code spring.servlet.multipart.max-file-size}. */
    static final int TAILLE_MAXIMALE = 10 * 1024 * 1024;
    private static final int LONGUEUR_NOM_MAXIMALE = 255;

    private final Documents documents;
    private final Clock horloge;
    private final ApplicationEventPublisher evenements;

    DocumentService(Documents documents, Clock horloge, ApplicationEventPublisher evenements) {
        this.documents = documents;
        this.horloge = horloge;
        this.evenements = evenements;
    }

    @Transactional
    public DocumentVue ajouter(TypeObjet type, long idObjet, NatureDocument nature, String nomFichier, byte[] contenu,
            String acteur) {
        if (contenu.length == 0 || contenu.length > TAILLE_MAXIMALE) {
            throw new IllegalArgumentException("Le fichier doit être non vide et ne pas dépasser 10 Mo.");
        }
        FormatFichier format = FormatFichier.detecter(contenu)
                .orElseThrow(() -> new IllegalArgumentException("Seuls les fichiers PDF, PNG et JPEG sont acceptés."));
        String nom = nomNettoye(nomFichier);
        String empreinte = empreinte(contenu);
        long id = documents.inserer(type, idObjet, nature, nom, format.typeContenu(), empreinte, contenu, acteur,
                Instant.now(horloge).truncatedTo(ChronoUnit.MICROS));
        evenements.publishEvent(new EvenementDocument(type, idObjet, acteur,
                nature + " « " + nom + " », SHA-256 " + empreinte));
        return lister(type, idObjet).stream().filter(d -> d.id() == id).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<DocumentVue> lister(TypeObjet type, long idObjet) {
        return documents.lister(type, idObjet);
    }

    @Transactional(readOnly = true)
    public ContenuDocument contenu(TypeObjet type, long idObjet, long id) {
        return documents.contenu(type, idObjet, id).orElseThrow(() -> new ObjetIntrouvableException("Document", id));
    }

    /** Retire tout chemin envoyé par le navigateur et les caractères de contrôle. */
    private static String nomNettoye(String nomFichier) {
        String nom = nomFichier == null ? "" : nomFichier.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (nom.isEmpty()) {
            return "piece";
        }
        return nom.length() > LONGUEUR_NOM_MAXIMALE ? nom.substring(nom.length() - LONGUEUR_NOM_MAXIMALE) : nom;
    }

    private static String empreinte(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }
}
