export interface LigneBudgetaire {
  id: number;
  exercice: number;
  imputation: string;
  libelle: string;
  nature: 'FONCTIONNEMENT' | 'INVESTISSEMENT';
  creditsOuverts: number;
  montantEngage: number;
  montantPaye: number;
  disponibleAEngager: number;
}

export interface ServiceOrdonnateur {
  code: string;
  libelle: string;
  regime: 'NORMAL' | 'MODULE';
}

export interface Observation {
  code: string;
  reference: string;
  message: string;
}

export type TypeCommande = 'MARCHE' | 'BON_DE_COMMANDE' | 'CONTRAT_DROIT_COMMUN';
export type StatutEngagement = 'SOUMIS' | 'VISE' | 'SUSPENDU' | 'REFUSE';
export type TypeDecision = 'VISA' | 'SUSPENSION' | 'REFUS';

export interface LigneProposition {
  designation: string;
  quantite: number;
  prixUnitaire: number;
  montant?: number;
}

export interface Engagement {
  id: number;
  exercice: number;
  serviceOrdonnateur: string;
  regime: string;
  ligneBudgetaireId: number;
  imputation: string;
  objet: string;
  typeCommande: TypeCommande;
  referencePiece: string | null;
  fournisseurNom: string;
  fournisseurIdentifiant: string;
  lignes: LigneProposition[];
  montantPropose: number;
  sommeAValoir: number;
  montantAEngager: number;
  montantLiquide: number;
  statut: StatutEngagement;
  dateDepot: string;
  echeanceVisa: string;
  ordonnateur: string;
  controleDeRegulariteRequis: boolean;
  enRetardDeVisa: boolean;
  visaTacitementDu: boolean;
  anomalies: Observation[];
  decisions: { type: TypeDecision; date: string; comptable: string; observations: string[] }[];
}

export interface PreControle {
  sommeAValoir: number;
  montantAEngager: number;
  echeanceVisa: string;
  controleDeRegulariteRequis: boolean;
  anomalies: Observation[];
}

export interface Commande {
  id: number;
  exercice: number;
  objet: string;
  typeCommande: TypeCommande;
  referencePiece: string | null;
  serviceOrdonnateur: string;
  montantPropose: number;
  resteALiquider: number;
}

export type StatutDossier =
  | 'DEPOSE'
  | 'SERVICE_FAIT'
  | 'LIQUIDE'
  | 'ORDONNANCE'
  | 'SUSPENDU'
  | 'REQUISITIONNE'
  | 'PAYE';

export interface Dossier {
  id: number;
  engagementId: number;
  exercice: number;
  serviceOrdonnateur: string;
  ordonnateur: string;
  imputation: string;
  creancierNom: string;
  creancierIdentifiant: string;
  creancierRib: string | null;
  numeroFacture: string;
  montantFacture: number;
  retenueGarantie: number;
  dateDepot: string;
  deposant: string;
  dateCertification: string | null;
  dateServiceFait: string | null;
  montantLiquide: number;
  numeroOrdonnance: string | null;
  dateOrdonnancement: string | null;
  statut: StatutDossier;
  datePaiement: string | null;
  modeReglement: 'VIREMENT' | 'ESPECES' | null;
  payeSurRequisition: boolean;
  interetsMoratoires: number;
  delais: {
    echeanceOrdonnancement: string | null;
    echeancePaiement: string | null;
    echeanceVisaComptable: string | null;
    joursSuspendus: number;
    joursDeRetard: number;
    ordonnancementEnRetard: boolean;
    visaComptableEnRetard: boolean;
  };
  anomalies: { code: string; reference: string; libelle: string; requisitionnable: boolean }[];
  suspensions: { debut: string; fin: string | null; motif: string }[];
  decisions: { type: string; date: string; comptable: string; motifs: string[]; note: string | null }[];
  requisition: { date: string; ordonnateur: string; motif: string } | null;
  echeanceCertification: string;
}

export type NatureDocument =
  | 'FACTURE'
  | 'ATTESTATION_SERVICE_FAIT'
  | 'BON_DE_COMMANDE'
  | 'MARCHE'
  | 'CONTRAT'
  | 'RIB'
  | 'AUTRE';

export interface DocumentJoint {
  id: number;
  nature: NatureDocument;
  nomFichier: string;
  typeContenu: string;
  taille: number;
  empreinte: string;
  deposePar: string;
  deposeLe: string;
}

export interface InteretsCalcul {
  total: { valeur: number };
  jours: number;
  tranches: { annee: number; trimestre: number; jours: number; taux: number; montant: { valeur: number } }[];
}

export interface TableauDeBord {
  exercice: number;
  lignes: {
    imputation: string;
    libelle: string;
    nature: string;
    creditsOuverts: number;
    montantEngage: number;
    montantPaye: number;
    tauxEngagement: number;
    tauxPaiement: number;
  }[];
  creditsOuverts: number;
  montantEngage: number;
  montantPaye: number;
  tauxEngagement: number;
  tauxPaiement: number;
  engagementsEnAttenteDeVisa: number;
  engagementsEnRetardDeVisa: number;
  dossiersParStatut: Record<string, number>;
  delaiMoyenPaiementJours: number | null;
  partPayesDansLeDelai: number | null;
  dossiersEnRetardNonPayes: number;
  interetsMoratoiresPayes: number;
  interetsMoratoiresCourus: number;
}

export interface EntreeJournal {
  id: number;
  horodatage: string;
  acteur: string;
  action: string;
  typeObjet: string;
  idObjet: string;
  details: string | null;
  empreintePrecedente: string;
  empreinte: string;
}

export interface Integrite {
  intacte: boolean;
  entreesVerifiees: number;
  premiereEntreeAlteree: number | null;
}

export interface TauxTrimestriel {
  annee: number;
  trimestre: number;
  taux: number;
  source: string;
}

export interface JourFerie {
  jour: string;
  libelle: string;
}

export interface Accreditation {
  utilisateur: string;
  serviceCode: string;
}

export interface CompteFournisseur {
  utilisateur: string;
  fournisseurIdentifiant: string;
  fournisseurNom: string;
}
