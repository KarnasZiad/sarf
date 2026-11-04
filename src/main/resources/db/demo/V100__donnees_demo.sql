-- Données de démonstration, entièrement fictives (profil « demo » uniquement).

INSERT INTO services_ordonnateurs (code, libelle, regime) VALUES
    ('SRV-DSI', 'Direction des systèmes d''information (service fictif)', 'NORMAL'),
    ('SRV-LOG', 'Division de la logistique (service fictif, contrôle modulé)', 'MODULE');

-- L'ordonnateur de démonstration est accrédité auprès du comptable pour les deux services.
INSERT INTO accreditations (utilisateur, service_code) VALUES
    ('ordonnateur.amal', 'SRV-DSI'),
    ('ordonnateur.amal', 'SRV-LOG');

INSERT INTO lignes_budgetaires (exercice, imputation, libelle, nature, credits_ouverts) VALUES
    (2026, '1.2.1.10.31', 'Achat de matériel informatique (ligne fictive)', 'FONCTIONNEMENT', 2000000.00),
    (2026, '1.2.1.10.45', 'Maintenance des applications (ligne fictive)', 'FONCTIONNEMENT', 600000.00),
    (2026, '2.1.3.20.11', 'Équipement de la salle serveurs (ligne fictive)', 'INVESTISSEMENT', 5000000.00);

-- Taux d'exemple : à remplacer par les décisions trimestrielles du Trésorier général du Royaume.
INSERT INTO taux_interets_moratoires (annee, trimestre, taux, source)
SELECT annee, trimestre, 3.40, 'Valeur d''exemple fictive, à remplacer par la décision trimestrielle du TGR'
  FROM generate_series(2026, 2027) AS annee
 CROSS JOIN generate_series(1, 4) AS trimestre;
