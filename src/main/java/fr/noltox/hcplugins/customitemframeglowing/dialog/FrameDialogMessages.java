package fr.noltox.hcplugins.customitemframeglowing.dialog;

import fr.noltox.hcplugins.customitemframeglowing.support.MiniMessages;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;


/**
 * Configurable MiniMessage text used only by the native frame-customization dialog.
 */
public final class FrameDialogMessages {

    private final Component title;
    private final Component description;
    private final Component normalFrame;
    private final Component glowFrame;
    private final Component selected;
    private final Component close;

    private FrameDialogMessages(
            Component title,
            Component description,
            Component normalFrame,
            Component glowFrame,
            Component selected,
            Component close
    ) {
        this.title = title;
        this.description = description;
        this.normalFrame = normalFrame;
        this.glowFrame = glowFrame;
        this.selected = selected;
        this.close = close;
    }

    public static FrameDialogMessages from(FileConfiguration config) {
        return new FrameDialogMessages(
                required(config, "dialog.title"),
                required(config, "dialog.description"),
                required(config, "dialog.normal-frame"),
                required(config, "dialog.glow-frame"),
                required(config, "dialog.selected"),
                required(config, "dialog.close")
        );
    }

    private static Component required(FileConfiguration config, String path) {
        Object value = config.get(path);
        if (!(value instanceof String stringValue)) {
            throw new IllegalStateException("La clé de configuration '" + path + "' doit être un texte.");
        }
        return MiniMessages.parse(stringValue);
    }

    public Component title() {
        return title;
    }

    public Component description() {
        return description;
    }

    public Component normalFrame() {
        return normalFrame;
    }

    public Component glowFrame() {
        return glowFrame;
    }

    public Component selected() {
        return selected;
    }

    public Component close() {
        return close;
    }

}
