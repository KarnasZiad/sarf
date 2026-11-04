# 0004 : Un vrai PostgreSQL pour les tests d'intégration, avec ou sans Docker

## Contexte

Les tests d'intégration doivent tourner sur PostgreSQL, et non sur une base en mémoire, car les
verrous, les tableaux, les déclencheurs et `ON CONFLICT` en font partie. Le poste de développement
dispose de peu de mémoire, et Docker Desktop n'y est pas toujours démarré.

## Décision

`BaseDeDonneesDeTest` démarre un conteneur Testcontainers quand Docker est disponible (CI), et
sinon un PostgreSQL embarqué (zonky, binaires Windows de la même version que l'image Docker).

## Conséquences

- `./mvnw verify` passe sur le poste de développement sans Docker, avec environ 50 Mo de mémoire.
- La CI utilise l'image `postgres:18.6-alpine`, identique à `compose.yaml`.
- Sous Linux sans Docker, il faudrait ajouter les binaires Linux (profil Maven à créer).
