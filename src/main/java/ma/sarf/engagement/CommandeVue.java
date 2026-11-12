package ma.sarf.engagement;

import java.math.BigDecimal;

/** Commande visée, vue par le fournisseur qui peut la facturer. */
public record CommandeVue(long id, int exercice, String objet, String typeCommande, String referencePiece,
        String serviceOrdonnateur, BigDecimal montantPropose, BigDecimal resteALiquider) {
}
