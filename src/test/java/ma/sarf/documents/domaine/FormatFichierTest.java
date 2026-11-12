package ma.sarf.documents.domaine;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FormatFichierTest {

    @Test
    @DisplayName("Le format est reconnu à partir du contenu réel du fichier")
    void formatsAcceptes() {
        assertThat(FormatFichier.detecter("%PDF-1.7 contenu".getBytes(StandardCharsets.US_ASCII)))
                .contains(FormatFichier.PDF);
        assertThat(FormatFichier.detecter(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}))
                .contains(FormatFichier.JPEG);
        assertThat(FormatFichier.detecter(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00}))
                .contains(FormatFichier.PNG);
    }

    @Test
    @DisplayName("Un fichier renommé en .pdf sans en être un est refusé")
    void contenuNonReconnu() {
        assertThat(FormatFichier.detecter("<html>facture.pdf</html>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(FormatFichier.detecter(new byte[] {'%', 'P'})).isEmpty();
    }
}
