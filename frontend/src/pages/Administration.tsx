import { useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { envoyer } from '../api';
import { Carte, Champ, Chargement, Entete, Refus, useAction, useAujourdhui, useLire } from '../composants';
import { date } from '../format';
import type { Accreditation, CompteFournisseur, JourFerie, ServiceOrdonnateur, TauxTrimestriel } from '../types';

function Formulaire({ action, libelle, erreur, enCours, children }: { action: () => void; libelle: string; erreur: Error | null; enCours: boolean; children: ReactNode }) {
  const soumettre = (e: FormEvent) => {
    e.preventDefault();
    action();
  };
  return (
    <form className="formulaire" onSubmit={soumettre}>
      <div className="ligne-champs">{children}</div>
      <Refus erreur={erreur} />
      <div><button className="bouton primaire" disabled={enCours}>{libelle}</button></div>
    </form>
  );
}

function Horloge() {
  const aujourdhui = useAujourdhui();
  const [valeur, setValeur] = useState('');
  const deplacer = useAction(() => envoyer('/api/demo/horloge', { date: valeur }), () => setValeur(''));
  return (
    <Carte titre="Horloge de démonstration">
      <p className="petit sourd">Fait avancer la date du système pour montrer un délai qui expire. Date actuelle : <b>{date(aujourdhui)}</b>.</p>
      <Formulaire action={() => deplacer.mutate(undefined)} libelle="Aller à cette date" erreur={deplacer.error} enCours={deplacer.isPending}>
        <Champ libelle="Nouvelle date"><input required type="date" value={valeur} onChange={(e) => setValeur(e.target.value)} /></Champ>
      </Formulaire>
    </Carte>
  );
}

function Taux() {
  const liste = useLire<TauxTrimestriel[]>('/api/taux-interets-moratoires');
  const [annee, setAnnee] = useState(String(new Date().getFullYear()));
  const [trimestre, setTrimestre] = useState('1');
  const [bons, setBons] = useState('');
  const [source, setSource] = useState('');
  const enregistrer = useAction(() => envoyer('/api/taux-interets-moratoires', { annee: Number(annee), trimestre: Number(trimestre), tauxMoyenBonsDuTresor: Number(bons), source }), () => { setBons(''); setSource(''); });
  return (
    <>
      <Carte titre="Enregistrer le taux d'un trimestre">
        <p className="petit sourd">Saisir le taux moyen pondéré des bons du Trésor à 3 mois du trimestre précédent. Le taux est majoré d'un point et arrondi au dixième supérieur (décret n° 2-16-344, art. 11).</p>
        <Formulaire action={() => enregistrer.mutate(undefined)} libelle="Enregistrer" erreur={enregistrer.error} enCours={enregistrer.isPending}>
          <Champ libelle="Année"><input required type="number" min="2000" max="2100" value={annee} onChange={(e) => setAnnee(e.target.value)} /></Champ>
          <Champ libelle="Trimestre">
            <select value={trimestre} onChange={(e) => setTrimestre(e.target.value)}>{[1, 2, 3, 4].map((t) => <option key={t} value={t}>T{t}</option>)}</select>
          </Champ>
          <Champ libelle="Bons du Trésor (%)"><input required type="number" min="0" max="50" step="0.01" value={bons} onChange={(e) => setBons(e.target.value)} /></Champ>
          <Champ libelle="Source"><input required maxLength={300} value={source} onChange={(e) => setSource(e.target.value)} /></Champ>
        </Formulaire>
      </Carte>
      <Carte titre="Taux enregistrés">
        <Chargement erreur={liste.error} chargement={liste.isLoading} />
        <div className="table-cadre">
          <table>
            <thead><tr><th>Trimestre</th><th className="droite">Taux</th><th>Source</th></tr></thead>
            <tbody>{liste.data?.map((t) => <tr key={`${t.annee}-${t.trimestre}`}><td className="nombre">T{t.trimestre} {t.annee}</td><td className="droite nombre">{t.taux} %</td><td className="petit">{t.source}</td></tr>)}</tbody>
          </table>
        </div>
      </Carte>
    </>
  );
}

function JoursFeries() {
  const aujourdhui = useAujourdhui();
  const annee = aujourdhui.slice(0, 4);
  const liste = useLire<JourFerie[]>(`/api/jours-feries?annee=${annee}`);
  const [jour, setJour] = useState('');
  const [libelle, setLibelle] = useState('');
  const ajouter = useAction(() => envoyer('/api/jours-feries', { jour, libelle }), () => { setJour(''); setLibelle(''); });
  return (
    <>
      <Carte titre="Ajouter un jour férié">
        <p className="petit sourd">Les fêtes religieuses suivent le calendrier hégirien et sont à saisir chaque année.</p>
        <Formulaire action={() => ajouter.mutate(undefined)} libelle="Ajouter" erreur={ajouter.error} enCours={ajouter.isPending}>
          <Champ libelle="Date"><input required type="date" value={jour} onChange={(e) => setJour(e.target.value)} /></Champ>
          <Champ libelle="Libellé"><input required maxLength={120} value={libelle} onChange={(e) => setLibelle(e.target.value)} /></Champ>
        </Formulaire>
      </Carte>
      <Carte titre={`Jours fériés ${annee}`}>
        <Chargement erreur={liste.error} chargement={liste.isLoading} />
        <table><tbody>{liste.data?.map((j) => <tr key={j.jour}><td className="nombre">{date(j.jour)}</td><td>{j.libelle}</td></tr>)}</tbody></table>
      </Carte>
    </>
  );
}

function Accreditations() {
  const services = useLire<ServiceOrdonnateur[]>('/api/services-ordonnateurs');
  const liste = useLire<Accreditation[]>('/api/services-ordonnateurs/accreditations');
  const [utilisateur, setUtilisateur] = useState('');
  const [service, setService] = useState('');
  const accrediter = useAction(() => envoyer(`/api/services-ordonnateurs/${service}/accreditations`, { utilisateur }), () => setUtilisateur(''));
  return (
    <>
      <Carte titre="Accréditer un ordonnateur auprès du comptable">
        <Formulaire action={() => accrediter.mutate(undefined)} libelle="Accréditer" erreur={accrediter.error} enCours={accrediter.isPending}>
          <Champ libelle="Identifiant de connexion"><input required value={utilisateur} onChange={(e) => setUtilisateur(e.target.value)} /></Champ>
          <Champ libelle="Service">
            <select required value={service} onChange={(e) => setService(e.target.value)}>
              <option value="">Choisir</option>
              {services.data?.map((s) => <option key={s.code} value={s.code}>{s.libelle}</option>)}
            </select>
          </Champ>
        </Formulaire>
      </Carte>
      <Carte titre="Accréditations en vigueur">
        <Chargement erreur={liste.error} chargement={liste.isLoading} />
        <table><tbody>{liste.data?.map((a) => <tr key={`${a.utilisateur}-${a.serviceCode}`}><td>{a.utilisateur}</td><td className="mono">{a.serviceCode}</td></tr>)}</tbody></table>
      </Carte>
    </>
  );
}

function ComptesFournisseurs() {
  const liste = useLire<CompteFournisseur[]>('/api/comptes-fournisseurs');
  const [utilisateur, setUtilisateur] = useState('');
  const [identifiant, setIdentifiant] = useState('');
  const [nom, setNom] = useState('');
  const rattacher = useAction(() => envoyer('/api/comptes-fournisseurs', { utilisateur, fournisseurIdentifiant: identifiant, fournisseurNom: nom }), () => { setUtilisateur(''); setIdentifiant(''); setNom(''); });
  return (
    <>
      <Carte titre="Rattacher un compte à un fournisseur">
        <Formulaire action={() => rattacher.mutate(undefined)} libelle="Rattacher" erreur={rattacher.error} enCours={rattacher.isPending}>
          <Champ libelle="Identifiant de connexion"><input required maxLength={100} value={utilisateur} onChange={(e) => setUtilisateur(e.target.value)} /></Champ>
          <Champ libelle="ICE"><input required maxLength={30} value={identifiant} onChange={(e) => setIdentifiant(e.target.value)} /></Champ>
          <Champ libelle="Raison sociale"><input required maxLength={200} value={nom} onChange={(e) => setNom(e.target.value)} /></Champ>
        </Formulaire>
      </Carte>
      <Carte titre="Comptes rattachés">
        <Chargement erreur={liste.error} chargement={liste.isLoading} />
        <table><tbody>{liste.data?.map((c) => <tr key={c.utilisateur}><td>{c.utilisateur}</td><td className="mono">{c.fournisseurIdentifiant}</td><td>{c.fournisseurNom}</td></tr>)}</tbody></table>
      </Carte>
    </>
  );
}

const ONGLETS = [
  { cle: 'horloge', libelle: 'Horloge', vue: <Horloge /> },
  { cle: 'taux', libelle: 'Taux des intérêts', vue: <Taux /> },
  { cle: 'feries', libelle: 'Jours fériés', vue: <JoursFeries /> },
  { cle: 'accreditations', libelle: 'Accréditations', vue: <Accreditations /> },
  { cle: 'comptes', libelle: 'Comptes fournisseurs', vue: <ComptesFournisseurs /> },
];

export function Administration() {
  const [actif, setActif] = useState(ONGLETS[0].cle);
  return (
    <>
      <Entete titre="Administration" />
      <div className="onglets" role="tablist">
        {ONGLETS.map((o) => (
          <button key={o.cle} role="tab" aria-selected={o.cle === actif} className={o.cle === actif ? 'actif' : ''} onClick={() => setActif(o.cle)}>{o.libelle}</button>
        ))}
      </div>
      {ONGLETS.find((o) => o.cle === actif)?.vue}
    </>
  );
}
