# HCPlugins-ItemFrame

Plugin Paper de cadres invisibles avec contours RGB et profils de shaders.

Cloner `HCPlugins-Core` à côté de ce dépôt, puis lancer `./gradlew build`.
HCCore et PacketEvents sont requis sur le serveur. Le resource pack des shaders
est installé séparément via Nexo.

À la pose, le cadre vide utilise le glow blanc natif sans `ItemDisplay`. Les
couleurs HEX et les profils explicitement sélectionnés utilisent un proxy pour
transmettre leur couleur exacte. Pour un cadre vide, ce proxy affiche le modèle
quasi transparent `heavencube:frame_outline_proxy` du pack : son contour mesure
12 × 12 pixels comme le cadre vanilla, sans seconde icône visible devant ou
derrière. Mettre à jour le pack Nexo avant le plugin pour fournir ce modèle.

Si `HCPack-CustomGlowing` est aussi cloné à côté, le build vérifie les 16
couleurs de `config.yml` et la présence du modèle et de sa texture.
