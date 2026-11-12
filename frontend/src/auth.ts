import Keycloak from 'keycloak-js';

export type Role = 'ADMINISTRATEUR' | 'ORDONNATEUR' | 'COMPTABLE' | 'FOURNISSEUR' | 'AUDITEUR';

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8180',
  realm: 'sarf',
  clientId: 'sarf-web',
});

export function demarrerSession(): Promise<boolean> {
  return keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
}

/** Jeton valide pour au moins 30 secondes, renouvelé si nécessaire. */
export async function jeton(): Promise<string> {
  await keycloak.updateToken(30);
  return keycloak.token as string;
}

export interface Utilisateur {
  identifiant: string;
  nomAffiche: string;
  roles: Role[];
}

export function utilisateurConnecte(): Utilisateur {
  const t = keycloak.tokenParsed as { preferred_username?: string; name?: string; roles?: Role[] };
  return {
    identifiant: t.preferred_username ?? '',
    nomAffiche: t.name ?? t.preferred_username ?? '',
    roles: t.roles ?? [],
  };
}

export function seDeconnecter(): void {
  void keycloak.logout({ redirectUri: window.location.origin });
}
