import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import type { Role } from './auth';
import { seDeconnecter } from './auth';
import { useAujourdhui, useLire, useUtilisateur } from './composants';
import { date } from './format';
import { Administration } from './pages/Administration';
import { Audit } from './pages/Audit';
import { Budget } from './pages/Budget';
import { Commandes } from './pages/Commandes';
import { Corbeille } from './pages/Corbeille';
import { DocumentDossier, FicheEngagement } from './pages/Documents';
import { DetailDossier, ListeDossiers } from './pages/Dossiers';
import { DetailEngagement, ListeEngagements, NouvelEngagement, RegulariserEngagement } from './pages/Engagements';
import { Pilotage } from './pages/Pilotage';
import type { Commande, Dossier, Engagement } from './types';

const LIBELLES_ROLE: Record<Role, string> = {
  ORDONNATEUR: 'Ordonnateur',
  COMPTABLE: 'Comptable public',
  FOURNISSEUR: 'Fournisseur',
  AUDITEUR: 'Auditeur',
  ADMINISTRATEUR: 'Administrateur',
};

const MENUS: Record<Role, { lien: string; libelle: string }[]> = {
  ORDONNATEUR: [
    { lien: '/', libelle: 'À traiter' },
    { lien: '/engagements', libelle: 'Engagements' },
    { lien: '/dossiers', libelle: 'Dossiers de paiement' },
    { lien: '/budget', libelle: 'Budget' },
    { lien: '/pilotage', libelle: 'Pilotage' },
  ],
  COMPTABLE: [
    { lien: '/', libelle: 'À traiter' },
    { lien: '/engagements', libelle: 'Engagements' },
    { lien: '/dossiers', libelle: 'Dossiers de paiement' },
    { lien: '/budget', libelle: 'Budget' },
    { lien: '/pilotage', libelle: 'Pilotage' },
  ],
  FOURNISSEUR: [
    { lien: '/', libelle: 'À traiter' },
    { lien: '/commandes', libelle: 'Mes commandes' },
    { lien: '/dossiers', libelle: 'Mes factures' },
  ],
  AUDITEUR: [
    { lien: '/pilotage', libelle: 'Pilotage' },
    { lien: '/audit', libelle: "Journal d'audit" },
    { lien: '/engagements', libelle: 'Engagements' },
    { lien: '/dossiers', libelle: 'Dossiers de paiement' },
    { lien: '/budget', libelle: 'Budget' },
  ],
  ADMINISTRATEUR: [
    { lien: '/administration', libelle: 'Administration' },
    { lien: '/budget', libelle: 'Budget' },
    { lien: '/pilotage', libelle: 'Pilotage' },
    { lien: '/audit', libelle: "Journal d'audit" },
    { lien: '/engagements', libelle: 'Engagements' },
    { lien: '/dossiers', libelle: 'Dossiers de paiement' },
  ],
};

/** Nombre d'éléments qui attendent l'utilisateur connecté. */
function useATraiter(): number {
  const moi = useUtilisateur();
  const agent = moi.a('ORDONNATEUR') || moi.a('COMPTABLE');
  const engagements = useLire<Engagement[]>('/api/engagements', agent).data ?? [];
  const dossiers = useLire<Dossier[]>('/api/dossiers', agent).data ?? [];
  const commandes = useLire<Commande[]>('/api/engagements/commandes', moi.a('FOURNISSEUR')).data ?? [];
  if (moi.a('ORDONNATEUR')) {
    return (
      engagements.filter((e) => e.ordonnateur === moi.identifiant && e.statut === 'SUSPENDU').length +
      dossiers.filter((d) => d.ordonnateur === moi.identifiant && ['DEPOSE', 'SERVICE_FAIT', 'LIQUIDE', 'SUSPENDU'].includes(d.statut)).length
    );
  }
  if (moi.a('COMPTABLE')) {
    return engagements.filter((e) => e.statut === 'SOUMIS').length + dossiers.filter((d) => ['ORDONNANCE', 'REQUISITIONNE'].includes(d.statut)).length;
  }
  return commandes.filter((c) => c.resteALiquider > 0).length;
}

function Coque() {
  const moi = useUtilisateur();
  const role = moi.roles.find((r) => r in MENUS) ?? 'AUDITEUR';
  const aujourdhui = useAujourdhui();
  const aTraiter = useATraiter();
  return (
    <div className="coque">
      <aside className="barre">
        <div className="marque">
          <b>Sarf</b>
          <small>Exécution de la dépense publique</small>
        </div>
        <nav className="menu" aria-label="Navigation principale">
          {MENUS[role].map((entree) => (
            <NavLink key={entree.lien} to={entree.lien} end={entree.lien === '/'}>
              {entree.libelle}
              {entree.lien === '/' && aTraiter > 0 && <span className="nombre">{aTraiter}</span>}
            </NavLink>
          ))}
        </nav>
        <div className="pied-barre">
          <div>
            <span className="role">{LIBELLES_ROLE[role].toUpperCase()}</span>
            <div>{moi.nomAffiche}</div>
          </div>
          <button onClick={seDeconnecter}>Se déconnecter</button>
        </div>
      </aside>
      <main className="contenu">
        <div className="bandeau-demo">
          <span>Environnement de démonstration, données fictives. Application indépendante, sans lien avec les systèmes de l'État.</span>
          <span>Date du système : <b>{date(aujourdhui)}</b></span>
        </div>
        <Outlet />
      </main>
    </div>
  );
}

function Accueil() {
  const moi = useUtilisateur();
  if (moi.a('ADMINISTRATEUR')) return <Navigate to="/administration" replace />;
  if (moi.a('AUDITEUR')) return <Navigate to="/pilotage" replace />;
  return <Corbeille />;
}

export function Application() {
  return (
    <Routes>
      <Route element={<Coque />}>
        <Route path="/" element={<Accueil />} />
        <Route path="/engagements" element={<ListeEngagements />} />
        <Route path="/engagements/nouveau" element={<NouvelEngagement />} />
        <Route path="/engagements/:id" element={<DetailEngagement />} />
        <Route path="/engagements/:id/regulariser" element={<RegulariserEngagement />} />
        <Route path="/engagements/:id/fiche" element={<FicheEngagement />} />
        <Route path="/commandes" element={<Commandes />} />
        <Route path="/dossiers" element={<ListeDossiers />} />
        <Route path="/dossiers/:id" element={<DetailDossier />} />
        <Route path="/dossiers/:id/document/:type" element={<DocumentDossier />} />
        <Route path="/budget" element={<Budget />} />
        <Route path="/pilotage" element={<Pilotage />} />
        <Route path="/audit" element={<Audit />} />
        <Route path="/administration" element={<Administration />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
