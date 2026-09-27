# HCPlugins-ItemFrame

Plugin Paper de cadres invisibles avec contours RGB et profils de shaders.

Cloner `HCPlugins-Core` à côté de ce dépôt, puis lancer `./gradlew build`.
HCCore et PacketEvents sont requis sur le serveur. Le resource pack des shaders
est installé séparément via Nexo.

À la pose, le cadre vide utilise le glow blanc natif sans `ItemDisplay`. Les
couleurs HEX et les profils explicitement sélectionnés utilisent encore un proxy
pour transmettre leur couleur exacte. Si `HCPack-CustomGlowing` est aussi cloné
à côté, le build vérifie que les 16 couleurs de `config.yml` correspondent aux
carriers et aux couleurs de sortie du shader du pack.
