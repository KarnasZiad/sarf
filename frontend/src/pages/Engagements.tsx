import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { envoyer, televerser } from '../api';
import { BadgeEngagement, Carte, Champ, Chargement, Echeance, Entete, Pieces, Refus, useAction, useAujourdhui, useLire, useUtilisateur } from '../composants';
import { date, dh, LIBELLES_COMMANDE, LIBELLES_STATUT_ENGAGEMENT } from '../format';
import type { DocumentJoint, Engagement, LigneBudgetaire, NatureDocument, PreControle, ServiceOrdonnateur, TypeCommande, TypeDecision } from '../types';

export function ListeEngagements() {
  const moi = useUtilisateur();
  const navigate = useNavigate();
  const aujourdhui = useAujourdhui();
  const [statut, setStatut] = useState('');
  const [recherche, setRecherche] = useState('');
  const { data, error, isLoading } = useLire<Engagement[]>(`/api/engagements${statut ? `?statut=${statut}` : ''}`);
  const filtre = recherche.toLowerCase();
  const liste = (data ?? []).filter((e) => `${e.objet} ${e.fournisseurNom} ${e.imputation}`.toLowerCase().includes(filtre));
  return (
    <>
      <Entete titre="Engagements">{moi.a('ORDONNATEUR') && <Link className="bouton primaire" to="/engagements/nouveau">Nouvel engagement</Link>}</Entete>
      <Carte>
        <div className="ligne-champs">
          <Champ libelle="Rechercher"><input value={recherche} onChange={(e) => setRecherche(e.target.value)} placeholder="Objet, fournisseur, imputation" /></Champ>
          <Champ libelle="Statut">
            <select value={statut} onChange={(e) => setStatut(e.target.value)}>
              <option value="">Tous</option>
              {Object.entries(LIBELLES_STATUT_ENGAGEMENT).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Champ>
        </div>
        <Chargement erreur={error} chargement={isLoading} />
        {data && liste.length === 0 && <p className="sourd">Aucun engagement ne correspond.</p>}
        {liste.length > 0 && (
          <div className="table-cadre">
            <table>
              <thead><tr><th>N°</th><th>Objet</th><th>Fournisseur</th><th>Imputation</th><th className="droite">Montant</th><th>Statut</th><th>Visa</th></tr></thead>
              <tbody>
                {liste.map((e) => (
                  <tr key={e.id} className="cliquable" onClick={() => navigate(`/engagements/${e.id}`)}>
                    <td className="nombre"><Link to={`/engagements/${e.id}`} onClick={(ev) => ev.stopPropagation()}>{e.id}</Link></td>
                    <td>{e.objet}</td>
                    <td>{e.fournisseurNom}</td>
                    <td className="mono">{e.imputation}</td>
                    <td className="droite nombre">{dh(e.montantPropose)}</td>
                    <td><BadgeEngagement statut={e.statut} /></td>
                    <td>{e.statut === 'SOUMIS' ? <Echeance limite={e.echeanceVisa} aujourdhui={aujourdhui} /> : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Carte>
    </>
  );
}

interface LigneSaisie { designation: string; quantite: string; prixUnitaire: string }
export interface PropositionSaisie {
  objet: string;
  typeCommande: TypeCommande;
  referencePiece: string;
  fournisseurNom: string;
  fournisseurIdentifiant: string;
  lignes: LigneSaisie[];
}

const LIGNE_VIDE: LigneSaisie = { designation: '', quantite: '1', prixUnitaire: '' };
const PROPOSITION_VIDE: PropositionSaisie = { objet: '', typeCommande: 'BON_DE_COMMANDE', referencePiece: '', fournisseurNom: '', fournisseurIdentifiant: '', lignes: [LIGNE_VIDE] };

function totalDes(lignes: LigneSaisie[]): number {
  const centimes = lignes.reduce((somme, l) => somme + Math.round(Number(l.quantite || 0) * Number(l.prixUnitaire || 0) * 100), 0);
  return centimes / 100;
}

function corpsProposition(p: PropositionSaisie) {
  return {
    objet: p.objet,
    typeCommande: p.typeCommande,
    referencePiece: p.referencePiece || null,
    fournisseurNom: p.fournisseurNom,
    fournisseurIdentifiant: p.fournisseurIdentifiant,
    lignes: p.lignes.map((l) => ({ designation: l.designation, quantite: Number(l.quantite), prixUnitaire: Number(l.prixUnitaire) })),
    montantPropose: totalDes(p.lignes),
  };
}

function propositionComplete(p: PropositionSaisie): boolean {
  return !!p.objet.trim() && !!p.fournisseurNom.trim() && !!p.fournisseurIdentifiant.trim()
    && p.lignes.every((l) => l.designation.trim() && Number(l.quantite) > 0 && l.prixUnitaire !== '' && Number(l.prixUnitaire) >= 0)
    && totalDes(p.lignes) > 0;
}

/** Formulaire commun à la création et à la correction, avec le pré-contrôle en direct. */
function FormulaireProposition({ initial, ligneFixe, serviceFixe, libelleEnvoi, onEnvoyer, enCours, erreur }: {
  initial: PropositionSaisie;
  ligneFixe?: number;
  serviceFixe?: string;
  libelleEnvoi: string;
  onEnvoyer: (proposition: ReturnType<typeof corpsProposition>, cible: { ligneId: number; service: string }, piece: File | null, nature: NatureDocument) => void;
  enCours: boolean;
  erreur: Error | null;
}) {
  const aujourdhui = useAujourdhui();
  const exercice = Number(aujourdhui.slice(0, 4));
  const lignesBudget = useLire<LigneBudgetaire[]>(`/api/lignes-budgetaires?exercice=${exercice}`);
  const services = useLire<ServiceOrdonnateur[]>('/api/services-ordonnateurs');
  const [ligneId, setLigneId] = useState<number | ''>(ligneFixe ?? '');
  const [service, setService] = useState(serviceFixe ?? '');
  const [p, setP] = useState(initial);
  const [piece, setPiece] = useState<File | null>(null);
  const [controle, setControle] = useState<PreControle | null>(null);
  const [erreurControle, setErreurControle] = useState<Error | null>(null);

  const total = totalDes(p.lignes);
  const pret = ligneId !== '' && !!service && propositionComplete(p);
  const cleControle = useMemo(() => JSON.stringify({ ligneId, service, p }), [ligneId, service, p]);
  const natureDePiece: NatureDocument = p.typeCommande === 'MARCHE' ? 'MARCHE' : p.typeCommande === 'CONTRAT_DROIT_COMMUN' ? 'CONTRAT' : 'BON_DE_COMMANDE';

  useEffect(() => {
    if (!pret) {
      setControle(null);
      return;
    }
    let actuel = true;
    const minuterie = setTimeout(() => {
      envoyer<PreControle>('/api/engagements/pre-controle', { ligneBudgetaireId: ligneId, serviceOrdonnateur: service, proposition: corpsProposition(p) })
        .then((resultat) => { if (actuel) { setControle(resultat); setErreurControle(null); } })
        .catch((e: Error) => { if (actuel) setErreurControle(e); });
    }, 350);
    return () => { actuel = false; clearTimeout(minuterie); };
    // La clé sérialisée résume ligneId, service et p.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cleControle, pret]);

  const majLigne = (i: number, champ: keyof LigneSaisie, valeur: string) =>
    setP({ ...p, lignes: p.lignes.map((l, j) => (j === i ? { ...l, [champ]: valeur } : l)) });
  const ligneChoisie = lignesBudget.data?.find((l) => l.id === ligneId);

  const soumettre = (e: FormEvent) => {
    e.preventDefault();
    if (pret) onEnvoyer(corpsProposition(p), { ligneId: ligneId as number, service }, piece, natureDePiece);
  };

  return (
    <form className="deux-colonnes" onSubmit={soumettre}>
      <div className="pile">
        <Carte titre="Imputation">
          <div className="ligne-champs">
            <Champ libelle="Ligne budgétaire">
              <select required value={ligneId} disabled={ligneFixe !== undefined} onChange={(e) => setLigneId(e.target.value ? Number(e.target.value) : '')}>
                <option value="">Choisir une ligne</option>
                {lignesBudget.data?.map((l) => <option key={l.id} value={l.id}>{l.imputation} · {l.libelle}</option>)}
              </select>
            </Champ>
            <Champ libelle="Service ordonnateur">
              <select required value={service} disabled={serviceFixe !== undefined} onChange={(e) => setService(e.target.value)}>
                <option value="">Choisir un service</option>
                {services.data?.map((s) => <option key={s.code} value={s.code}>{s.libelle}</option>)}
              </select>
            </Champ>
          </div>
          {ligneChoisie && <p className="petit sourd">Disponible sur cette ligne : <b className="nombre">{dh(ligneChoisie.disponibleAEngager)}</b></p>}
        </Carte>
        <Carte titre="Objet de la dépense">
          <Champ libelle="Objet"><input required maxLength={300} value={p.objet} onChange={(e) => setP({ ...p, objet: e.target.value })} /></Champ>
          <div className="ligne-champs">
            <Champ libelle="Type de commande">
              <select value={p.typeCommande} onChange={(e) => setP({ ...p, typeCommande: e.target.value as TypeCommande })}>
                {Object.entries(LIBELLES_COMMANDE).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
            </Champ>
            <Champ libelle="Référence de la pièce"><input maxLength={60} value={p.referencePiece} onChange={(e) => setP({ ...p, referencePiece: e.target.value })} placeholder="BC-2026-014" /></Champ>
          </div>
          <div className="ligne-champs">
            <Champ libelle="Fournisseur"><input required maxLength={200} value={p.fournisseurNom} onChange={(e) => setP({ ...p, fournisseurNom: e.target.value })} /></Champ>
            <Champ libelle="Identifiant du fournisseur (ICE)"><input required maxLength={30} value={p.fournisseurIdentifiant} onChange={(e) => setP({ ...p, fournisseurIdentifiant: e.target.value })} /></Champ>
          </div>
        </Carte>
        <Carte titre="Détail" droite={<b className="nombre">{dh(total)}</b>}>
          {p.lignes.map((l, i) => (
            <div className="ligne-champs" key={i}>
              <Champ libelle="Désignation"><input required maxLength={200} value={l.designation} onChange={(e) => majLigne(i, 'designation', e.target.value)} /></Champ>
              <Champ libelle="Quantité"><input required type="number" min="0.001" step="0.001" value={l.quantite} onChange={(e) => majLigne(i, 'quantite', e.target.value)} /></Champ>
              <Champ libelle="Prix unitaire (DH)"><input required type="number" min="0" step="0.01" value={l.prixUnitaire} onChange={(e) => majLigne(i, 'prixUnitaire', e.target.value)} /></Champ>
            </div>
          ))}
          <div className="rangee-boutons">
            <button type="button" className="bouton" onClick={() => setP({ ...p, lignes: [...p.lignes, LIGNE_VIDE] })}>Ajouter une ligne</button>
            {p.lignes.length > 1 && <button type="button" className="bouton" onClick={() => setP({ ...p, lignes: p.lignes.slice(0, -1) })}>Retirer la dernière</button>}
          </div>
        </Carte>
      </div>
      <div className="pile">
        <Carte titre="Pré-contrôle">
          <p className="petit sourd">Les contrôles que le comptable appliquera, avant tout envoi.</p>
          {!pret && <p className="sourd">Complétez le formulaire pour lancer le contrôle.</p>}
          <Refus erreur={erreurControle} />
          {pret && controle && (
            <>
              <dl className="liste-defs">
                <div><dt>Montant proposé</dt><dd className="nombre">{dh(total)}</dd></div>
                <div><dt>Somme à valoir (1 %)</dt><dd className="nombre">{dh(controle.sommeAValoir)}</dd></div>
                <div><dt>À réserver au visa</dt><dd className="nombre">{dh(controle.montantAEngager)}</dd></div>
                <div><dt>Réponse du comptable avant le</dt><dd>{date(controle.echeanceVisa)}</dd></div>
              </dl>
              <p className="petit">Contrôle de régularité : <b>{controle.controleDeRegulariteRequis ? 'requis' : 'non requis'}</b></p>
              {controle.anomalies.length === 0 && <div className="message ok">Aucune anomalie : le dossier peut être envoyé.</div>}
              {controle.anomalies.map((a) => (
                <div className="message attention" key={a.code}>
                  <span>{a.message}</span>
                  <span className="article">{a.reference}</span>
                </div>
              ))}
            </>
          )}
        </Carte>
        <Carte titre="Pièce de l'engagement">
          <Champ libelle="Bon de commande, marché ou contrat" aide="PDF, PNG ou JPEG, 10 Mo au maximum.">
            <input type="file" accept="application/pdf,image/png,image/jpeg" onChange={(e) => setPiece(e.target.files?.[0] ?? null)} />
          </Champ>
        </Carte>
        <Refus erreur={erreur} />
        <button className="bouton primaire" disabled={!pret || enCours}>{enCours ? 'Envoi…' : libelleEnvoi}</button>
      </div>
    </form>
  );
}

async function joindre(id: number, piece: File | null, nature: NatureDocument): Promise<void> {
  if (!piece) return;
  const formulaire = new FormData();
  formulaire.append('nature', nature);
  formulaire.append('fichier', piece);
  await televerser<DocumentJoint>(`/api/engagements/${id}/documents`, formulaire);
}

export function NouvelEngagement() {
  const navigate = useNavigate();
  const envoi = useAction(async ({ proposition, cible, piece, nature }: { proposition: unknown; cible: { ligneId: number; service: string }; piece: File | null; nature: NatureDocument }) => {
    const cree = await envoyer<Engagement>('/api/engagements', { ligneBudgetaireId: cible.ligneId, serviceOrdonnateur: cible.service, proposition });
    await joindre(cree.id, piece, nature);
    return cree;
  }, (cree) => navigate(`/engagements/${cree.id}`));
  return (
    <>
      <Entete fil={<Link to="/engagements">Engagements</Link>} titre="Nouvel engagement" />
      <FormulaireProposition
        initial={PROPOSITION_VIDE}
        libelleEnvoi="Envoyer au comptable"
        enCours={envoi.isPending}
        erreur={envoi.error}
        onEnvoyer={(proposition, cible, piece, nature) => envoi.mutate({ proposition, cible, piece, nature })}
      />
    </>
  );
}

export function RegulariserEngagement() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { data, error, isLoading } = useLire<Engagement>(`/api/engagements/${id}`);
  const envoi = useAction(async ({ proposition, piece, nature }: { proposition: unknown; piece: File | null; nature: NatureDocument }) => {
    const maj = await envoyer<Engagement>(`/api/engagements/${id}/regularisation`, proposition);
    await joindre(id, piece, nature);
    return maj;
  }, () => navigate(`/engagements/${id}`));
  if (!data) return <Chargement erreur={error} chargement={isLoading} />;
  const initial: PropositionSaisie = {
    objet: data.objet,
    typeCommande: data.typeCommande,
    referencePiece: data.referencePiece ?? '',
    fournisseurNom: data.fournisseurNom,
    fournisseurIdentifiant: data.fournisseurIdentifiant,
    lignes: data.lignes.map((l) => ({ designation: l.designation, quantite: String(l.quantite), prixUnitaire: String(l.prixUnitaire) })),
  };
  return (
    <>
      <Entete fil={<Link to={`/engagements/${id}`}>Engagement n° {id}</Link>} titre="Corriger et renvoyer au comptable" />
      <FormulaireProposition
        initial={initial}
        ligneFixe={data.ligneBudgetaireId}
        serviceFixe={data.serviceOrdonnateur}
        libelleEnvoi="Renvoyer au comptable"
        enCours={envoi.isPending}
        erreur={envoi.error}
        onEnvoyer={(proposition, _cible, piece, nature) => envoi.mutate({ proposition, piece, nature })}
      />
    </>
  );
}

const LIBELLES_DECISION: Record<TypeDecision, string> = { VISA: 'Visa', SUSPENSION: 'Suspension', REFUS: 'Refus' };

function DecisionComptable({ engagement }: { engagement: Engagement }) {
  const [observations, setObservations] = useState('');
  const decision = useAction((type: TypeDecision) =>
    envoyer<Engagement>(`/api/engagements/${engagement.id}/decision`, {
      type,
      observations: observations.split('\n').map((o) => o.trim()).filter(Boolean),
    }), () => setObservations(''));
  const bloque = engagement.anomalies.length > 0;
  return (
    <Carte titre="Votre décision">
      {engagement.visaTacitementDu && <div className="message attention">Le délai de réponse est dépassé sur ce marché : le visa est dû.<span className="article">Décret n° 2-07-1235, art. 6</span></div>}
      {bloque && <div className="message attention">Le visa est impossible tant que les anomalies relevées ne sont pas levées.</div>}
      <Champ libelle="Observations" aide="Une par ligne. Elles sont toutes transmises en une seule fois à l'ordonnateur.">
        <textarea value={observations} onChange={(e) => setObservations(e.target.value)} />
      </Champ>
      <Refus erreur={decision.error} />
      <div className="rangee-boutons">
        <button className="bouton primaire" disabled={decision.isPending || bloque} onClick={() => decision.mutate('VISA')}>Viser et réserver les crédits</button>
        <button className="bouton" disabled={decision.isPending} onClick={() => decision.mutate('SUSPENSION')}>Suspendre</button>
        <button className="bouton danger" disabled={decision.isPending} onClick={() => decision.mutate('REFUS')}>Refuser</button>
      </div>
    </Carte>
  );
}

export function DetailEngagement() {
  const id = Number(useParams().id);
  const moi = useUtilisateur();
  const aujourdhui = useAujourdhui();
  const { data: e, error, isLoading } = useLire<Engagement>(`/api/engagements/${id}`);
  if (!e) return <Chargement erreur={error} chargement={isLoading} />;
  const estSonOrdonnateur = moi.a('ORDONNATEUR') && e.ordonnateur === moi.identifiant;
  return (
    <>
      <Entete fil={<Link to="/engagements">Engagements</Link>} titre={<>Engagement n° {e.id} <BadgeEngagement statut={e.statut} /></>}>
        <Link className="bouton" to={`/engagements/${e.id}/fiche`}>Fiche d'engagement</Link>
        {estSonOrdonnateur && e.statut === 'SUSPENDU' && <Link className="bouton primaire" to={`/engagements/${e.id}/regulariser`}>Corriger et renvoyer</Link>}
      </Entete>
      <div className="deux-colonnes">
        <div className="pile">
          <Carte titre={e.objet}>
            <dl className="liste-defs">
              <div><dt>Fournisseur</dt><dd>{e.fournisseurNom}</dd></div>
              <div><dt>ICE</dt><dd className="mono">{e.fournisseurIdentifiant}</dd></div>
              <div><dt>Commande</dt><dd>{LIBELLES_COMMANDE[e.typeCommande]}{e.referencePiece ? ` · ${e.referencePiece}` : ''}</dd></div>
              <div><dt>Imputation</dt><dd className="mono">{e.imputation}</dd></div>
              <div><dt>Service</dt><dd>{e.serviceOrdonnateur} ({e.regime === 'MODULE' ? 'contrôle modulé' : 'contrôle normal'})</dd></div>
              <div><dt>Ordonnateur</dt><dd>{e.ordonnateur}</dd></div>
            </dl>
            <div className="table-cadre">
              <table>
                <thead><tr><th>Désignation</th><th className="droite">Quantité</th><th className="droite">Prix unitaire</th><th className="droite">Montant</th></tr></thead>
                <tbody>
                  {e.lignes.map((l, i) => (
                    <tr key={i}><td>{l.designation}</td><td className="droite nombre">{l.quantite}</td><td className="droite nombre">{dh(l.prixUnitaire)}</td><td className="droite nombre">{dh(l.montant)}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Carte>
          <Pieces racine={`/api/engagements/${e.id}`} peutAjouter={estSonOrdonnateur && e.statut !== 'REFUSE'} natureParDefaut="BON_DE_COMMANDE" />
          {e.decisions.length > 0 && (
            <Carte titre="Décisions du comptable">
              {e.decisions.map((d, i) => (
                <div key={i}>
                  <b>{LIBELLES_DECISION[d.type]}</b> <span className="sourd petit">le {date(d.date)}, par {d.comptable}</span>
                  {d.observations.length > 0 && <ul className="petit">{d.observations.map((o, j) => <li key={j}>{o}</li>)}</ul>}
                </div>
              ))}
            </Carte>
          )}
        </div>
        <div className="pile">
          <Carte titre="Montants">
            <dl className="liste-defs">
              <div><dt>Proposé</dt><dd className="nombre">{dh(e.montantPropose)}</dd></div>
              <div><dt>Somme à valoir</dt><dd className="nombre">{dh(e.sommeAValoir)}</dd></div>
              <div><dt>À engager</dt><dd className="nombre">{dh(e.montantAEngager)}</dd></div>
              <div><dt>Liquidé</dt><dd className="nombre">{dh(e.montantLiquide)}</dd></div>
            </dl>
          </Carte>
          <Carte titre="Contrôle" droite={e.statut === 'SOUMIS' ? <Echeance limite={e.echeanceVisa} aujourdhui={aujourdhui} /> : undefined}>
            <p className="petit">Déposé le {date(e.dateDepot)}. Contrôle de régularité : <b>{e.controleDeRegulariteRequis ? 'requis' : 'non requis'}</b>.</p>
            {e.statut === 'SOUMIS' && e.anomalies.length === 0 && <div className="message ok">Aucune anomalie automatique.</div>}
            {e.anomalies.map((a) => (
              <div className="message attention" key={a.code}><span>{a.message}</span><span className="article">{a.reference}</span></div>
            ))}
          </Carte>
          {moi.a('COMPTABLE') && e.statut === 'SOUMIS' && <DecisionComptable engagement={e} />}
        </div>
      </div>
    </>
  );
}
