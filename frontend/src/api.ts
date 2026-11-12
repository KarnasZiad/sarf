import { jeton } from './auth';

/** Refus de l'API. {@code reference} porte l'article du texte quand une règle métier est en cause. */
export class ErreurApi extends Error {
  constructor(
    message: string,
    readonly statut: number,
    readonly code?: string,
    readonly reference?: string,
  ) {
    super(message);
  }
}

const MESSAGES_PAR_STATUT: Record<number, string> = {
  400: 'Certaines informations sont manquantes ou invalides.',
  401: 'Votre session a expiré. Rechargez la page pour vous reconnecter.',
  403: "Votre profil n'est pas autorisé à effectuer cette action.",
  404: "Cet élément n'existe pas ou ne vous est pas accessible.",
  409: "Ce dossier vient d'être modifié par quelqu'un d'autre. Rechargez la page.",
};

async function appeler(chemin: string, options: RequestInit = {}): Promise<Response> {
  const entetes = new Headers(options.headers);
  entetes.set('Authorization', `Bearer ${await jeton()}`);
  const reponse = await fetch(chemin, { ...options, headers: entetes });
  if (!reponse.ok) {
    const probleme = await reponse.json().catch(() => null);
    throw new ErreurApi(
      probleme?.detail ?? MESSAGES_PAR_STATUT[reponse.status] ?? 'Le service ne répond pas. Réessayez dans un instant.',
      reponse.status,
      probleme?.code,
      probleme?.reference,
    );
  }
  return reponse;
}

export async function lire<T>(chemin: string): Promise<T> {
  return (await appeler(chemin)).json();
}

export async function envoyer<T>(chemin: string, corps?: unknown): Promise<T> {
  const reponse = await appeler(chemin, {
    method: 'POST',
    headers: corps === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: corps === undefined ? undefined : JSON.stringify(corps),
  });
  return reponse.status === 204 ? (undefined as T) : reponse.json();
}

export async function televerser<T>(chemin: string, formulaire: FormData): Promise<T> {
  return (await appeler(chemin, { method: 'POST', body: formulaire })).json();
}

export async function telecharger(chemin: string, nomFichier: string): Promise<void> {
  const blob = await (await appeler(chemin)).blob();
  const url = URL.createObjectURL(blob);
  const lien = document.createElement('a');
  lien.href = url;
  lien.download = nomFichier;
  lien.click();
  URL.revokeObjectURL(url);
}
