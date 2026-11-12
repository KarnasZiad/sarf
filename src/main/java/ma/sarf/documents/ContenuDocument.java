package ma.sarf.documents;

/** Fichier à télécharger. */
public record ContenuDocument(String nomFichier, String typeContenu, byte[] contenu) {
}
