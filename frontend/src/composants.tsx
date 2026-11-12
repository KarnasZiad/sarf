import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createContext, useContext, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ErreurApi, lire, telecharger, televerser } from './api';
import type { Role, Utilisateur } from './auth';
import { date, dateHeure, joursEntre, LIBELLES_DOCUMENT, LIBELLES_STATUT_DOSSIER, LIBELLES_STATUT_ENGAGEMENT, pluriel } from './format';
import type { DocumentJoint, NatureDocument } from './types';

export const Session = createContext<Utilisateur | null>(null);

export function useUtilisateur(): Utilisateur & { a: (role: Role) => boolean } {
  const utilisateur = useContext(Session) as Utilisateur;
  return { ...utilisateur, a: (role: Role) => utilisateur.roles.includes(role) };
}

export function useLire<T>(chemin: string, actif = true) {
  return useQuery<T, ErreurApi>({ queryKey: [chemin], queryFn: () => lire<T>(chemin), enabled: actif });
}

/** Date de référence : l'horloge de démonstration si elle existe, sinon la date du jour. */
export function useAujourdhui(): string {
  const { data } = useQuery<{ date: string }>({
    queryKey: ['/api/demo/horloge'],
    queryFn: () => lire<{ date: string }>('/api/demo/horloge'),
    retry: false,
  });
  return data?.date ?? new Date().toISOString().slice(0, 10);
}

/** Action métier : affiche le refus motivé, et recharge les données après succès. */
export function useAction<V, R = unknown>(action: (variables: V) => Promise<R>, apres?: (resultat: R) => void) {
  const client = useQueryClient();
  return useMutation<R, ErreurApi, V>({
    mutationFn: action,
    onSuccess: async (resultat) => {
      await client.invalidateQueries();
      apres?.(resultat);
    },
  });
}

export function Refus({ erreur }: { erreur: ErreurApi | Error | null | undefined }) {
  if (!erreur) return null;
  const refus = erreur as ErreurApi;
  return (
    <div className="message critique" role="alert">
      <span>{erreur.message}</span>
      {refus.reference && <span className="article">Fondement : {refus.reference}</span>}
    </div>
  );
}

export function Chargement({ erreur, chargement }: { erreur?: Error | null; chargement?: boolean }) {
  if (erreur) return <Refus erreur={erreur} />;
  if (chargement) return <p className="sourd">Chargement…</p>;
  return null;
}

export function Entete({ fil, titre, children }: { fil?: ReactNode; titre: ReactNode; children?: ReactNode }) {
  return (
    <div className="entete">
      <div>
        {fil && <div className="fil">{fil}</div>}
        <h1>{titre}</h1>
      </div>
      {children && <div className="actions-entete sans-impression">{children}</div>}
    </div>
  );
}

export function Carte({ titre, droite, children }: { titre?: ReactNode; droite?: ReactNode; children: ReactNode }) {
  return (
    <section className="carte">
      {titre && (
        <div className="carte-tete">
          <h2>{titre}</h2>
          {droite}
        </div>
      )}
      {children}
    </section>
  );
}

export function Champ({ libelle, aide, children }: { libelle: string; aide?: string; children: ReactNode }) {
  return (
    <label className="champ">
      {libelle}
      {children}
      {aide && <span className="aide">{aide}</span>}
    </label>
  );
}

const TONS_DOSSIER: Record<string, string> = {
  DEPOSE: 'neutre', SERVICE_FAIT: 'info', LIQUIDE: 'info', ORDONNANCE: 'attention', SUSPENDU: 'critique', REQUISITIONNE: 'attention', PAYE: 'ok',
};
const TONS_ENGAGEMENT: Record<string, string> = { SOUMIS: 'attention', VISE: 'ok', SUSPENDU: 'critique', REFUSE: 'critique' };

export const BadgeDossier = ({ statut }: { statut: string }) => (
  <span className={`badge ${TONS_DOSSIER[statut]}`}>{LIBELLES_STATUT_DOSSIER[statut]}</span>
);
export const BadgeEngagement = ({ statut }: { statut: string }) => (
  <span className={`badge ${TONS_ENGAGEMENT[statut]}`}>{LIBELLES_STATUT_ENGAGEMENT[statut]}</span>
);

/** Temps restant avant une échéance, avec un état visible sans dépendre de la seule couleur. */
export function Echeance({ limite, aujourdhui }: { limite: string | null | undefined; aujourdhui: string }) {
  if (!limite) return <span className="sourd">—</span>;
  const restant = joursEntre(aujourdhui, limite);
  const ton = restant < 0 ? 'critique' : restant <= 5 ? 'attention' : 'neutre';
  const texte = restant < 0 ? `En retard de ${pluriel(-restant, 'jour')}` : restant === 0 ? "Aujourd'hui" : `Reste ${pluriel(restant, 'jour')}`;
  return (
    <span className={`badge ${ton}`} title={`Échéance : ${date(limite)}`}>
      {texte}
    </span>
  );
}

const NATURES: NatureDocument[] = ['FACTURE', 'ATTESTATION_SERVICE_FAIT', 'BON_DE_COMMANDE', 'MARCHE', 'CONTRAT', 'RIB', 'AUTRE'];

/** Pièces justificatives d'un engagement ou d'un dossier. */
export function Pieces({ racine, peutAjouter, natureParDefaut }: { racine: string; peutAjouter: boolean; natureParDefaut: NatureDocument }) {
  const chemin = `${racine}/documents`;
  const { data, error, isLoading } = useLire<DocumentJoint[]>(chemin);
  const [nature, setNature] = useState<NatureDocument>(natureParDefaut);
  const entree = useRef<HTMLInputElement>(null);
  const ajout = useAction((fichier: File) => {
    const formulaire = new FormData();
    formulaire.append('nature', nature);
    formulaire.append('fichier', fichier);
    return televerser<DocumentJoint>(chemin, formulaire);
  }, () => {
    if (entree.current) entree.current.value = '';
  });
  return (
    <Carte titre="Pièces justificatives">
      <Chargement erreur={error} chargement={isLoading} />
      {data?.length === 0 && <p className="sourd petit">Aucune pièce jointe.</p>}
      {data && data.length > 0 && (
        <div className="table-cadre">
          <table>
            <tbody>
              {data.map((piece) => (
                <tr key={piece.id}>
                  <td>
                    <b>{LIBELLES_DOCUMENT[piece.nature]}</b>
                    <div className="petit sourd">{piece.nomFichier} · {Math.max(1, Math.round(piece.taille / 1024))} Ko</div>
                    <div className="petit sourd" title={`SHA-256 : ${piece.empreinte}`}>
                      Déposée par {piece.deposePar}, le {dateHeure(piece.deposeLe)}
                    </div>
                  </td>
                  <td className="droite">
                    <button className="bouton" onClick={() => void telecharger(`${chemin}/${piece.id}/contenu`, piece.nomFichier)}>
                      Ouvrir
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {peutAjouter && (
        <div className="formulaire sans-impression">
          <Champ libelle="Ajouter une pièce" aide="PDF, PNG ou JPEG, 10 Mo au maximum.">
            <select value={nature} onChange={(e) => setNature(e.target.value as NatureDocument)} aria-label="Nature de la pièce">
              {NATURES.map((n) => <option key={n} value={n}>{LIBELLES_DOCUMENT[n]}</option>)}
            </select>
          </Champ>
          <input
            ref={entree}
            type="file"
            aria-label="Fichier à joindre"
            accept="application/pdf,image/png,image/jpeg"
            disabled={ajout.isPending}
            onChange={(e) => {
              const fichier = e.target.files?.[0];
              if (fichier) ajout.mutate(fichier);
            }}
          />
          <Refus erreur={ajout.error} />
        </div>
      )}
    </Carte>
  );
}
