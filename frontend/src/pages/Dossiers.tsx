import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { envoyer } from '../api';
import { BadgeDossier, Carte, Champ, Chargement, Echeance, Entete, Pieces, Refus, useAction, useAujourdhui, useLire, useUtilisateur } from '../composants';
import { date, dh, joursEntre, LIBELLES_STATUT_DOSSIER, MOTIFS_SUSPENSION, pluriel } from '../format';
import type { Dossier, InteretsCalcul, StatutDossier } from '../types';

function prochaineEcheance(d: Dossier): string | null {
  switch (d.statut) {
    case 'DEPOSE': return d.echeanceCertification;
    case 'SERVICE_FAIT':
    case 'LIQUIDE': return d.delais.echeanceOrdonnancement;
    case 'ORDONNANCE': return d.delais.echeanceVisaComptable;
    case 'PAYE': return null;
    default: return d.delais.echeancePaiement;
  }
}

export function ListeDossiers() {
  const moi = useUtilisateur();
  const navigate = useNavigate();
  const aujourdhui = useAujourdhui();
  const [statut, setStatut] = useState('');
  const [recherche, setRecherche] = useState('');
  const { data, error, isLoading } = useLire<Dossier[]>(`/api/dossiers${statut ? `?statut=${statut}` : ''}`);
  const filtre = recherche.toLowerCase();
  const liste = (data ?? []).filter((d) => `${d.numeroFacture} ${d.creancierNom} ${d.numeroOrdonnance ?? ''}`.toLowerCase().includes(filtre));
  return (
    <>
      <Entete titre={moi.a('FOURNISSEUR') ? 'Mes factures' : 'Dossiers de paiement'} />
      <Carte>
        <div className="ligne-champs">
          <Champ libelle="Rechercher"><input value={recherche} onChange={(e) => setRecherche(e.target.value)} placeholder="Facture, fournisseur, ordonnance" /></Champ>
          <Champ libelle="Statut">
            <select value={statut} onChange={(e) => setStatut(e.target.value)}>
              <option value="">Tous</option>
              {Object.entries(LIBELLES_STATUT_DOSSIER).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </Champ>
        </div>
        <Chargement erreur={error} chargement={isLoading} />
        {data && liste.length === 0 && <p className="sourd">Aucun dossier ne correspond.</p>}
        {liste.length > 0 && (
          <div className="table-cadre">
            <table>
              <thead><tr><th>Facture</th><th>Fournisseur</th><th>Déposée le</th><th className="droite">Montant</th><th>Statut</th><th>Échéance</th><th className="droite">Intérêts</th></tr></thead>
              <tbody>
                {liste.map((d) => (
                  <tr key={d.id} className="cliquable" onClick={() => navigate(`/dossiers/${d.id}`)}>
                    <td className="mono"><Link to={`/dossiers/${d.id}`} onClick={(ev) => ev.stopPropagation()}>{d.numeroFacture}</Link></td>
                    <td>{d.creancierNom}</td>
                    <td className="nombre">{date(d.dateDepot)}</td>
                    <td className="droite nombre">{dh(d.montantFacture)}</td>
                    <td><BadgeDossier statut={d.statut} /></td>
                    <td><Echeance limite={prochaineEcheance(d)} aujourdhui={aujourdhui} /></td>
                    <td className="droite nombre">{d.interetsMoratoires > 0 ? dh(d.interetsMoratoires) : '—'}</td>
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

const RANGS: Record<StatutDossier, number> = { DEPOSE: 0, SERVICE_FAIT: 1, LIQUIDE: 2, ORDONNANCE: 3, SUSPENDU: 3, REQUISITIONNE: 3, PAYE: 4 };

function Frise({ d }: { d: Dossier }) {
  const etapes = [
    { nom: 'Facture déposée', date: d.dateDepot },
    { nom: 'Service fait', date: d.dateServiceFait },
    { nom: 'Liquidation', date: null, detail: d.montantLiquide > 0 ? dh(d.montantLiquide) : null },
    { nom: 'Ordonnancement', date: d.dateOrdonnancement, detail: d.numeroOrdonnance },
    { nom: 'Paiement', date: d.datePaiement },
  ];
  const rang = RANGS[d.statut];
  return (
    <ol className="frise">
      {etapes.map((e, i) => (
        <li key={e.nom} className={i <= rang ? 'fait' : i === rang + 1 ? 'courant' : ''}>
          <b>{e.nom}</b>
          {e.date && <span className="nombre">{date(e.date)}</span>}
          {e.detail && <div className="petit sourd">{e.detail}</div>}
        </li>
      ))}
    </ol>
  );
}

function CarteDelais({ d, voitInterets }: { d: Dossier; voitInterets: boolean }) {
  const aujourdhui = useAujourdhui();
  const detail = useLire<InteretsCalcul>(`/api/dossiers/${d.id}/interets`, voitInterets && d.delais.joursDeRetard > 0);
  if (!d.dateServiceFait || !d.delais.echeancePaiement) {
    return (
      <Carte titre="Délai légal" droite={<Echeance limite={d.echeanceCertification} aujourdhui={aujourdhui} />}>
        <p className="petit">Le délai de paiement de 60 jours démarrera au service fait. L'ordonnateur doit le constater avant le <b>{date(d.echeanceCertification)}</b>.</p>
        <p className="petit sourd">Décret n° 2-16-344, art. 5 et 6 : au-delà de 30 jours, le 31<sup>e</sup> jour est retenu.</p>
      </Carte>
    );
  }
  const total = Math.max(1, joursEntre(d.dateServiceFait, d.delais.echeancePaiement));
  const terme = d.datePaiement ?? aujourdhui;
  const ecoules = joursEntre(d.dateServiceFait, terme);
  const enRetard = d.delais.joursDeRetard > 0;
  return (
    <Carte titre="Délai légal" droite={d.statut === 'PAYE' ? <span className={`badge ${enRetard ? 'critique' : 'ok'}`}>{enRetard ? `Payé avec ${pluriel(d.delais.joursDeRetard, 'jour')} de retard` : 'Payé dans le délai'}</span> : <Echeance limite={d.delais.echeancePaiement} aujourdhui={aujourdhui} />}>
      <div className="jauge" role="img" aria-label={`${ecoules} jours écoulés sur ${total}`}>
        <i style={{ width: `${Math.min(100, (ecoules / total) * 100)}%`, background: enRetard ? 'var(--critique)' : 'var(--accent)' }} />
      </div>
      <p className="petit nombre">{ecoules} jours écoulés sur {total}{d.delais.joursSuspendus > 0 ? `, dont ${pluriel(d.delais.joursSuspendus, 'jour')} de suspension` : ''}</p>
      <dl className="liste-defs">
        <div><dt>Service fait retenu</dt><dd>{date(d.dateServiceFait)}</dd></div>
        <div><dt>Ordonnancer avant le</dt><dd>{date(d.delais.echeanceOrdonnancement)}</dd></div>
        <div><dt>Payer avant le</dt><dd>{date(d.delais.echeancePaiement)}</dd></div>
        {d.delais.echeanceVisaComptable && <div><dt>Limite du comptable</dt><dd>{date(d.delais.echeanceVisaComptable)}</dd></div>}
      </dl>
      {d.delais.ordonnancementEnRetard && <div className="message critique">Le délai d'ordonnancement de 45 jours est dépassé.<span className="article">Décret n° 2-16-344, art. 2</span></div>}
      {d.delais.visaComptableEnRetard && <div className="message critique">Le délai du comptable est dépassé.<span className="article">Décret n° 2-16-344, art. 2</span></div>}
      {enRetard && (
        <div className="message attention">
          <span>Intérêts moratoires {d.statut === 'PAYE' ? 'dus' : 'courus à ce jour'} : <b className="nombre">{dh(d.interetsMoratoires)}</b>, pour {pluriel(d.delais.joursDeRetard, 'jour')} de retard.</span>
          {detail.data?.tranches.map((t) => (
            <span className="petit nombre" key={`${t.annee}-${t.trimestre}`}>T{t.trimestre} {t.annee} : {pluriel(t.jours, 'jour')} à {t.taux} %, soit {dh(t.montant.valeur)}</span>
          ))}
          <span className="article">Décret n° 2-16-344, art. 8 à 11</span>
        </div>
      )}
    </Carte>
  );
}

function ActionsOrdonnateur({ d }: { d: Dossier }) {
  const racine = `/api/dossiers/${d.id}`;
  const [montant, setMontant] = useState(String(d.montantFacture));
  const [motif, setMotif] = useState('');
  const action = useAction(({ chemin, corps }: { chemin: string; corps?: unknown }) => envoyer<Dossier>(`${racine}/${chemin}`, corps), () => setMotif(''));
  const faire = (chemin: string, corps?: unknown) => action.mutate({ chemin, corps });
  const suspensionEnCours = d.suspensions.some((s) => !s.fin);
  const peutSignaler = (d.statut === 'SERVICE_FAIT' || d.statut === 'LIQUIDE') && !suspensionEnCours;
  return (
    <Carte titre="Votre action">
      <Refus erreur={action.error} />
      {d.statut === 'DEPOSE' && (
        <>
          <p className="petit">En certifiant, vous attestez que la prestation a été réalisée. Le délai de paiement commence.</p>
          <button className="bouton primaire" disabled={action.isPending} onClick={() => faire('service-fait')}>Certifier le service fait</button>
        </>
      )}
      {d.statut === 'SERVICE_FAIT' && (
        <>
          <Champ libelle="Montant à liquider (DH)" aide={`Facture : ${dh(d.montantFacture)}`}><input type="number" min="0.01" step="0.01" value={montant} onChange={(e) => setMontant(e.target.value)} /></Champ>
          <button className="bouton primaire" disabled={action.isPending} onClick={() => faire('liquidation', { montant: Number(montant) })}>Liquider</button>
        </>
      )}
      {d.statut === 'LIQUIDE' && (
        <>
          <p className="petit">L'ordonnance reçoit son numéro dans la série du service, puis part chez le comptable.</p>
          <button className="bouton primaire" disabled={action.isPending || suspensionEnCours} onClick={() => faire('ordonnancement')}>Émettre l'ordonnance</button>
        </>
      )}
      {suspensionEnCours && (
        <>
          <div className="message attention">Le délai est suspendu : des pièces sont attendues du fournisseur.</div>
          <button className="bouton" disabled={action.isPending} onClick={() => faire('pieces-recues')}>Pièces reçues, reprendre le délai</button>
        </>
      )}
      {peutSignaler && (
        <>
          <Champ libelle="Pièces manquantes" aide="Le délai est suspendu jusqu'à leur réception (décret n° 2-16-344, art. 3)."><textarea value={motif} onChange={(e) => setMotif(e.target.value)} /></Champ>
          <button className="bouton" disabled={action.isPending || !motif.trim()} onClick={() => faire('pieces-manquantes', { motif })}>Demander les pièces</button>
        </>
      )}
      {d.statut === 'SUSPENDU' && (
        <>
          <p className="petit">Le comptable a suspendu le visa. Régularisez puis renvoyez, ou requérez le paiement par écrit.</p>
          <button className="bouton primaire" disabled={action.isPending} onClick={() => faire('regularisation')}>Dossier régularisé, renvoyer au comptable</button>
          <Champ libelle="Réquisition écrite" aide="Sous votre responsabilité (décret n° 2-07-1235, art. 29). Impossible pour trois motifs (art. 30)."><textarea value={motif} onChange={(e) => setMotif(e.target.value)} /></Champ>
          <button className="bouton danger" disabled={action.isPending || !motif.trim()} onClick={() => faire('requisition', { motif })}>Requérir le paiement</button>
        </>
      )}
    </Carte>
  );
}

function ActionsComptable({ d }: { d: Dossier }) {
  const racine = `/api/dossiers/${d.id}`;
  const [mode, setMode] = useState<'VIREMENT' | 'ESPECES'>('VIREMENT');
  const [motifs, setMotifs] = useState<string[]>([]);
  const [note, setNote] = useState('');
  const action = useAction(({ chemin, corps }: { chemin: string; corps: unknown }) => envoyer<Dossier>(`${racine}/${chemin}`, corps));
  const selecteurMode = (
    <Champ libelle="Mode de règlement">
      <select value={mode} onChange={(e) => setMode(e.target.value as 'VIREMENT' | 'ESPECES')}>
        <option value="VIREMENT">Virement</option>
        <option value="ESPECES">Espèces</option>
      </select>
    </Champ>
  );
  if (d.statut === 'REQUISITIONNE') {
    return (
      <Carte titre="Votre action">
        <div className="message info">
          <span>Réquisition de {d.requisition?.ordonnateur} : {d.requisition?.motif}</span>
          <span className="article">Décret n° 2-07-1235, art. 29 : votre responsabilité est dégagée.</span>
        </div>
        {selecteurMode}
        <Refus erreur={action.error} />
        <button className="bouton primaire" disabled={action.isPending} onClick={() => action.mutate({ chemin: 'paiement-sur-requisition', corps: { modeReglement: mode } })}>Payer sur réquisition</button>
      </Carte>
    );
  }
  const bloque = d.anomalies.length > 0;
  const basculer = (code: string) => setMotifs(motifs.includes(code) ? motifs.filter((m) => m !== code) : [...motifs, code]);
  return (
    <Carte titre="Votre contrôle">
      {!bloque && <div className="message ok">Le contrôle de validité ne relève aucune anomalie.</div>}
      {selecteurMode}
      <button className="bouton primaire" disabled={action.isPending || bloque} onClick={() => action.mutate({ chemin: 'controle', corps: { decision: 'VISER', modeReglement: mode } })}>Viser et payer {dh(d.montantLiquide)}</button>
      <h3>Ou suspendre le visa</h3>
      {MOTIFS_SUSPENSION.map((m) => (
        <label className="case" key={m.code}>
          <input type="checkbox" checked={motifs.includes(m.code)} onChange={() => basculer(m.code)} />
          <span>{m.libelle}{!m.requisitionnable && <span className="petit sourd"> (réquisition impossible)</span>}</span>
        </label>
      ))}
      <Champ libelle="Note motivée"><textarea value={note} onChange={(e) => setNote(e.target.value)} /></Champ>
      <Refus erreur={action.error} />
      <button className="bouton danger" disabled={action.isPending || !note.trim() || (motifs.length === 0 && !bloque)} onClick={() => action.mutate({ chemin: 'controle', corps: { decision: 'SUSPENDRE', motifs, note } })}>Suspendre le visa</button>
    </Carte>
  );
}

const LIBELLES_DECISION: Record<string, string> = { VISA_ET_REGLEMENT: 'Visa et règlement', SUSPENSION: 'Suspension du visa', VISA_SUR_REQUISITION: 'Paiement sur réquisition' };

export function DetailDossier() {
  const id = Number(useParams().id);
  const moi = useUtilisateur();
  const { data: d, error, isLoading } = useLire<Dossier>(`/api/dossiers/${id}`);
  if (!d) return <Chargement erreur={error} chargement={isLoading} />;
  const estSonOrdonnateur = moi.a('ORDONNATEUR') && d.ordonnateur === moi.identifiant;
  const montreActionsOrdonnateur = estSonOrdonnateur && ['DEPOSE', 'SERVICE_FAIT', 'LIQUIDE', 'SUSPENDU'].includes(d.statut);
  const montreActionsComptable = moi.a('COMPTABLE') && ['ORDONNANCE', 'REQUISITIONNE'].includes(d.statut);
  const peutAjouter = d.statut !== 'PAYE' && (estSonOrdonnateur || (moi.a('FOURNISSEUR') && d.deposant === moi.identifiant));
  return (
    <>
      <Entete fil={<Link to="/dossiers">{moi.a('FOURNISSEUR') ? 'Mes factures' : 'Dossiers de paiement'}</Link>} titre={<>Facture {d.numeroFacture} <BadgeDossier statut={d.statut} /></>}>
        <Link className="bouton" to={`/dossiers/${d.id}/document/accuse`}>Accusé de dépôt</Link>
        {d.numeroOrdonnance && <Link className="bouton" to={`/dossiers/${d.id}/document/ordonnance`}>Ordonnance</Link>}
        {d.statut === 'PAYE' && <Link className="bouton" to={`/dossiers/${d.id}/document/avis`}>Avis de paiement</Link>}
      </Entete>
      <Carte><Frise d={d} /></Carte>
      <div className="deux-colonnes">
        <div className="pile">
          <Carte titre="Dossier">
            <dl className="liste-defs">
              <div><dt>Fournisseur</dt><dd>{d.creancierNom}</dd></div>
              <div><dt>ICE</dt><dd className="mono">{d.creancierIdentifiant}</dd></div>
              <div><dt>RIB</dt><dd className="mono">{d.creancierRib ?? '—'}</dd></div>
              <div><dt>Montant de la facture</dt><dd className="nombre">{dh(d.montantFacture)}</dd></div>
              <div><dt>Retenue de garantie</dt><dd className="nombre">{dh(d.retenueGarantie)}</dd></div>
              <div><dt>Montant liquidé</dt><dd className="nombre">{d.montantLiquide > 0 ? dh(d.montantLiquide) : '—'}</dd></div>
              <div><dt>Engagement</dt><dd>{moi.a('FOURNISSEUR') ? `n° ${d.engagementId}` : <Link to={`/engagements/${d.engagementId}`}>n° {d.engagementId}</Link>}</dd></div>
              <div><dt>Imputation</dt><dd className="mono">{d.imputation}</dd></div>
              <div><dt>Ordonnateur</dt><dd>{d.ordonnateur}</dd></div>
              {d.numeroOrdonnance && <div><dt>N° d'ordonnance</dt><dd className="mono">{d.numeroOrdonnance}</dd></div>}
              {d.datePaiement && <div><dt>Payé le</dt><dd>{date(d.datePaiement)}{d.modeReglement ? `, par ${d.modeReglement === 'VIREMENT' ? 'virement' : 'espèces'}` : ''}</dd></div>}
            </dl>
          </Carte>
          <Pieces racine={`/api/dossiers/${d.id}`} peutAjouter={peutAjouter} natureParDefaut="ATTESTATION_SERVICE_FAIT" />
          {(d.decisions.length > 0 || d.suspensions.length > 0 || d.requisition) && (
            <Carte titre="Historique">
              {d.suspensions.map((s, i) => <p className="petit" key={`s${i}`}><b>Délai suspendu</b> du {date(s.debut)}{s.fin ? ` au ${date(s.fin)}` : ', en cours'} : {s.motif}</p>)}
              {d.decisions.map((x, i) => (
                <div className="petit" key={`d${i}`}>
                  <b>{LIBELLES_DECISION[x.type] ?? x.type}</b> <span className="sourd">le {date(x.date)}, par {x.comptable}</span>
                  {x.note && <p>{x.note}</p>}
                  {x.motifs.length > 0 && <ul>{x.motifs.map((m) => <li key={m}>{m}</li>)}</ul>}
                </div>
              ))}
              {d.requisition && <p className="petit"><b>Réquisition</b> le {date(d.requisition.date)}, par {d.requisition.ordonnateur} : {d.requisition.motif}</p>}
            </Carte>
          )}
        </div>
        <div className="pile">
          <CarteDelais d={d} voitInterets={!moi.a('FOURNISSEUR')} />
          {d.anomalies.length > 0 && (
            <Carte titre={d.statut === 'LIQUIDE' ? 'Ce que le comptable relèvera' : 'Contrôle de validité'}>
              {d.anomalies.map((a) => (
                <div className="message attention" key={a.code}>
                  <span>{a.libelle}{!a.requisitionnable && ' (réquisition impossible)'}</span>
                  <span className="article">{a.reference}</span>
                </div>
              ))}
            </Carte>
          )}
          {montreActionsOrdonnateur && <ActionsOrdonnateur d={d} />}
          {montreActionsComptable && <ActionsComptable d={d} />}
        </div>
      </div>
    </>
  );
}
