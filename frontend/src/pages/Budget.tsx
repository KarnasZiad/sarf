import { useState } from 'react';
import type { FormEvent } from 'react';
import { envoyer } from '../api';
import { Carte, Champ, Chargement, Entete, Refus, useAction, useAujourdhui, useLire, useUtilisateur } from '../composants';
import { dh } from '../format';
import type { LigneBudgetaire } from '../types';

function FormulaireLigne({ exercice }: { exercice: number }) {
  const [imputation, setImputation] = useState('');
  const [libelle, setLibelle] = useState('');
  const [nature, setNature] = useState('FONCTIONNEMENT');
  const [credits, setCredits] = useState('');
  const ouverture = useAction(() => envoyer('/api/lignes-budgetaires', { exercice, imputation, libelle, nature, creditsOuverts: Number(credits) }), () => {
    setImputation('');
    setLibelle('');
    setCredits('');
  });
  const soumettre = (e: FormEvent) => {
    e.preventDefault();
    ouverture.mutate(undefined);
  };
  return (
    <Carte titre={`Ouvrir une ligne sur l'exercice ${exercice}`}>
      <form className="formulaire" onSubmit={soumettre}>
        <div className="ligne-champs">
          <Champ libelle="Imputation" aide="Chiffres séparés par des points."><input required pattern="\d+(\.\d+)*" value={imputation} onChange={(e) => setImputation(e.target.value)} /></Champ>
          <Champ libelle="Libellé"><input required value={libelle} onChange={(e) => setLibelle(e.target.value)} /></Champ>
          <Champ libelle="Nature">
            <select value={nature} onChange={(e) => setNature(e.target.value)}>
              <option value="FONCTIONNEMENT">Fonctionnement</option>
              <option value="INVESTISSEMENT">Investissement</option>
            </select>
          </Champ>
          <Champ libelle="Crédits ouverts (DH)"><input required type="number" min="0" step="0.01" value={credits} onChange={(e) => setCredits(e.target.value)} /></Champ>
        </div>
        <Refus erreur={ouverture.error} />
        <div><button className="bouton primaire" disabled={ouverture.isPending}>Ouvrir la ligne</button></div>
      </form>
    </Carte>
  );
}

export function Budget() {
  const moi = useUtilisateur();
  const aujourdhui = useAujourdhui();
  const anneeCourante = Number(aujourdhui.slice(0, 4));
  const [choix, setChoix] = useState<number | null>(null);
  const exercice = choix ?? anneeCourante;
  const { data, error, isLoading } = useLire<LigneBudgetaire[]>(`/api/lignes-budgetaires?exercice=${exercice}`);
  return (
    <>
      <Entete titre="Budget">
        <select aria-label="Exercice" value={exercice} onChange={(e) => setChoix(Number(e.target.value))}>
          {[anneeCourante - 1, anneeCourante, anneeCourante + 1].map((a) => <option key={a} value={a}>Exercice {a}</option>)}
        </select>
      </Entete>
      {moi.a('ADMINISTRATEUR') && <FormulaireLigne exercice={exercice} />}
      <Carte>
        <Chargement erreur={error} chargement={isLoading} />
        {data?.length === 0 && <p className="sourd">Aucune ligne ouverte sur cet exercice.</p>}
        {data && data.length > 0 && (
          <div className="table-cadre">
            <table>
              <thead><tr><th>Imputation</th><th>Libellé</th><th>Nature</th><th className="droite">Crédits ouverts</th><th className="droite">Engagé</th><th className="droite">Payé</th><th className="droite">Disponible</th></tr></thead>
              <tbody>
                {data.map((l) => (
                  <tr key={l.id}>
                    <td className="mono">{l.imputation}</td>
                    <td>{l.libelle}</td>
                    <td>{l.nature === 'FONCTIONNEMENT' ? 'Fonctionnement' : 'Investissement'}</td>
                    <td className="droite nombre">{dh(l.creditsOuverts)}</td>
                    <td className="droite nombre">{dh(l.montantEngage)}</td>
                    <td className="droite nombre">{dh(l.montantPaye)}</td>
                    <td className="droite nombre"><b>{dh(l.disponibleAEngager)}</b></td>
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
