package ma.sarf.documents;

import java.time.Instant;

/**
 * Pièce justificative telle qu'affichée, sans son contenu.
 *
 * @param empreinte SHA-256 du fichier, qui prouve qu'il n'a pas changé depuis son dépôt
 */
public record DocumentVue(long id, NatureDocument nature, String nomFichier, String typeContenu, int taille,
        String empreinte, String deposePar, Instant deposeLe) {
}
