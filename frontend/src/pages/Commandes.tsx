import { useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { envoyer, televerser } from '../api';
import { Carte, Champ, Chargement, Entete, Refus, useAction, useLire } from '../composants';
import { dh, LIBELLES_COMMANDE } from '../format';
import type { Commande, DocumentJoint, Dossier } from '../types';

function FormulaireDepot({ commande, fermer }: { commande: Commande; fermer: () => void }) {
  const navigate = useNavigate();
  const [numero, setNumero] = useState('');
  const [montant, setMontant] = useState(String(commande.resteALiquider));
  const [retenue, setRetenue] = useState('0');
  const [rib, setRib] = useState('');
  const [facture, setFacture] = useState<File | null>(null);
  const depot = useAction(async () => {
    const dossier = await envoyer<Dossier>('/api/dossiers', {
      engagementId: commande.id,
      numeroFacture: numero,
      montantFacture: Number(montant),
      retenueGarantie: Number(retenue || 0),
      rib,
    });
    const formulaire = new FormData();
    formulaire.append('nature', 'FACTURE');
    formulaire.append('fichier', facture as File);
    await televerser<DocumentJoint>(`/api/dossiers/${dossier.id}/documents`, formulaire);
    return dossier;
  }, (dossier) => navigate(`/dossiers/${dossier.id}/document/accuse`));

  const soumettre = (e: FormEvent) => {
    e.preventDefault();
    depot.mutate(undefined);
  };
  const ribValide = /^\d{24}$/.test(rib.replace(/\s/g, ''));

  return (
    <Carte titre={`Déposer une facture : ${commande.objet}`}>
      <form className="formulaire" onSubmit={soumettre}>
        <div className="ligne-champs">
          <Champ libelle="Numéro de facture"><input required maxLength={60} value={numero} onChange={(e) => setNumero(e.target.value)} /></Champ>
          <Champ libelle="Montant TTC (DH)" aide={`Reste à facturer : ${dh(commande.resteALiquider)}`}><input required type="number" min="0.01" step="0.01" value={montant} onChange={(e) => setMontant(e.target.value)} /></Champ>
          <Champ libelle="Retenue de garantie (DH)"><input type="number" min="0" step="0.01" value={retenue} onChange={(e) => setRetenue(e.target.value)} /></Champ>
        </div>
        <Champ libelle="RIB" aide={rib && !ribValide ? 'Le RIB compte 24 chiffres.' : '24 chiffres.'}><input required maxLength={40} value={rib} onChange={(e) => setRib(e.target.value)} /></Champ>
        <Champ libelle="Facture" aide="PDF, PNG ou JPEG, 10 Mo au maximum."><input required type="file" accept="application/pdf,image/png,image/jpeg" onChange={(e) => setFacture(e.target.files?.[0] ?? null)} /></Champ>
        <Refus erreur={depot.error} />
        <div className="rangee-boutons">
          <button className="bouton primaire" disabled={depot.isPending || !facture}>{depot.isPending ? 'Dépôt…' : 'Déposer la facture'}</button>
          <button type="button" className="bouton" onClick={fermer}>Annuler</button>
        </div>
      </form>
    </Carte>
  );
}

export function Commandes() {
  const { data, error, isLoading } = useLire<Commande[]>('/api/engagements/commandes');
  const [choisie, setChoisie] = useState<Commande | null>(null);
  return (
    <>
      <Entete titre="Mes commandes" />
      {choisie && <FormulaireDepot key={choisie.id} commande={choisie} fermer={() => setChoisie(null)} />}
      <Carte>
        <Chargement erreur={error} chargement={isLoading} />
        {data?.length === 0 && <p className="sourd">Aucune commande visée pour votre entreprise.</p>}
        {data && data.length > 0 && (
          <div className="table-cadre">
            <table>
              <thead><tr><th>Objet</th><th>Commande</th><th>Service</th><th className="droite">Montant</th><th className="droite">Reste à facturer</th><th /></tr></thead>
              <tbody>
                {data.map((c) => (
                  <tr key={c.id}>
                    <td>{c.objet}</td>
                    <td>{LIBELLES_COMMANDE[c.typeCommande]}{c.referencePiece ? ` · ${c.referencePiece}` : ''}</td>
                    <td>{c.serviceOrdonnateur}</td>
                    <td className="droite nombre">{dh(c.montantPropose)}</td>
                    <td className="droite nombre">{dh(c.resteALiquider)}</td>
                    <td className="droite"><button className="bouton primaire" disabled={c.resteALiquider <= 0} onClick={() => setChoisie(c)}>Déposer une facture</button></td>
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
