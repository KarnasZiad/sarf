import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Chargement, Entete, useLire, useUtilisateur } from '../composants';
import { date, dh, LIBELLES_COMMANDE, montantEnLettres } from '../format';
import type { Dossier, Engagement } from '../types';

const MENTION = 'Document généré par Sarf, application indépendante, en environnement de démonstration. Sans valeur officielle.';

function Qr({ contenu }: { contenu: string }) {
  const [image, setImage] = useState('');
  useEffect(() => {
    let actif = true;
    void QRCode.toDataURL(contenu, { margin: 0, width: 96 }).then((url) => { if (actif) setImage(url); });
    return () => { actif = false; };
  }, [contenu]);
  return image ? <img src={image} width={96} height={96} alt="Code QR de vérification" /> : null;
}

function BarreImpression({ retour, libelle }: { retour: string; libelle: string }) {
  return (
    <Entete fil={<Link to={retour}>{libelle}</Link>} titre="Document">
      <button className="bouton primaire" onClick={() => window.print()}>Imprimer ou enregistrer en PDF</button>
    </Entete>
  );
}

function Accuse({ d }: { d: Dossier }) {
  const reference = `AR-${d.exercice}-${String(d.id).padStart(6, '0')}`;
  return (
    <article className="document">
      <h1>Accusé de dépôt de facture</h1>
      <div className="message ok sans-impression">Votre facture est déposée. Conservez cet accusé.</div>
      <div className="qr">
        <Qr contenu={`SARF|${reference}|${d.creancierIdentifiant}|${d.numeroFacture}|${d.montantFacture}|${d.dateDepot}`} />
        <div>
          <b>Référence : <span className="mono">{reference}</span></b>
          <div>Déposée le {date(d.dateDepot)} par {d.deposant}</div>
        </div>
      </div>
      <table>
        <tbody>
          <tr><td>Fournisseur</td><td><b>{d.creancierNom}</b> (ICE {d.creancierIdentifiant})</td></tr>
          <tr><td>Facture n°</td><td><b>{d.numeroFacture}</b></td></tr>
          <tr><td>Montant</td><td><b>{dh(d.montantFacture)}</b></td></tr>
          <tr><td>Engagement</td><td>n° {d.engagementId}, imputation {d.imputation}</td></tr>
          <tr><td>Service fait à constater avant le</td><td><b>{date(d.echeanceCertification)}</b></td></tr>
        </tbody>
      </table>
      <p>Le délai de paiement de 60 jours court à compter de la constatation du service fait, ou au plus tard du 31<sup>e</sup> jour suivant le dépôt (décret n° 2-16-344, art. 2, 5 et 6).</p>
      <p className="mention">{MENTION}</p>
    </article>
  );
}

function Ordonnance({ d }: { d: Dossier }) {
  return (
    <article className="document">
      <div className="entete-doc">
        <span>Service ordonnateur : <b>{d.serviceOrdonnateur}</b><br />Exercice : <b>{d.exercice}</b></span>
        <span>N° <b className="mono">{d.numeroOrdonnance}</b><br />Émise le {date(d.dateOrdonnancement)}</span>
      </div>
      <h1>Ordonnance de paiement</h1>
      <table>
        <tbody>
          <tr><td>Bénéficiaire</td><td><b>{d.creancierNom}</b> (ICE {d.creancierIdentifiant})</td></tr>
          <tr><td>RIB</td><td className="mono">{d.creancierRib}</td></tr>
          <tr><td>Imputation budgétaire</td><td className="mono">{d.imputation}</td></tr>
          <tr><td>Engagement</td><td>n° {d.engagementId}</td></tr>
          <tr><td>Facture</td><td>{d.numeroFacture}, déposée le {date(d.dateDepot)}</td></tr>
          <tr><td>Service fait constaté le</td><td>{date(d.dateServiceFait)}</td></tr>
          <tr><td>Montant liquidé</td><td><b>{dh(d.montantLiquide)}</b></td></tr>
        </tbody>
      </table>
      <p>Arrêtée la présente ordonnance à la somme de : <b>{montantEnLettres(d.montantLiquide)}</b>.</p>
      <div className="signatures">
        <span>L'ordonnateur<br /><b>{d.ordonnateur}</b></span>
        <span>Visa du comptable<br />{d.statut === 'PAYE' ? <b>Visé et payé le {date(d.datePaiement)}</b> : 'En attente'}</span>
      </div>
      <p className="mention">{MENTION}</p>
    </article>
  );
}

function Avis({ d }: { d: Dossier }) {
  return (
    <article className="document">
      <h1>Avis de paiement</h1>
      <table>
        <tbody>
          <tr><td>Bénéficiaire</td><td><b>{d.creancierNom}</b></td></tr>
          <tr><td>RIB crédité</td><td className="mono">{d.creancierRib}</td></tr>
          <tr><td>Facture</td><td>{d.numeroFacture}</td></tr>
          <tr><td>Ordonnance</td><td className="mono">{d.numeroOrdonnance}</td></tr>
          <tr><td>Date de règlement</td><td><b>{date(d.datePaiement)}</b></td></tr>
          <tr><td>Mode</td><td>{d.modeReglement === 'VIREMENT' ? 'Virement' : 'Espèces'}</td></tr>
          <tr><td>Montant réglé</td><td><b>{dh(d.montantLiquide)}</b></td></tr>
          {d.interetsMoratoires > 0 && <tr><td>Intérêts moratoires dus</td><td><b>{dh(d.interetsMoratoires)}</b> pour {d.delais.joursDeRetard} jours de retard</td></tr>}
        </tbody>
      </table>
      {d.payeSurRequisition && <p>Paiement effectué sur réquisition de l'ordonnateur (décret n° 2-07-1235, art. 29).</p>}
      <p className="mention">{MENTION}</p>
    </article>
  );
}

export function DocumentDossier() {
  const { id, type } = useParams();
  const moi = useUtilisateur();
  const { data: d, error, isLoading } = useLire<Dossier>(`/api/dossiers/${id}`);
  if (!d) return <Chargement erreur={error} chargement={isLoading} />;
  return (
    <>
      <BarreImpression retour={`/dossiers/${d.id}`} libelle={moi.a('FOURNISSEUR') ? 'Ma facture' : 'Dossier'} />
      {type === 'accuse' && <Accuse d={d} />}
      {type === 'ordonnance' && d.numeroOrdonnance && <Ordonnance d={d} />}
      {type === 'avis' && d.statut === 'PAYE' && <Avis d={d} />}
    </>
  );
}

export function FicheEngagement() {
  const { id } = useParams();
  const { data: e, error, isLoading } = useLire<Engagement>(`/api/engagements/${id}`);
  if (!e) return <Chargement erreur={error} chargement={isLoading} />;
  const visa = e.decisions.find((d) => d.type === 'VISA');
  return (
    <>
      <BarreImpression retour={`/engagements/${e.id}`} libelle="Engagement" />
      <article className="document">
        <div className="entete-doc">
          <span>Service ordonnateur : <b>{e.serviceOrdonnateur}</b><br />Exercice : <b>{e.exercice}</b></span>
          <span>Engagement n° <b>{e.id}</b><br />Déposé le {date(e.dateDepot)}</span>
        </div>
        <h1>Fiche d'engagement de dépense</h1>
        <table>
          <tbody>
            <tr><td>Objet</td><td><b>{e.objet}</b></td></tr>
            <tr><td>Fournisseur</td><td>{e.fournisseurNom} (ICE {e.fournisseurIdentifiant})</td></tr>
            <tr><td>Commande</td><td>{LIBELLES_COMMANDE[e.typeCommande]}{e.referencePiece ? ` · ${e.referencePiece}` : ''}</td></tr>
            <tr><td>Imputation budgétaire</td><td className="mono">{e.imputation}</td></tr>
            <tr><td>Montant proposé</td><td><b>{dh(e.montantPropose)}</b></td></tr>
            <tr><td>Somme à valoir (1 %)</td><td>{dh(e.sommeAValoir)}</td></tr>
            <tr><td>Montant à engager</td><td><b>{dh(e.montantAEngager)}</b></td></tr>
          </tbody>
        </table>
        <p>Arrêté le présent engagement à la somme de : <b>{montantEnLettres(e.montantAEngager)}</b>.</p>
        <div className="signatures">
          <span>L'ordonnateur<br /><b>{e.ordonnateur}</b></span>
          <span>Visa du comptable<br />{visa ? <b>Visé le {date(visa.date)} par {visa.comptable}</b> : 'En attente'}</span>
        </div>
        <p className="mention">{MENTION}</p>
      </article>
    </>
  );
}
