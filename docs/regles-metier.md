# Matrice de traçabilité : article, code, test

| Règle | Texte | Où elle est appliquée | Test |
|---|---|---|---|
| Fonctions d'ordonnateur et de comptable incompatibles | RGCP, art. 4 | `Engagement.decider`, `DossierPaiement.exigerComptableDistinct`, `ActeurCourant.nom` (jeton cumulant les deux rôles) | `EngagementTest.incompatibilite`, `DossierPaiementTest.Paiement.incompatibilite`, `EngagementsControllerTest.cumulDesFonctionsRefuse` |
| Accréditation des ordonnateurs auprès du comptable | RGCP, art. 5 | `Engagement.controlesAutomatiques`, `ControleValidite` (signature non qualifiée) | `EngagementTest.ordonnateurNonAccredite`, `ControleValiditeTest.chaquePointVerifie` |
| Ordre engagement, liquidation, ordonnancement, paiement | RGCP, art. 32 | `DossierPaiement` (machine à états) | `DossierPaiementTest.ordreDesEtapes` |
| Liquidation plafonnée à l'engagement et à la facture | RGCP, art. 33 et 34 | `Engagement.liquider`, `DossierPaiement.liquider`, contraintes SQL | `EngagementTest.liquidationPlafonnee`, `DossierPaiementTest.liquidationPlafonnee` |
| Numérotation des ordonnances unique et ininterrompue | RGCP, art. 36 | `CompteurOrdonnances` (compteur en table, sans séquence) | `ChaineDepenseIT.chaineComplete` |
| Paiement par virement au-delà de 1 500 DH | RGCP, art. 45 | `DossierPaiement.payer` | `DossierPaiementTest.Paiement.virementObligatoire` |
| Crédits non consommés annulés (fonctionnement) ou reportés (investissement) | RGCP, art. 87 | `LigneBudgetaire.soldeFinExercice` | `LigneBudgetaireTest` |
| Contrôle budgétaire de l'engagement : crédits, imputation, exactitude des calculs | Décret n° 2-07-1235, art. 3 | `Engagement.controlesAutomatiques`, `LigneBudgetaire.engager` (ligne verrouillée) | `EngagementTest.ControleBudgetaire` |
| Visa, suspension ou refus motivé ; observations groupées | art. 5 | `Engagement.decider` | `EngagementTest.DecisionDuComptable` |
| Délai de visa : 5 jours ouvrables, 12 pour un marché ; visa dû à défaut de réponse pour un marché | art. 6 | `Engagement.delaiDeVisa`, `Engagement.visaTacitementDu`, `CalendrierOuvrable` | `EngagementTest.Soumission`, `EngagementTest.visaTaciteDesMarches`, `CalendrierOuvrableTest` |
| Facture déposée seulement sur un engagement visé | art. 7 | `DepenseService.deposer` | `ChaineDepenseIT` |
| Contrôle de validité avant le visa de paiement | art. 8 | `ControleValidite`, `DossierPaiement.viserEtPayer` et `suspendre` | `ControleValiditeTest`, `DossierPaiementTest.Paiement` |
| Pas de contrôle de régularité jusqu'à 20 000 DH en biens et services | art. 10 | `Engagement.exigeControleDeRegularite` | `EngagementTest.seuilDuRegimeNormal` |
| Contrôle modulé : seuils de 100 000, 200 000 et 400 000 DH, délais de 4 et 10 jours | art. 12 et 13 | `Engagement.exigeControleDeRegularite`, `Engagement.delaiDeVisa` | `EngagementTest.seuilsDuControleModule`, `delaiDeVisaControleModule` |
| Une même créance n'est jamais payée deux fois | art. 12 | `DepenseService.deposer`, contrainte SQL `facture_unique` | `ChaineDepenseIT.chaineComplete` |
| Réquisition écrite de l'ordonnateur, responsabilité du comptable dégagée | art. 29 | `DossierPaiement.requerir` et `payerSurRequisition` | `DossierPaiementTest.requisitionAcceptee` |
| Réquisition impossible pour crédits, caractère libératoire ou visa d'engagement absent | art. 30 | `MotifSuspension.requisitionnable` | `requisitionIrrecevable`, `motifsBloquantsReverifies` |
| 60 jours pour payer : 45 pour ordonnancer, 15 pour le comptable | Décret n° 2-16-344, art. 2 | `Chronometre` | `ChronometreTest.echeancesLegales` |
| Pièces manquantes du bénéficiaire : délai suspendu | art. 3 | `DossierPaiement.signalerPiecesManquantes`, `Chronometre` | `ChronometreTest.suspensionImputableAuBeneficiaire`, `DossierPaiementTest.piecesEnAttente` |
| Après suspension du visa, au moins 5 jours restants au comptable | art. 4 | `Chronometre` | `ChronometreTest.reliquatMinimumDuComptable` |
| Service fait dans les 30 jours, sinon 31e jour | art. 5 et 6 | `DossierPaiement.certifierServiceFait` | `DossierPaiementTest.ServiceFait` |
| Intérêts moratoires : base, période, formule, taux trimestriel | art. 8 à 11 | `InteretsMoratoires`, `DossierPaiement.baseInteretsMoratoires` | `InteretsMoratoiresTest`, `ChaineDepenseIT` (139,73 DH) |
| Somme à valoir de 1 % | art. 12 | `Engagement.soumettre` | `EngagementTest.sommeAValoir` |
| Traçabilité de toutes les opérations | Bonne pratique de contrôle (RGCP, art. 60 et suivants) | `JournalAuditService`, déclencheurs SQL | `EntreeJournalTest`, `ChaineDepenseIT.journalEnAjoutSeul` |
