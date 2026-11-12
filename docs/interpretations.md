# Interprétations et simplifications

Un prototype doit dire clairement où il interprète les textes et où il simplifie.

## Interprétations

| Sujet | Texte | Choix retenu |
|---|---|---|
| « Jours ouvrables francs » | Décret n° 2-07-1235, art. 6 | Le jour du dépôt n'est pas compté. Les samedis, dimanches et jours fériés de la table `jours_feries` ne comptent pas. |
| « 31e jour à compter de la date de dépôt » | Décret n° 2-16-344, art. 6 | Date de dépôt + 31 jours : le premier jour du délai est le lendemain du dépôt. |
| Retard sur plusieurs trimestres | Décret n° 2-16-344, art. 11 | Chaque jour de retard est compté au taux de son trimestre. Le total exact est arrondi au centime à la fin. |
| Trimestre sans taux publié | art. 11, al. 2 | Le taux du trimestre précédent est retenu, en remontant jusqu'à 8 trimestres. Au-delà, le calcul est refusé plutôt que faux. |
| Délai global et suspension du visa | art. 3 et 4 | Seules les suspensions imputables au bénéficiaire (pièces manquantes) prolongent les 60 jours. Une suspension du visa par le comptable redonne la main à l'ordonnateur dans le même délai global, avec au moins 5 jours pour le comptable au retour. |
| Contrôle de régularité automatisé | art. 3 | La machine vérifie seulement la présence de la pièce d'engagement au-delà du seuil. L'appréciation de la régularité au regard des textes reste humaine : le comptable ajoute ses observations. |

## Simplifications

- **Une seule enveloppe de crédits par ligne**, sans distinguer crédits d'engagement et crédits de
  paiement, ni programmes et projets de la LOF.
- **Pas de sous-ordonnateurs** ni de délégations de crédits (RGCP, art. 64). L'ordonnateur qui
  engage est celui qui certifie, liquide et ordonnance.
- **Dépenses de biens et services par commande publique uniquement.** Les dépenses de personnel,
  les marchés négociés, les fonds de concours et la défense nationale sont hors périmètre.
- **Le refus de visa est définitif.** La procédure de passer outre (art. 28 : Trésorier général du
  Royaume, puis chef du gouvernement) n'est pas modélisée.
- **Les intérêts moratoires sont calculés et enregistrés au paiement**, mais leur ordonnancement
  séparé dans les 30 jours (art. 9 et 13) n'est pas modélisé.
- **La transmission de l'ordonnance au comptable** a lieu le jour de l'ordonnancement, sans
  bordereau d'émission distinct (RGCP, art. 86).
- **Pièces justificatives** : seuls le service fait et le RIB sont vérifiés, la nomenclature
  officielle n'étant pas reprise.
- **Fournisseurs** : un compte est rattaché à un fournisseur par l'administrateur. Il ne voit et ne
  facture que ses commandes, et ne consulte que les factures qu'il a déposées.
- **Signature électronique** : la plateforme réelle exige une signature par certificat. Sarf
  horodate le dépôt et délivre un accusé avec code QR, sans signature ni tiers de confiance.
- **Facture jointe** : l'écran de dépôt exige le fichier. L'API accepte un dépôt sans pièce, et
  l'absence de facture jointe ne bloque pas les étapes suivantes.
