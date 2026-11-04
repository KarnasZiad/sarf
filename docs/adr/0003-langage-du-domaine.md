# 0003 : Le domaine parle le langage des textes

## Contexte

Les notions de la comptabilité publique marocaine (engagement, liquidation, ordonnancement,
ordonnateur, comptable assignataire, réquisition, somme à valoir) n'ont pas d'équivalent anglais
exact. Une traduction ferait perdre le lien avec les articles.

## Décision

Les classes et méthodes métier sont nommées en français, avec les termes des décrets :
`Engagement.decider`, `DossierPaiement.requerir`, `MotifSuspension.requisitionnable`. Chaque règle
cite son article dans la Javadoc, dans l'exception levée et dans le nom de son test. Les messages
de commit restent en anglais.

## Conséquences

- Un agent du ministère peut lire le domaine en le comparant aux textes.
- Toute erreur renvoyée par l'API contient le code de la règle et l'article qui la fonde.
