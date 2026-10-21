# Generateurs (Spigot / Paper 1.20.x)

Plugin de générateurs : 100 paliers, barrel (x1 à x20), beacons à durée limitée, argent via Vault + EssentialsX.

## Compiler le .jar

Prérequis : JDK 17+ et Maven, avec accès internet (dépendances Spigot API et VaultAPI).

```
mvn clean package
```

Le fichier est généré dans `target/Generateurs-1.0.0.jar`. Place-le dans `plugins/`.
Plugins requis sur le serveur : Vault + EssentialsX.

Pour une autre version de Minecraft, change `spigot-api` dans `pom.xml` et `api-version` dans `plugin.yml`.

## Réglage obligatoire EssentialsX

EssentialsX plafonne l'argent par défaut (option `max-money` dans son `config.yml`, 10T par défaut à vérifier
sur ta version). Monte cette valeur au-dessus de 50Q (ex. `100000000000000000`), sinon les gains sont bloqués.

## Fonctionnement

- `/gen` : menu de 100 générateurs (3 pages). Clic = achat, l'objet va dans l'inventaire.
- Générateur posé : clic droit = casser (objet rendu), clic gauche = production, Maj + clic gauche = améliorer.
- Production : 1 item toutes les 15 s (chunk chargé). Ramassé par son propriétaire, il est converti en argent.
- Amélioration = prix(palier+1) - prix(palier) : neutre, jamais de gain ni de perte.
- Barrel : un par joueur. Les drops y sont stockés (capacité = 10 000 x niveau). Clic droit = récupérer
  l'argent avec le multiplicateur du niveau (x1 à x20). Maj + clic droit = reprendre le barrel.
- Beacon : bonus additif (+50 % = 0.5), limité à 3 actifs, expire après sa durée (compte à rebours dès la pose).
- Limite de générateurs : 25 par défaut, ou permission `gen.max.<n>` (ex. `gen.max.60`).

## Commandes admin (permission gen.admin)

- `/gen give <joueur> <palier 1-100> [quantité]`
- `/gen givebarrel <joueur> <niveau>`
- `/gen givebeacon <joueur> <bonus> [heures]`
- `/gen eco` : argent créé au total et maximum théorique par joueur
- `/gen reload`

Les give permettent de brancher un plugin de crates/clés (commande console).

## Économie (voir config.yml)

- Valeur par item : 1$ (foin) à 5 000 000$ (bloc de commande), courbe exponentielle. Prix = valeur x 50.
- Maximum théorique d'un seul joueur (25 générateurs palier 100, barrel x20, 3 beacons +100 %) :
  environ 5,76e13 $/jour, soit ~173 jours pour 10Q et ~868 jours pour 50Q en jeu parfait.
- Leviers pour resserrer : `top-value`, `default-max-generators`, `max-beacons`, `beacon.max-bonus`,
  `barrel.max-level`, durée des beacons.
- Le plugin compte l'argent créé (`total-minted` dans `data.yml`) et affiche une alerte console à 50 %, 80 % et 100 % du budget.
  Aucune limite bloquante n'est codée.
