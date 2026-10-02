# HCPlugins-ItemFrame

Plugin Paper de cadres invisibles avec contours RGB et profils de shaders.

**Licence :** code source consultable et contributions bienvenues, mais usage
réservé aux serveurs HeavenCube. Toute réutilisation ou distribution exige une
autorisation écrite préalable. Voir [LICENSE](LICENSE).

Cloner `HCPlugins-Core` à côté de ce dépôt, puis lancer `./gradlew build`.
HCCore et PacketEvents sont requis sur le serveur. Le resource pack des shaders
est installé séparément via Nexo.

La configuration est `plugins/HCPlugins/HCItemFrame.yml`. L'ancien fichier
`plugins/HCItemFrame/config.yml` n'est pas repris automatiquement.

À la pose, le cadre vide utilise le glow blanc natif sans `ItemDisplay`. Les
couleurs HEX et les profils explicitement sélectionnés utilisent un proxy pour
transmettre leur couleur exacte. Pour un cadre vide, ce proxy affiche le modèle
quasi transparent `heavencube:frame_outline_proxy` du pack : son contour mesure
12 × 12 pixels comme le cadre vanilla, sans seconde icône visible devant ou
derrière. Mettre à jour le pack Nexo avant le plugin pour fournir ce modèle.

Si `HCPack-CustomAssets` est aussi cloné à côté, le build vérifie les 16
couleurs de `config.yml` et la présence du modèle et de sa texture.

## Liens importants

- [HCPlugins-Core](https://github.com/HeavenCube/HCPlugins-Core) : HCCore, services communs et guide de création des plugins.
- [HCPlugins-actions](https://github.com/HeavenCube/HCPlugins-actions) : workflows GitHub Actions partagés.
- [HCPack-CustomAssets](https://github.com/HeavenCube/HCPack-CustomAssets) : resource pack Nexo 26.3 : glow, police et effets de texte.
- [HCPlugins-AdvancementsRedirect](https://github.com/HeavenCube/HCPlugins-AdvancementsRedirect)
- [HCPlugins-Glowing](https://github.com/HeavenCube/HCPlugins-Glowing)
- [HCPlugins-HuskHomesGUI](https://github.com/HeavenCube/HCPlugins-HuskHomesGUI)
- [HCPlugins-ItemFrame](https://github.com/HeavenCube/HCPlugins-ItemFrame)
- [HCPlugins-JoinMessage](https://github.com/HeavenCube/HCPlugins-JoinMessage)
- [HCPlugins-PlaceholdersExtra](https://github.com/HeavenCube/HCPlugins-PlaceholdersExtra)

## Maintenance et documentation technique

HCCore est obligatoire. Pour toute modification technique, commencer par [AGENTS.md](AGENTS.md),
puis [le guide du plugin](docs/TECHNICAL.md) et le Core voisin.
Le [guide commun](https://github.com/HeavenCube/HCPlugins-Core/blob/main/docs/ECOSYSTEM.md) décrit les conventions de toute la suite.
`CLAUDE.md` et `GEMINI.md` renvoient aux mêmes instructions, sans copie des règles.
Le catalogue commun `plugins/HCPlugins/translations.yml` se recharge par `/hcplugins core reload`.
