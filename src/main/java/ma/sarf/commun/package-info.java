/**
 * Noyau partagé : valeurs du domaine (montants, calendrier, paramètres réglementaires),
 * sécurité, gestion des erreurs. Module ouvert : tous les autres modules peuvent l'utiliser.
 */
@ApplicationModule(type = ApplicationModule.Type.OPEN, displayName = "Commun")
package ma.sarf.commun;

import org.springframework.modulith.ApplicationModule;
