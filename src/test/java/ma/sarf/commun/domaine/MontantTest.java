package ma.sarf.commun.domaine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MontantTest {

    @Test
    @DisplayName("Un montant est arrondi au centime et ne souffre pas des erreurs du double")
    void arrondiAuCentime() {
        assertThat(Montant.de("0.10").plus(Montant.de("0.20"))).isEqualTo(Montant.de("0.30"));
        assertThat(Montant.de("10.005").valeur()).isEqualByComparingTo("10.01");
    }

    @Test
    @DisplayName("Un montant ne peut pas être négatif")
    void jamaisNegatif() {
        assertThatThrownBy(() -> Montant.de(10).moins(Montant.de(11))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un pourcentage s'applique en points et s'arrondit au centime")
    void pourcentage() {
        assertThat(Montant.de("123456.78").pourcentage(BigDecimal.ONE)).isEqualTo(Montant.de("1234.57"));
    }
}
