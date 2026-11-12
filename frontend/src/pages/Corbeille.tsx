import { Link } from 'react-router-dom';
import { Carte, Chargement, Echeance, Entete, useAujourdhui, useLire, useUtilisateur } from '../composants';
import { dh } from '../format';
import type { Commande, Dossier, Engagement } from '../types';

interface Tache {
  cle: string;
  lien: string;
  action: string;
  objet: string;
  montant: number;
  echeance: string | null;
}

function tachesDossier(d: Dossier): Tache | null {
  const base = { cle: `d${d.id}`, lien: `/dossiers/${d.id}`, objet: `Facture ${d.numeroFacture} · ${d.creancierNom}`, montant: d.montantFacture };
  switch (d.statut) {
    case 'DEPOSE':
      return { ...base, action: 'Certifier le service fait', echeance: d.echeanceCertification };
    case 'SERVICE_FAIT':
      return { ...base, action: 'Liquider la dépense', echeance: d.delais.echeanceOrdonnancement };
    case 'LIQUIDE':
      return { ...base, action: 'Émettre l’ordonnance de paiement', echeance: d.delais.echeanceOrdonnancement };
    case 'SUSPENDU':
      return { ...base, action: 'Régulariser ou requérir le paiement', echeance: d.delais.echeancePaiement };
    default:
      return null;
  }
}

function tachesComptable(d: Dossier): Tache | null {
  const base = { cle: `d${d.id}`, lien: `/dossiers/${d.id}`, objet: `Ordonnance ${d.numeroOrdonnance} · ${d.creancierNom}`, montant: d.montantLiquide };
  if (d.statut === 'ORDONNANCE') return { ...base, action: 'Contrôler et payer', echeance: d.delais.echeanceVisaComptable };
  if (d.statut === 'REQUISITIONNE') return { ...base, action: 'Payer sur réquisition', echeance: d.delais.echeancePaiement };
  return null;
}

const parEcheance = (a: Tache, b: Tache) => (a.echeance ?? '9').localeCompare(b.echeance ?? '9');

function ListeTaches({ taches, vide }: { taches: Tache[]; vide: string }) {
  const aujourdhui = useAujourdhui();
  if (taches.length === 0) return <p className="sourd">{vide}</p>;
  return (
    <div>
      {[...taches].sort(parEcheance).map((t) => (
        <Link key={t.cle} to={t.lien} className="tache">
          <span>
            <b>{t.action}</b>
            <small>{t.objet}</small>
          </span>
          <span className="nombre">{dh(t.montant)}</span>
          <Echeance limite={t.echeance} aujourdhui={aujourdhui} />
        </Link>
      ))}
    </div>
  );
}

function CorbeilleFournisseur() {
  const commandes = useLire<Commande[]>('/api/engagements/commandes');
  const dossiers = useLire<Dossier[]>('/api/dossiers');
  const taches: Tache[] = (commandes.data ?? [])
    .filter((c) => c.resteALiquider > 0)
    .map((c) => ({ cle: `c${c.id}`, lien: '/commandes', action: 'Déposer une facture', objet: c.objet, montant: c.resteALiquider, echeance: null }));
  const suivi: Tache[] = (dossiers.data ?? [])
    .filter((d) => d.statut !== 'PAYE')
    .map((d) => ({ cle: `d${d.id}`, lien: `/dossiers/${d.id}`, action: `Facture ${d.numeroFacture}`, objet: d.delais.echeancePaiement ? 'Paiement attendu' : 'Service fait à constater', montant: d.montantFacture, echeance: d.delais.echeancePaiement ?? d.echeanceCertification }));
  return (
    <>
      <Entete titre="À traiter" />
      <Chargement erreur={commandes.error ?? dossiers.error} chargement={commandes.isLoading || dossiers.isLoading} />
      <Carte titre="Commandes à facturer"><ListeTaches taches={taches} vide="Aucune commande à facturer." /></Carte>
      <Carte titre="Vos factures en cours"><ListeTaches taches={suivi} vide="Aucune facture en cours." /></Carte>
    </>
  );
}

function CorbeilleAgent() {
  const moi = useUtilisateur();
  const engagements = useLire<Engagement[]>('/api/engagements');
  const dossiers = useLire<Dossier[]>('/api/dossiers');
  const listeE = engagements.data ?? [];
  const listeD = dossiers.data ?? [];
  const taches: Tache[] = moi.a('ORDONNATEUR')
    ? [
        ...listeE
          .filter((e) => e.ordonnateur === moi.identifiant && e.statut === 'SUSPENDU')
          .map((e) => ({ cle: `e${e.id}`, lien: `/engagements/${e.id}`, action: 'Corriger l’engagement suspendu', objet: e.objet, montant: e.montantPropose, echeance: null })),
        ...listeD.filter((d) => d.ordonnateur === moi.identifiant).map(tachesDossier).filter((t): t is Tache => t !== null),
      ]
    : [
        ...listeE
          .filter((e) => e.statut === 'SOUMIS')
          .map((e) => ({ cle: `e${e.id}`, lien: `/engagements/${e.id}`, action: 'Viser l’engagement', objet: `${e.objet} · ${e.fournisseurNom}`, montant: e.montantPropose, echeance: e.echeanceVisa })),
        ...listeD.map(tachesComptable).filter((t): t is Tache => t !== null),
      ];
  return (
    <>
      <Entete titre="À traiter">{moi.a('ORDONNATEUR') && <Link className="bouton primaire" to="/engagements/nouveau">Nouvel engagement</Link>}</Entete>
      <Chargement erreur={engagements.error ?? dossiers.error} chargement={engagements.isLoading || dossiers.isLoading} />
      <Carte titre="Par échéance, du plus urgent au moins urgent">
        <ListeTaches taches={taches} vide="Rien à traiter pour le moment." />
      </Carte>
    </>
  );
}

export function Corbeille() {
  return useUtilisateur().a('FOURNISSEUR') ? <CorbeilleFournisseur /> : <CorbeilleAgent />;
}
