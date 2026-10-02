# Guide technique — HCItemFrame

## Point d’entrée

Ce dépôt appartient à la suite privée d’usage HeavenCube, publiée comme source consultable.
Il dépend obligatoirement de HCCore. Lire d’abord [AGENTS.md](../AGENTS.md), puis le Core voisin.
Le [guide commun](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) décrit les règles Java/Paper, les contrats Core,
le packaging et la CI. Ce guide local décrit les particularités à préserver ; le code reste l’autorité.

## Dépendances et compilation

HCCore et PacketEvents obligatoires. HCPack-CustomAssets installé séparément via Nexo pour les profils et le modèle de contour.

Cloner Core à côté ; JAR standard. Cloner aussi HCPack-CustomAssets pour activer `verifyGlowPackContract` (facultatif au build, utile pour un changement de couleur/modèle).

```powershell
.\gradlew.bat build
```

Utiliser JDK 25. Sous Linux : `./gradlew build`. Le JAR est dans `build/libs/` ; installer aussi
les plugins serveur requis. Un clone Core modifié affecte le classpath local ; noter son commit.
Après extension d’API commune, construire Core séparément et installer sa version compatible en premier.

## Commandes et permissions

`/hcplugins itemframe give <joueur> [quantité] [-silent]` et `reload` : opérateurs. Personnalisation Shift + clic gauche : `hcplugins.itemframe.customize`, défaut false. La quantité est bornée à 2304 ; les surplus d’inventaire sont déposés selon la logique existante. Ne pas inventer un alias `-s` non accepté par le parseur.

La branche canonique est `/hcplugins itemframe` ; elle est enregistrée chez Core, pas comme
une deuxième racine. Les résultats de reload, refus opérateur et autres textes partagés utilisent
`HCPluginsCore.translations(plugin)`. `{duration}` inclut déjà l’unité `ms`.

## Fichiers et données

`plugins/HCPlugins/HCItemFrame.yml`. L’identité et les variantes des items/cadres sont stockées en PDC ; aucun fichier de base de données de cadres.

Valeurs par défaut dans `src/main/resources/`, jamais écrasées à chaque démarrage. Aucun import
automatique des anciens dossiers du monorepo. Messages communs dans `plugins/HCPlugins/translations.yml` ;
messages métier locaux. Modifier le fichier partagé se recharge avec `/hcplugins core reload`.

## Chemin d’exécution

L’item marqué produit un cadre géré par CustomFrameService. Vide et sans contour choisi : vrai cadre avec glow blanc natif, sans proxy. Un contour RGB/profil choisi utilise HexFrameDisplayRenderer et un ItemDisplay. Le modèle `heavencube:frame_outline_proxy` du pack évite la seconde icône visible pour le cadre vide. Item inséré, orientation et rotation suivent la logique du renderer et du service.

## Carte du code pour une modification

| Fichier | Responsabilité et points à préserver |
| --- | --- |
| [HCItemFrame.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/HCItemFrame.java) | Génération de composants, remplacement, restauration et nettoyage. |
| [CustomFrameService.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/service/CustomFrameService.java) | État des vrais cadres, variantes, interactions et drops. |
| [CustomFrameState.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/state/CustomFrameState.java) | Identité/état PDC : conserver les clés et IDs persistants. |
| [CustomFrameItemFactory.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/item/CustomFrameItemFactory.java) | Item canonique donné/droppé, metadata et stack size. |
| [InvisibleFrameListener.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/listener/InvisibleFrameListener.java) | Pose, interaction, casse et synchronisation des cadres chargés. |
| [CustomFrameItemProtectionListener.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/listener/CustomFrameItemProtectionListener.java) | Protection et synchronisation des items personnalisés. |
| [HexFrameDisplayRenderer.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/render/HexFrameDisplayRenderer.java) | Proxy, transformation, rotation, couleur et suppression des orphelins. |
| [FrameItemMetadataMasker.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/render/FrameItemMetadataMasker.java) | Masquage client via PacketEvents ; lifecycle du listener. |
| [FrameCustomizationDialog.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/dialog/FrameCustomizationDialog.java) | UUID monde/cadre, permission et activité revalidés avant action. |
| [FrameOutlineCatalog.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/config/FrameOutlineCatalog.java) | Validation HEX/profil et refus des collisions de carriers. |
| [GiveInvisibleFrameCommand.java](../src/main/java/fr/noltox/hcplugins/customitemframeglowing/command/GiveInvisibleFrameCommand.java) | Give/reload et contrôles opérateur. |

`src/main/resources/paper-plugin.yml` définit identité, dépendances et permissions serveur.
`settings.gradle.kts` définit les builds composites ; `build.gradle.kts` le packaging.
`.github/workflows/build.yml` appelle les actions partagées à `@main` ; `.github/dependabot.yml`
maintient les dépendances. Une mise à jour de dépendance doit conserver ces contrats.

## Invariants et zones à risque

- Plugin serveur `HCItemFrame`, HCCore obligatoire ; module `itemframe`.
- HCCore et PacketEvents obligatoires. HCPack-CustomAssets installé séparément via Nexo pour les profils et le modèle de contour.
- Ne pas supprimer le proxy pour les couleurs en affirmant que les équipes vanilla transmettent un HEX arbitraire ; le contrat actuel exige ce proxy.
- Préserver identité PDC, IDs historiques des colorants, drops canoniques, disparition du cadre rempli et rotations vanilla.
- Traiter murs, plafond et sol, avant/arrière, item inséré/enlevé et changement de variante comme cas distincts de rendu.
- Nettoyer proxies sur casse, unload/disable et reload ; revalider UUID/monde et permission dans les callbacks.
- Ne pas embarquer le pack dans le JAR ; mettre à jour le pack avant un plugin qui exige un nouveau modèle.

Avant une nouvelle logique transversale : chercher les usages dans Core et les autres plugins ;
ajouter au Core le contrat partagé réellement nécessaire avant le raccordement local. Ne pas recopier
un loader YAML, un registre de commandes ou un catalogue de traductions. Garder les événements et
états spécifiques ici. Thread serveur pour le jeu ; considérer callbacks et APIs tierces selon leur
thread réel, puis revalider le contexte avant mutation.

## Validation et limites

Lire les tests existants de catalogue/état/rendu avant modification. `verifyGlowPackContract` vérifie 16 HEX et les assets du proxy uniquement si le pack est trouvé. En jeu : toutes orientations, rotation, deux faces, vide/rempli, retrait/drops, chunk reload, restart et refus de permission.

Le build ne démontre pas l’alignement graphique final. La tâche pack peut être SKIPPED si son clone est absent ; préciser ce cas au lieu d’annoncer une validation pack complète.

Documentation seule : vérifier les liens locaux et le diff. Modification runtime : build, tests ciblés,
et scénario serveur correspondant. Rapporter seulement ce qui a été exécuté, avec résultat et limite.
Pour transfert entre IA, donner le commit Core testé et les fichiers/changements encore non committés.
