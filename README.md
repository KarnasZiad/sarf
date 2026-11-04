# Sarf

**Prototype pédagogique de la chaîne d'exécution de la dépense publique de l'État marocain** :
engagement, visa du comptable, service fait, liquidation, ordonnancement, contrôle de validité et
paiement, avec le délai légal de 60 jours et le calcul des intérêts moratoires.

*Sarf* (صرف) vient de « الآمر بالصرف », l'ordonnateur.

> **Avertissement.** Sarf n'est pas une copie de la GID ni d'aucun système de la Trésorerie générale
> du Royaume, dont le fonctionnement interne n'est pas public. Il applique les règles des **textes
> réglementaires publiés** (voir [les sources](docs/sources.md)). Toutes les données sont fictives.

## L'histoire qu'il raconte

Un ministère achète vingt ordinateurs pour 100 000 DH.

1. **Engagement.** L'ordonnateur propose l'engagement. Il est majoré d'une somme à valoir de 1 %
   pour d'éventuels intérêts moratoires.
2. **Visa d'engagement.** Le comptable public contrôle les crédits, l'imputation et l'exactitude
   des calculs, puis la régularité au-delà de certains seuils. Il vise, suspend ou refuse en
   motivant sa décision. Au visa, les crédits sont réservés sur la ligne budgétaire.
3. **Facture.** Le fournisseur dépose sa facture. Une même facture ne peut pas être déposée deux
   fois.
4. **Service fait.** L'ordonnateur le certifie dans les 30 jours. Au-delà, la date retenue est le
   31e jour, et les 60 jours du délai de paiement commencent à courir.
5. **Liquidation et ordonnancement.** Le montant dû est arrêté, puis l'ordre de payer est émis
   avec un numéro d'une série unique et ininterrompue.
6. **Contrôle de validité et paiement.** Le comptable vise et paie par virement. Il peut aussi
   suspendre avec une note motivée ; l'ordonnateur peut alors requérir le paiement, sauf pour les
   trois motifs que la loi rend impossibles à lever.
7. **Intérêts moratoires.** Si l'administration paie après le 60e jour, ils sont calculés
   automatiquement : créance × jours × taux / 365, au taux de chaque trimestre.

Chaque action est inscrite dans un **journal d'audit chaîné par empreintes SHA-256**, que la base
de données interdit de modifier.

## Les règles appliquées

La [matrice de traçabilité](docs/regles-metier.md) relie chaque règle à son article, à la classe
qui l'applique et au test qui la vérifie. Les tests portent le numéro de l'article dans leur nom,
par exemple : « Décret n° 2-07-1235, art. 30 : pas de réquisition contre une insuffisance de
crédits ».

Les choix d'interprétation et les simplifications sont listés dans
[docs/interpretations.md](docs/interpretations.md).

## Architecture

Un **monolithe modulaire** (Spring Modulith) dont les règles métier sont écrites en Java pur
(architecture hexagonale) :

| Module | Rôle |
|---|---|
| `commun` | Montants, calendrier des jours ouvrables, seuils et délais réglementaires, sécurité |
| `budget` | Lignes budgétaires : crédits ouverts, engagés, payés ; verrou contre les dépassements |
| `engagement` | Proposition, contrôle budgétaire et de régularité, visa, suspension, refus |
| `depense` | Dossier de paiement, chronomètre légal, intérêts moratoires, réquisition |
| `audit` | Journal en ajout seul, chaîné par empreintes, vérifiable |
| `pilotage` | Tableau de bord : taux d'engagement et de paiement, délai moyen, intérêts |

Des tests vérifient l'architecture elle-même : aucun module n'utilise les classes internes d'un
autre, et le domaine ne dépend ni de Spring ni de la base. Les diagrammes C4 des modules sont
générés dans `target/spring-modulith-docs`.

Les décisions et leurs raisons sont dans [docs/adr](docs/adr).

**Stack** : Java 21, Spring Boot 4.0, Spring Modulith 2.0, PostgreSQL 18 (accès SQL explicite par
`JdbcClient`), Flyway, Keycloak 26 (OIDC), springdoc (Swagger UI), JUnit 5, ArchUnit,
Testcontainers ou PostgreSQL embarqué, Docker Compose, GitHub Actions avec CodeQL et Trivy.

## Démarrer

### Lancer les tests (sans Docker)

```bash
./mvnw verify
```

Ce sont 75 tests unitaires, d'architecture et web, et 4 tests d'intégration sur un vrai
PostgreSQL. Si Docker tourne, les tests d'intégration utilisent un conteneur (Testcontainers) ;
sinon, sous Windows, un **PostgreSQL embarqué** d'environ 50 Mo de mémoire.

### Lancer la démonstration complète (Docker)

```bash
cp .env.example .env      # puis choisir les mots de passe
./mvnw -DskipTests package
docker compose up -d --build
```

- Swagger UI : <http://localhost:8080/swagger-ui.html>. Le bouton « Authorize » ouvre la connexion
  Keycloak.
- Comptes de démonstration : `admin.sarf`, `ordonnateur.amal`, `comptable.karim`,
  `fournisseur.atlas`, `auditeur.salma`, avec le mot de passe `SARF_DEMO_PASSWORD`.
- Le profil `demo` charge des données fictives et une **horloge déplaçable** :
  `POST /api/demo/horloge {"date": "2026-05-26"}`, en administrateur, fait avancer le temps pour
  montrer un délai qui expire.

### Le scénario en une minute

1. `ordonnateur.amal` : `POST /api/engagements`
2. `comptable.karim` : `POST /api/engagements/{id}/decision` `{"type": "VISA"}`
3. `fournisseur.atlas` : `POST /api/dossiers`, à faire deux fois pour voir le refus du double dépôt
4. `ordonnateur.amal` : `POST /api/dossiers/{id}/service-fait`, puis `/liquidation`, puis
   `/ordonnancement`
5. `admin.sarf` : avancer l'horloge au-delà de l'échéance
6. `comptable.karim` : `POST /api/dossiers/{id}/controle` `{"decision": "VISER"}`, avec les intérêts
   calculés
7. `auditeur.salma` : `GET /api/pilotage?exercice=2026` et `GET /api/audit/integrite`

Le test `ChaineDepenseIT` exécute exactement ce scénario.
