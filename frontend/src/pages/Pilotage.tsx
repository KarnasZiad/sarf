import { useState } from 'react';
import { Carte, Chargement, Entete, useAujourdhui, useLire } from '../composants';
import { decimal, dh, LIBELLES_STATUT_DOSSIER, pct } from '../format';
import type { TableauDeBord } from '../types';

function Barre({ valeur, couleur, libelle }: { valeur: number; couleur: string; libelle: string }) {
  return (
    <div className="jauge" title={libelle} role="img" aria-label={libelle}>
      <i style={{ width: `${Math.min(100, valeur)}%`, background: couleur }} />
    </div>
  );
}

export function Pilotage() {
  const aujourdhui = useAujourdhui();
  const anneeCourante = Number(aujourdhui.slice(0, 4));
  const [choix, setChoix] = useState<number | null>(null);
  const exercice = choix ?? anneeCourante;
  const { data: t, error, isLoading } = useLire<TableauDeBord>(`/api/pilotage?exercice=${exercice}`);
  return (
    <>
      <Entete titre="Pilotage">
        <select aria-label="Exercice" value={exercice} onChange={(e) => setChoix(Number(e.target.value))}>
          {[anneeCourante - 1, anneeCourante, anneeCourante + 1].map((a) => <option key={a} value={a}>Exercice {a}</option>)}
        </select>
      </Entete>
      <Chargement erreur={error} chargement={isLoading} />
      {t && (
        <>
          <div className="grille-tuiles">
            <div className="tuile"><b>{pct(t.tauxEngagement)}</b><span>Taux d'engagement</span></div>
            <div className="tuile"><b>{pct(t.tauxPaiement)}</b><span>Taux de paiement</span></div>
            <div className="tuile"><b>{t.delaiMoyenPaiementJours === null ? '—' : `${decimal(t.delaiMoyenPaiementJours)} j`}</b><span>Délai moyen de paiement</span></div>
            <div className="tuile"><b>{t.partPayesDansLeDelai === null ? '—' : pct(t.partPayesDansLeDelai)}</b><span>Payés dans le délai légal</span></div>
            <div className="tuile"><b>{t.dossiersEnRetardNonPayes}</b><span>Dossiers en retard, non payés</span></div>
            <div className="tuile"><b>{dh(t.interetsMoratoiresPayes + t.interetsMoratoiresCourus)}</b><span>Intérêts moratoires, payés et courus</span></div>
          </div>
          <Carte titre="Exécution par ligne budgétaire" droite={
            <div className="legende">
              <span><i style={{ background: 'var(--accent-clair)' }} />Engagé</span>
              <span><i style={{ background: 'var(--accent)' }} />Payé</span>
            </div>
          }>
            <div className="table-cadre">
              <table>
                <thead><tr><th>Ligne</th><th className="droite">Crédits ouverts</th><th>Exécution</th><th className="droite">Engagé</th><th className="droite">Payé</th></tr></thead>
                <tbody>
                  {t.lignes.map((l) => (
                    <tr key={l.imputation}>
                      <td><span className="mono">{l.imputation}</span><div className="petit sourd">{l.libelle}</div></td>
                      <td className="droite nombre">{dh(l.creditsOuverts)}</td>
                      <td>
                        <div className="barres-ligne">
                          <Barre valeur={l.tauxEngagement} couleur="var(--accent-clair)" libelle={`Engagé : ${pct(l.tauxEngagement)}`} />
                          <Barre valeur={l.tauxPaiement} couleur="var(--accent)" libelle={`Payé : ${pct(l.tauxPaiement)}`} />
                        </div>
                      </td>
                      <td className="droite nombre">{pct(l.tauxEngagement)}</td>
                      <td className="droite nombre">{pct(l.tauxPaiement)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Carte>
          <div className="deux-colonnes">
            <Carte titre="Dossiers par étape">
              {Object.keys(t.dossiersParStatut).length === 0 && <p className="sourd">Aucun dossier sur cet exercice.</p>}
              <table>
                <tbody>
                  {Object.entries(t.dossiersParStatut).map(([statut, nombre]) => (
                    <tr key={statut}><td>{LIBELLES_STATUT_DOSSIER[statut]}</td><td className="droite nombre"><b>{nombre}</b></td></tr>
                  ))}
                </tbody>
              </table>
            </Carte>
            <Carte titre="Visas d'engagement">
              <table>
                <tbody>
                  <tr><td>En attente</td><td className="droite nombre"><b>{t.engagementsEnAttenteDeVisa}</b></td></tr>
                  <tr><td>En retard</td><td className="droite nombre"><b>{t.engagementsEnRetardDeVisa}</b></td></tr>
                </tbody>
              </table>
            </Carte>
          </div>
        </>
      )}
    </>
  );
}
