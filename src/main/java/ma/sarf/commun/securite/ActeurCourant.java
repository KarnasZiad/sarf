package ma.sarf.commun.securite;

import java.util.Set;
import java.util.stream.Collectors;
import ma.sarf.commun.domaine.RegleMetierException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Identité de la personne qui agit, lue dans le jeton.
 */
public final class ActeurCourant {

    private ActeurCourant() {
    }

    /**
     * Nom de l'utilisateur authentifié.
     *
     * <p>Refuse aussi tout jeton qui cumule les rôles d'ordonnateur et de comptable : les deux
     * fonctions sont incompatibles (RGCP, art. 4). La règle est vérifiée ici en plus de la
     * configuration de Keycloak, pour ne jamais dépendre d'un seul verrou.
     */
    public static String nom() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !authentification.isAuthenticated()) {
            throw new IllegalStateException("Aucun utilisateur authentifié");
        }
        Set<String> autorites = authentification.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (autorites.contains("ROLE_" + Roles.ORDONNATEUR) && autorites.contains("ROLE_" + Roles.COMPTABLE)) {
            throw new RegleMetierException(
                    "INCOMPATIBILITE_FONCTIONS",
                    "Décret royal n° 330-66 (RGCP), art. 4",
                    "Les fonctions d'ordonnateur et de comptable sont incompatibles : ce compte cumule les deux rôles.");
        }
        return authentification.getName();
    }
}
