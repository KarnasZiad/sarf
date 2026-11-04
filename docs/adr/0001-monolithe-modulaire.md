# 0001 : Monolithe modulaire plutôt que microservices

## Contexte

Une dépense traverse le budget, l'engagement, le dossier de paiement et l'audit. Chaque étape
dépend de la précédente : on ne paie pas sans visa d'engagement, on ne liquide pas au-delà de
l'engagé, et toute opération validée doit être tracée.

## Décision

Un seul service Spring Boot, découpé en modules (Spring Modulith) qui ne communiquent que par
leurs interfaces publiques. Le journal d'audit écoute les événements métier **dans la même
transaction** que l'opération.

## Conséquences

- Une transaction PostgreSQL garantit la cohérence : visa et réservation des crédits, paiement et
  consommation des crédits, opération et entrée d'audit. Il n'y a ni saga ni cohérence à terme.
- Les frontières entre modules sont vérifiées par un test (`ArchitectureTest`). Si un module
  devait un jour devenir un service séparé, la découpe existe déjà.
- Le déploiement reste simple : une seule image, une seule base.
