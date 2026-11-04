-- Jours fériés nationaux à date fixe. Les fêtes religieuses (Aïd Al Fitr, Aïd Al Adha, 1er Moharram,
-- Aïd Al Mawlid) suivent le calendrier hégirien : l'administrateur les ajoute chaque année
-- (POST /api/jours-feries), de même que tout jour férié créé après cette liste.
INSERT INTO jours_feries (jour, libelle)
SELECT make_date(annee, mois, jour), libelle
  FROM generate_series(2026, 2030) AS annee
 CROSS JOIN (VALUES
     (1, 1, 'Nouvel an'),
     (1, 11, 'Manifeste de l''Indépendance'),
     (1, 14, 'Nouvel an amazigh'),
     (5, 1, 'Fête du Travail'),
     (7, 30, 'Fête du Trône'),
     (8, 14, 'Allégeance de Oued Eddahab'),
     (8, 20, 'Révolution du Roi et du Peuple'),
     (8, 21, 'Fête de la Jeunesse'),
     (11, 6, 'Marche verte'),
     (11, 18, 'Fête de l''Indépendance')
 ) AS fixes (mois, jour, libelle);
