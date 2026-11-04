package ma.sarf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sarf : prototype pédagogique de la chaîne d'exécution de la dépense publique de l'État
 * (engagement, liquidation, ordonnancement, paiement), fondé sur le décret royal n° 330-66
 * (RGCP), le décret n° 2-07-1235 (contrôle des dépenses) et le décret n° 2-16-344 (délais de
 * paiement et intérêts moratoires).
 */
@SpringBootApplication
public class SarfApplication {

    public static void main(String[] args) {
        SpringApplication.run(SarfApplication.class, args);
    }
}
