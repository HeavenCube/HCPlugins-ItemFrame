package fr.noltox.hcplugins.customitemframeglowing.message;

import fr.noltox.hcplugins.customitemframeglowing.support.MiniMessages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Renders configurable messages with safe MiniMessage placeholders.
 */
public record PluginMessages(
        String noPermission,
        String usage,
        String playerNotFound,
        String invalidAmount,
        String amountTooLarge,
        String staffConfirmation,
        String playerReceived,
        String reloadSuccess,
        String reloadFailure,
        String customizeHint,
        long customizeHintCooldownMillis
) {

    private static final String DEFAULT_AMOUNT_TOO_LARGE =
            "<red>La quantité maximale autorisée est de <white><amount></white> cadres.</red>";

    /**
     * Preserves the original constructor for integrations instantiating the message bundle directly.
     */
    public PluginMessages(
            String noPermission,
            String usage,
            String playerNotFound,
            String invalidAmount,
            String staffConfirmation,
            String playerReceived,
            String reloadSuccess,
            String reloadFailure,
            String customizeHint,
            long customizeHintCooldownMillis
    ) {
        this(
                noPermission,
                usage,
                playerNotFound,
                invalidAmount,
                DEFAULT_AMOUNT_TOO_LARGE,
                staffConfirmation,
                playerReceived,
                reloadSuccess,
                reloadFailure,
                customizeHint,
                customizeHintCooldownMillis
        );
    }

    public static PluginMessages from(FileConfiguration config) {
        return new PluginMessages(
                required(config, "messages.no-permission"),
                required(config, "messages.usage"),
                required(config, "messages.player-not-found"),
                required(config, "messages.invalid-amount"),
                optional(config, "messages.amount-too-large", DEFAULT_AMOUNT_TOO_LARGE),
                required(config, "messages.staff-confirmation"),
                required(config, "messages.player-received"),
                optional(config, "messages.reload-success", "<green>La configuration de HCItemFrame a été rechargée.</green>"),
                optional(config, "messages.reload-failure", "<red>Impossible de recharger la configuration. Consultez la console pour le détail.</red>"),
                optional(config, "messages.customize-hint", "<gray>Astuce : <yellow>Shift + clic gauche</yellow> sur ce cadre pour le personnaliser.</gray>"),
                customizeHintCooldownMillis(config)
        );
    }

    private static String required(FileConfiguration config, String path) {
        Object value = config.get(path);
        if (!(value instanceof String stringValue)) {
            throw invalidValue(path, "doit être un texte");
        }
        return stringValue;
    }

    private static String optional(FileConfiguration config, String path, String fallback) {
        Object value = config.get(path);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String stringValue)) {
            throw invalidValue(path, "doit être un texte");
        }
        return stringValue;
    }

    private static long customizeHintCooldownMillis(FileConfiguration config) {
        String path = "messages.customize-hint-cooldown-seconds";
        Object value = config.get(path);
        if (!(value instanceof Number number)) {
            throw invalidValue(path, "doit être un nombre entier");
        }
        long seconds = number.longValue();
        if (!Double.isFinite(number.doubleValue()) || number.doubleValue() != seconds
                || seconds < 0L || seconds > 3_600L) {
            throw invalidValue(path, "doit être comprise entre 0 et 3600");
        }
        return seconds * 1_000L;
    }

    private static IllegalStateException invalidValue(String path, String expectation) {
        return new IllegalStateException("La clé de configuration '" + path + "' " + expectation + ".");
    }

    public void send(CommandSender receiver, String template) {
        receiver.sendMessage(MiniMessages.parse(template));
    }

    public void send(CommandSender receiver, String template, String playerName, int amount) {
        send(receiver, template, playerName, Integer.toString(amount));
    }

    public void send(CommandSender receiver, String template, String playerName, String amount) {
        receiver.sendMessage(render(template, playerName, amount));
    }

    private Component render(String template, String playerName, String amount) {
        return MiniMessages.parse(
                template,
                Placeholder.unparsed("player", playerName),
                Placeholder.unparsed("amount", amount)
        );
    }
}
