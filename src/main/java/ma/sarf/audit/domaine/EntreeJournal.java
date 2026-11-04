package ma.sarf.audit.domaine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Entrée du journal d'audit. Chaque entrée contient l'empreinte de la précédente : modifier ou
 * supprimer une entrée casse la chaîne, ce que {@link #premiereRupture(List)} détecte.
 */
public record EntreeJournal(
        long id,
        Instant horodatage,
        String acteur,
        String action,
        String typeObjet,
        String idObjet,
        String details,
        String empreintePrecedente,
        String empreinte) {

    /** Empreinte de la toute première entrée : aucune entrée ne la précède. */
    public static final String ORIGINE = "0".repeat(64);

    public EntreeJournal {
        Objects.requireNonNull(horodatage);
        Objects.requireNonNull(empreintePrecedente);
        Objects.requireNonNull(empreinte);
    }

    /**
     * Chaque champ est préfixé par sa longueur : deux contenus différents ne peuvent pas produire
     * la même chaîne, quel que soit le texte des détails.
     */
    public static String empreinte(String precedente, Instant horodatage, String acteur, String action,
            String typeObjet, String idObjet, String details) {
        String contenu = Stream.of(precedente, horodatage.toString(), acteur, action, typeObjet, idObjet, details)
                .map(champ -> Objects.toString(champ, ""))
                .map(champ -> champ.length() + ":" + champ)
                .collect(Collectors.joining());
        try {
            byte[] hachage = MessageDigest.getInstance("SHA-256").digest(contenu.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hachage);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    /**
     * Rejoue la chaîne dans l'ordre et renvoie l'identifiant de la première entrée incohérente.
     */
    public static Optional<Long> premiereRupture(List<EntreeJournal> journal) {
        String precedente = ORIGINE;
        for (EntreeJournal entree : journal) {
            String attendue = empreinte(precedente, entree.horodatage(), entree.acteur(), entree.action(),
                    entree.typeObjet(), entree.idObjet(), entree.details());
            if (!entree.empreintePrecedente().equals(precedente) || !entree.empreinte().equals(attendue)) {
                return Optional.of(entree.id());
            }
            precedente = entree.empreinte();
        }
        return Optional.empty();
    }
}
