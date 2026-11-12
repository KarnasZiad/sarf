package ma.sarf.documents.domaine;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formats acceptés pour les pièces justificatives. Le format est déduit du contenu réel du fichier :
 * ni son extension ni le type annoncé par le navigateur ne sont fiables.
 */
public enum FormatFichier {

    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String typeContenu;
    private final byte[] signature;

    FormatFichier(String typeContenu, byte[] signature) {
        this.typeContenu = typeContenu;
        this.signature = signature;
    }

    public String typeContenu() {
        return typeContenu;
    }

    public static Optional<FormatFichier> detecter(byte[] contenu) {
        return Arrays.stream(values()).filter(format -> format.correspond(contenu)).findFirst();
    }

    private boolean correspond(byte[] contenu) {
        return contenu.length >= signature.length
                && Arrays.equals(contenu, 0, signature.length, signature, 0, signature.length);
    }
}
