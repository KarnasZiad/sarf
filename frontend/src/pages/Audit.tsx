import { Carte, Chargement, Entete, useLire } from '../composants';
import { dateHeure } from '../format';
import type { EntreeJournal, Integrite } from '../types';

export function Audit() {
  const integrite = useLire<Integrite>('/api/audit/integrite');
  const journal = useLire<EntreeJournal[]>('/api/audit?limite=200');
  return (
    <>
      <Entete titre="Journal d'audit" />
      <Chargement erreur={integrite.error} chargement={integrite.isLoading} />
      {integrite.data && (
        integrite.data.intacte
          ? <div className="message ok">Chaîne d'empreintes intacte : {integrite.data.entreesVerifiees} entrées vérifiées.</div>
          : <div className="message critique">Chaîne rompue : première entrée altérée n° {integrite.data.premiereEntreeAlteree}.</div>
      )}
      <Carte titre="Dernières opérations">
        <Chargement erreur={journal.error} chargement={journal.isLoading} />
        {journal.data && (
          <div className="table-cadre">
            <table>
              <thead><tr><th>N°</th><th>Horodatage</th><th>Acteur</th><th>Action</th><th>Objet</th><th>Détails</th><th>Empreinte</th></tr></thead>
              <tbody>
                {journal.data.map((e) => (
                  <tr key={e.id}>
                    <td className="nombre">{e.id}</td>
                    <td className="nombre petit">{dateHeure(e.horodatage)}</td>
                    <td>{e.acteur}</td>
                    <td className="mono">{e.action}</td>
                    <td>{e.typeObjet} {e.idObjet}</td>
                    <td className="petit">{e.details}</td>
                    <td className="mono" title={e.empreinte}>{e.empreinte.slice(0, 10)}…</td>
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
