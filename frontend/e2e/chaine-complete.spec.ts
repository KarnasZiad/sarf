import { expect, test } from '@playwright/test';
import type { Browser, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** Mot de passe des comptes de démonstration : variable d'environnement, sinon fichier .env. */
function motDePasse(): string {
  if (process.env.SARF_DEMO_PASSWORD) return process.env.SARF_DEMO_PASSWORD;
  const ligne = readFileSync(resolve(import.meta.dirname, '../../.env'), 'utf8').split(/\r?\n/).find((l) => l.startsWith('SARF_DEMO_PASSWORD='));
  if (!ligne) throw new Error('SARF_DEMO_PASSWORD introuvable');
  return ligne.slice('SARF_DEMO_PASSWORD='.length);
}

const CAPTURES = process.env.E2E_CAPTURES;
const PDF = { name: 'piece.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nfacture de test\n') };
const FAUX_PDF = { name: 'faux.pdf', mimeType: 'application/pdf', buffer: Buffer.from('<html>pas un pdf</html>') };

async function capture(page: Page, nom: string) {
  if (CAPTURES) await page.screenshot({ path: resolve(CAPTURES, `${nom}.png`), fullPage: true });
}

/** Ouvre une session distincte par acteur, connectée par la vraie page Keycloak. */
async function connecter(browser: Browser, utilisateur: string): Promise<Page> {
  const contexte = await browser.newContext();
  const page = await contexte.newPage();
  await page.goto('/');
  await page.locator('#username').fill(utilisateur);
  await page.locator('#password').fill(motDePasse());
  await page.locator('#kc-login').click();
  await expect(page.locator('.barre')).toBeVisible();
  return page;
}

async function allerAu(admin: Page, jour: string) {
  await admin.goto('/administration');
  await admin.getByLabel('Nouvelle date').fill(jour);
  await admin.getByRole('button', { name: 'Aller à cette date' }).click();
  const [annee, mois, j] = jour.split('-');
  await expect(admin.locator('.bandeau-demo')).toContainText(`${j}/${mois}/${annee}`);
}

async function deposerFacture(fournisseur: Page, suffixe: string, numero: string) {
  await fournisseur.goto('/commandes');
  await fournisseur.getByRole('row', { name: new RegExp(suffixe) }).getByRole('button', { name: 'Déposer une facture' }).click();
  await fournisseur.getByLabel('Numéro de facture').fill(numero);
  await fournisseur.getByLabel('RIB').fill('011780000012345678901234');
  await fournisseur.getByLabel('Facture', { exact: false }).last().setInputFiles(PDF);
  await fournisseur.getByRole('button', { name: 'Déposer la facture' }).click();
}

test('suspension, correction, puis réquisition refusée et acceptée', async ({ browser }) => {
  const suffixe = Date.now().toString().slice(-7);
  const admin = await connecter(browser, 'admin.sarf');
  const ordonnateur = await connecter(browser, 'ordonnateur.amal');
  const comptable = await connecter(browser, 'comptable.karim');
  const fournisseur = await connecter(browser, 'fournisseur.atlas');
  await allerAu(admin, '2026-06-01');

  // Un engagement sans pièce peut être envoyé, mais le comptable ne peut pas le viser.
  await ordonnateur.goto('/engagements/nouveau');
  await ordonnateur.getByLabel('Ligne budgétaire').selectOption({ index: 1 });
  await ordonnateur.getByLabel('Service ordonnateur').selectOption('SRV-DSI');
  await ordonnateur.getByLabel('Objet', { exact: true }).fill(`Imprimantes réseau ${suffixe}`);
  await ordonnateur.getByLabel('Fournisseur', { exact: true }).fill('Atlas Informatique SARL');
  await ordonnateur.getByLabel('Identifiant du fournisseur (ICE)').fill('001234567000089');
  await ordonnateur.getByLabel('Désignation').fill('Imprimante');
  await ordonnateur.getByLabel('Quantité').fill('5');
  await ordonnateur.getByLabel('Prix unitaire (DH)').fill('9000');
  await expect(ordonnateur.getByText('La pièce qui fonde l\'engagement')).toBeVisible();
  await ordonnateur.getByRole('button', { name: 'Envoyer au comptable' }).click();
  await expect(ordonnateur).toHaveURL(/\/engagements\/\d+$/);
  const cheminEngagement = new URL(ordonnateur.url()).pathname;

  await comptable.goto(cheminEngagement);
  await expect(comptable.getByRole('button', { name: 'Viser et réserver les crédits' })).toBeDisabled();
  await comptable.getByLabel('Observations').fill('Joindre le bon de commande signé');
  await comptable.getByRole('button', { name: 'Suspendre' }).click();
  await expect(comptable.locator('h1')).toContainText('Suspendu');

  // L'ordonnateur corrige dans le même formulaire et représente l'engagement.
  await ordonnateur.goto(cheminEngagement);
  await expect(ordonnateur.getByText('Joindre le bon de commande signé')).toBeVisible();
  await ordonnateur.getByRole('link', { name: 'Corriger et renvoyer' }).click();
  await ordonnateur.getByLabel('Référence de la pièce').fill(`BC-${suffixe}`);
  await expect(ordonnateur.getByText('Aucune anomalie : le dossier peut être envoyé.')).toBeVisible();
  await ordonnateur.getByRole('button', { name: 'Renvoyer au comptable' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('En attente de visa');

  await comptable.goto(cheminEngagement);
  await comptable.getByRole('button', { name: 'Viser et réserver les crédits' }).click();
  await expect(comptable.locator('h1')).toContainText('Visé');

  await deposerFacture(fournisseur, suffixe, `F-${suffixe}`);
  await expect(fournisseur.getByRole('heading', { name: 'Accusé de dépôt de facture' })).toBeVisible();
  const cheminDossier = new URL(fournisseur.url()).pathname.replace('/document/accuse', '');

  await ordonnateur.goto(cheminDossier);
  await ordonnateur.getByRole('button', { name: 'Certifier le service fait' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Service fait');
  await ordonnateur.getByRole('button', { name: 'Liquider' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Liquidé');
  await ordonnateur.getByRole('button', { name: 'Émettre l\'ordonnance' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Chez le comptable');

  // Suspension pour crédits : la réquisition est refusée, avec l'article 30.
  const suspendre = async (motif: string, note: string) => {
    await comptable.goto(cheminDossier);
    await comptable.getByLabel(motif).check();
    await comptable.getByLabel('Note motivée').fill(note);
    await comptable.getByRole('button', { name: 'Suspendre le visa' }).click();
    await expect(comptable.locator('h1')).toContainText('Visa suspendu');
  };
  await suspendre('Crédits de paiement indisponibles', 'Crédits de paiement non disponibles');
  await ordonnateur.goto(cheminDossier);
  await ordonnateur.getByLabel('Réquisition écrite').fill('Urgence de service');
  await ordonnateur.getByRole('button', { name: 'Requérir le paiement' }).click();
  await expect(ordonnateur.getByRole('alert')).toContainText('Décret n° 2-07-1235, art. 30');
  await capture(ordonnateur, '12-requisition-refusee');
  await ordonnateur.getByRole('button', { name: 'Dossier régularisé, renvoyer au comptable' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Chez le comptable');

  // Suspension pour signature : la réquisition est recevable et le comptable paie.
  await suspendre('Signature d\'un ordonnateur', 'Délégation de signature non transmise');
  await ordonnateur.goto(cheminDossier);
  await ordonnateur.getByLabel('Réquisition écrite').fill('Délégation signée, transmise par courrier');
  await ordonnateur.getByRole('button', { name: 'Requérir le paiement' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Réquisitionné');
  await comptable.goto(cheminDossier);
  await comptable.getByRole('button', { name: 'Payer sur réquisition' }).click();
  await expect(comptable.locator('h1')).toContainText('Payé');
  await expect(comptable.getByText('Paiement sur réquisition', { exact: true })).toBeVisible();
});

test('toute la dépense se fait à l’écran, de l’engagement au paiement en retard', async ({ browser }) => {
  const suffixe = Date.now().toString().slice(-7);
  const admin = await connecter(browser, 'admin.sarf');
  const ordonnateur = await connecter(browser, 'ordonnateur.amal');
  const comptable = await connecter(browser, 'comptable.karim');
  const fournisseur = await connecter(browser, 'fournisseur.atlas');
  const auditeur = await connecter(browser, 'auditeur.salma');

  await allerAu(admin, '2026-03-02');

  // 1. L'ordonnateur saisit l'engagement : le pré-contrôle signale la pièce manquante avant l'envoi.
  await ordonnateur.goto('/engagements/nouveau');
  await ordonnateur.getByLabel('Ligne budgétaire').selectOption({ index: 1 });
  await ordonnateur.getByLabel('Service ordonnateur').selectOption('SRV-DSI');
  await ordonnateur.getByLabel('Objet', { exact: true }).fill(`Vingt ordinateurs portables ${suffixe}`);
  await ordonnateur.getByLabel('Fournisseur', { exact: true }).fill('Atlas Informatique SARL');
  await ordonnateur.getByLabel('Identifiant du fournisseur (ICE)').fill('001234567000089');
  await ordonnateur.getByLabel('Désignation').fill('Ordinateur portable');
  await ordonnateur.getByLabel('Quantité').fill('20');
  await ordonnateur.getByLabel('Prix unitaire (DH)').fill('5000');
  await expect(ordonnateur.getByText('La pièce qui fonde l\'engagement')).toBeVisible();
  await capture(ordonnateur, '01-precontrole-anomalie');

  await ordonnateur.getByLabel('Référence de la pièce').fill(`BC-2026-${suffixe}`);
  await expect(ordonnateur.getByText('Aucune anomalie : le dossier peut être envoyé.')).toBeVisible();
  await expect(ordonnateur.locator('.liste-defs')).toContainText('09/03/2026');
  await ordonnateur.getByLabel('Bon de commande, marché ou contrat').setInputFiles(PDF);
  await capture(ordonnateur, '02-precontrole-ok');
  await ordonnateur.getByRole('button', { name: 'Envoyer au comptable' }).click();
  await expect(ordonnateur).toHaveURL(/\/engagements\/\d+$/);
  await expect(ordonnateur.locator('h1')).toContainText('En attente de visa');
  await expect(ordonnateur.getByText('piece.pdf')).toBeVisible();
  const engagementId = ordonnateur.url().split('/').pop();

  // 2. Le comptable trouve l'engagement dans sa corbeille et le vise.
  await comptable.goto('/');
  await comptable.getByRole('link', { name: new RegExp(`Viser l’engagement.*${suffixe}`) }).click();
  await expect(comptable).toHaveURL(new RegExp(`/engagements/${engagementId}$`));
  await capture(comptable, '03-visa-comptable');
  await comptable.getByRole('button', { name: 'Viser et réserver les crédits' }).click();
  await expect(comptable.locator('h1')).toContainText('Visé');

  // 3. Le fournisseur dépose sa facture : un faux PDF est refusé, l'accusé est délivré.
  const numero = `F-${suffixe}`;
  const deposer = async (fichier: typeof PDF) => {
    await fournisseur.goto('/commandes');
    await fournisseur.getByRole('row', { name: new RegExp(suffixe) }).getByRole('button', { name: 'Déposer une facture' }).click();
    await fournisseur.getByLabel('Numéro de facture').fill(numero);
    await fournisseur.getByLabel('RIB').fill('011780000012345678901234');
    await fournisseur.getByLabel('Facture', { exact: false }).last().setInputFiles(fichier);
    await fournisseur.getByRole('button', { name: 'Déposer la facture' }).click();
  };
  await deposer(PDF);
  await expect(fournisseur.getByRole('heading', { name: 'Accusé de dépôt de facture' })).toBeVisible();
  await expect(fournisseur.locator('.document img')).toBeVisible();
  await capture(fournisseur, '04-accuse-depot');
  const dossierUrl = fournisseur.url().replace('/document/accuse', '');
  const cheminDossier = new URL(dossierUrl).pathname;

  // Le double dépôt est refusé avec l'article qui le fonde.
  await deposer(PDF);
  await expect(fournisseur.getByRole('alert')).toContainText('Décret n° 2-07-1235, art. 12');
  await capture(fournisseur, '05-double-depot-refuse');

  // Une pièce qui n'est pas un vrai PDF est refusée, même renommée.
  await fournisseur.goto(cheminDossier);
  await fournisseur.getByLabel('Fichier à joindre').setInputFiles(FAUX_PDF);
  await expect(fournisseur.getByRole('alert')).toContainText('Seuls les fichiers PDF');

  // 4. Service fait, liquidation et ordonnance.
  await allerAu(admin, '2026-03-12');
  await ordonnateur.goto(cheminDossier);
  await ordonnateur.getByRole('button', { name: 'Certifier le service fait' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Service fait');
  await ordonnateur.getByRole('button', { name: 'Liquider' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Liquidé');
  await ordonnateur.getByRole('button', { name: 'Émettre l\'ordonnance' }).click();
  await expect(ordonnateur.locator('h1')).toContainText('Chez le comptable');
  await expect(ordonnateur.getByText('11/05/2026').first()).toBeVisible();
  await capture(ordonnateur, '06-ordonnance-emise');
  await ordonnateur.getByRole('link', { name: 'Ordonnance', exact: true }).click();
  await expect(ordonnateur.locator('.document')).toContainText('cent mille dirhams');
  await capture(ordonnateur, '07-document-ordonnance');

  // 5. Le comptable paie en retard : les intérêts sont calculés.
  await allerAu(admin, '2026-05-26');
  await comptable.goto(cheminDossier);
  await expect(comptable.getByText('15 jours de retard')).toBeVisible();
  await expect(comptable.getByText('139,73').first()).toBeVisible();
  await capture(comptable, '08-controle-paiement-retard');
  await comptable.getByRole('button', { name: /Viser et payer/ }).click();
  await expect(comptable.locator('h1')).toContainText('Payé');

  // 6. Le fournisseur suit son paiement et l'auditeur vérifie le journal.
  await fournisseur.goto(cheminDossier);
  await expect(fournisseur.locator('h1')).toContainText('Payé');
  await fournisseur.getByRole('link', { name: 'Avis de paiement' }).click();
  await expect(fournisseur.locator('.document')).toContainText('139,73');
  await capture(fournisseur, '09-avis-paiement');

  await auditeur.goto('/audit');
  await expect(auditeur.getByText('Chaîne d\'empreintes intacte')).toBeVisible();
  await capture(auditeur, '10-journal-audit');
  await auditeur.goto('/pilotage');
  await expect(auditeur.getByText('Taux d\'engagement')).toBeVisible();
  await capture(auditeur, '11-pilotage');
});
