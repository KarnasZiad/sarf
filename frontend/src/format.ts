const MONTANT = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const POURCENTAGE = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 });

export const dh = (valeur: number | null | undefined): string => `${MONTANT.format(valeur ?? 0)} DH`;
export const pct = (valeur: number): string => `${POURCENTAGE.format(valeur)} %`;
export const decimal = (valeur: number): string => POURCENTAGE.format(valeur);

export function date(valeur: string | null | undefined): string {
  if (!valeur) return '—';
  const [annee, mois, jour] = valeur.slice(0, 10).split('-');
  return `${jour}/${mois}/${annee}`;
}

export function dateHeure(valeur: string): string {
  return new Date(valeur).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
}

/** Nombre de jours de {@code debut} à {@code fin}, en dates calendaires. */
export function joursEntre(debut: string, fin: string): number {
  const utc = (d: string) => Date.parse(`${d.slice(0, 10)}T00:00:00Z`);
  return Math.round((utc(fin) - utc(debut)) / 86_400_000);
}

export function pluriel(n: number, mot: string): string {
  return `${n} ${mot}${Math.abs(n) > 1 ? 's' : ''}`;
}

const UNITES = ['zéro', 'un', 'deux', 'trois', 'quatre', 'cinq', 'six', 'sept', 'huit', 'neuf', 'dix', 'onze', 'douze', 'treize', 'quatorze', 'quinze', 'seize'];
const DIZAINES = ['', '', 'vingt', 'trente', 'quarante', 'cinquante', 'soixante'];

function sousCent(n: number): string {
  if (n < 17) return UNITES[n];
  if (n < 20) return `dix-${UNITES[n - 10]}`;
  if (n < 70) {
    const unite = n % 10;
    const dizaine = DIZAINES[Math.floor(n / 10)];
    if (unite === 0) return dizaine;
    return unite === 1 ? `${dizaine} et un` : `${dizaine}-${UNITES[unite]}`;
  }
  if (n < 80) return n === 71 ? 'soixante et onze' : `soixante-${sousCent(n - 60)}`;
  return n === 80 ? 'quatre-vingts' : `quatre-vingt-${sousCent(n - 80)}`;
}

function sousMille(n: number): string {
  const centaines = Math.floor(n / 100);
  const reste = n % 100;
  if (centaines === 0) return sousCent(n);
  const prefixe = centaines === 1 ? 'cent' : `${UNITES[centaines]} cent`;
  if (reste === 0) return centaines > 1 ? `${prefixe}s` : prefixe;
  return `${prefixe} ${sousCent(reste)}`;
}

const sansPluriel = (texte: string) => texte.replace(/(cent|vingt)s$/, '$1');

function entierEnLettres(n: number): string {
  if (n < 1000) return sousMille(n);
  if (n < 1_000_000) {
    const milliers = Math.floor(n / 1000);
    const debut = milliers === 1 ? 'mille' : `${sansPluriel(sousMille(milliers))} mille`;
    return n % 1000 === 0 ? debut : `${debut} ${sousMille(n % 1000)}`;
  }
  if (n < 1_000_000_000) {
    const millions = Math.floor(n / 1_000_000);
    const debut = `${sansPluriel(entierEnLettres(millions))} million${millions > 1 ? 's' : ''}`;
    return n % 1_000_000 === 0 ? debut : `${debut} ${entierEnLettres(n % 1_000_000)}`;
  }
  const milliards = Math.floor(n / 1_000_000_000);
  const debut = `${sansPluriel(entierEnLettres(milliards))} milliard${milliards > 1 ? 's' : ''}`;
  return n % 1_000_000_000 === 0 ? debut : `${debut} ${entierEnLettres(n % 1_000_000_000)}`;
}

/** Montant arrêté en toutes lettres, comme sur une ordonnance de paiement. */
export function montantEnLettres(valeur: number): string {
  const centimesTotal = Math.round(valeur * 100);
  const dirhams = Math.floor(centimesTotal / 100);
  const centimes = centimesTotal % 100;
  const texte = `${entierEnLettres(dirhams)} dirham${dirhams > 1 ? 's' : ''}`;
  return centimes === 0 ? texte : `${texte} et ${entierEnLettres(centimes)} centime${centimes > 1 ? 's' : ''}`;
}

export const LIBELLES_STATUT_DOSSIER: Record<string, string> = {
  DEPOSE: 'Facture déposée',
  SERVICE_FAIT: 'Service fait',
  LIQUIDE: 'Liquidé',
  ORDONNANCE: 'Chez le comptable',
  SUSPENDU: 'Visa suspendu',
  REQUISITIONNE: 'Réquisitionné',
  PAYE: 'Payé',
};

export const LIBELLES_STATUT_ENGAGEMENT: Record<string, string> = {
  SOUMIS: 'En attente de visa',
  VISE: 'Visé',
  SUSPENDU: 'Suspendu',
  REFUSE: 'Refusé',
};

export const LIBELLES_COMMANDE: Record<string, string> = {
  BON_DE_COMMANDE: 'Bon de commande',
  MARCHE: 'Marché',
  CONTRAT_DROIT_COMMUN: 'Contrat de droit commun',
};

export const LIBELLES_DOCUMENT: Record<string, string> = {
  FACTURE: 'Facture',
  ATTESTATION_SERVICE_FAIT: 'Attestation de service fait',
  BON_DE_COMMANDE: 'Bon de commande',
  MARCHE: 'Marché',
  CONTRAT: 'Contrat',
  RIB: 'RIB',
  AUTRE: 'Autre pièce',
};

export const MOTIFS_SUSPENSION: { code: string; libelle: string; requisitionnable: boolean }[] = [
  { code: 'CALCUL_LIQUIDATION_INEXACT', libelle: 'Calculs de liquidation inexacts', requisitionnable: true },
  { code: 'ABSENCE_VISA_ENGAGEMENT', libelle: "Visa préalable d'engagement absent", requisitionnable: false },
  { code: 'DEFAUT_CARACTERE_LIBERATOIRE', libelle: 'Règlement non libératoire (créancier ou RIB incorrect)', requisitionnable: false },
  { code: 'SIGNATURE_NON_QUALIFIEE', libelle: "Signature d'un ordonnateur non qualifié ou non accrédité", requisitionnable: true },
  { code: 'CREDITS_PAIEMENT_INSUFFISANTS', libelle: 'Crédits de paiement indisponibles ou insuffisants', requisitionnable: false },
  { code: 'PIECES_JUSTIFICATIVES_INCOMPLETES', libelle: 'Pièces justificatives incomplètes', requisitionnable: true },
];
