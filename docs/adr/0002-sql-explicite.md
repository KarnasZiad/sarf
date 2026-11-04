# 0002 : SQL explicite avec JdbcClient plutôt que JPA

## Contexte

Les garanties importantes reposent sur la base de données : verrouillage d'une ligne budgétaire
pendant un visa, verrouillage optimiste par numéro de version, compteur d'ordonnances sans trou,
tableaux PostgreSQL pour les observations, journal en ajout seul.

## Décision

Les dépôts écrivent leur SQL avec `JdbcClient` (Spring 6.1 et plus). Le domaine reste en Java pur,
sans annotations de persistance.

## Conséquences

- Chaque verrou (`SELECT … FOR UPDATE`), chaque contrôle de version et chaque `INSERT … ON CONFLICT
  … RETURNING` est visible dans le code et peut s'expliquer ligne par ligne.
- La correspondance entre lignes de tables et objets du domaine est écrite à la main : c'est plus
  de code qu'avec JPA, mais sans comportement caché (chargement paresseux, ordre de flush).
- Les contraintes SQL (`facture_unique`, plafonds de liquidation, journal immuable) protègent les
  données même en cas de défaut du code.
