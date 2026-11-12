import { defineConfig } from '@playwright/test';

/**
 * Parcours complet dans le navigateur, sur la pile Docker Compose démarrée (profil « demo »).
 * Sous Windows, le navigateur Edge installé suffit : E2E_CANAL=msedge (valeur par défaut).
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 240_000,
  expect: { timeout: 15_000 },
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_URL ?? 'http://localhost:3000',
    channel: process.env.E2E_CANAL ?? 'msedge',
    locale: 'fr-FR',
    viewport: { width: 1360, height: 900 },
    screenshot: 'only-on-failure',
  },
});
