package fr.noltox.hcplugins.customitemframeglowing.command;

import fr.noltox.hcplugins.core.api.command.CoreCommand;
import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.message.PluginMessages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Définit la branche canonique {@code /hcplugins itemframe}.
 */
public final class GiveInvisibleFrameCommand implements CoreCommand {

    private static final int MAX_AMOUNT = 2_304;

    private final Supplier<CustomFrameItemFactory> itemFactorySupplier;
    private final Supplier<PluginMessages> messagesSupplier;
    private final BooleanSupplier configurationReloader;

    public GiveInvisibleFrameCommand(
            Supplier<CustomFrameItemFactory> itemFactorySupplier,
            Supplier<PluginMessages> messagesSupplier,
            BooleanSupplier configurationReloader
    ) {
        this.itemFactorySupplier = itemFactorySupplier;
        this.messagesSupplier = messagesSupplier;
        this.configurationReloader = configurationReloader;
    }

    private static boolean isAuthorized(CommandSender sender, PluginMessages messages) {
        if (sender.isOp()) {
            return true;
        }
        messages.send(sender, messages.noPermission(), "", 0);
        return false;
    }

    private static int parseAmount(
            CommandSender sender,
            Player target,
            String rawAmount,
            PluginMessages messages
    ) {
        if (rawAmount == null) {
            return 1;
        }
        int amount;
        try {
            amount = Integer.parseInt(rawAmount);
        } catch (NumberFormatException exception) {
            messages.send(sender, messages.invalidAmount(), target.getName(), rawAmount);
            return 0;
        }
        if (amount <= 0) {
            messages.send(sender, messages.invalidAmount(), target.getName(), rawAmount);
            return 0;
        }
        if (amount > MAX_AMOUNT) {
            messages.send(sender, messages.amountTooLarge(), target.getName(), MAX_AMOUNT);
            return 0;
        }
        return amount;
    }

    private static void dropRemaining(
            Player target,
            int remaining,
            int maxStackSize,
            CustomFrameItemFactory itemFactory
    ) {
        int amountLeft = remaining;
        while (amountLeft > 0) {
            int stackSize = Math.min(maxStackSize, amountLeft);
            target.getWorld().dropItem(target.getLocation(), itemFactory.create(stackSize));
            amountLeft -= stackSize;
        }
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            reload(sender);
            return;
        }
        if (args.length >= 2 && "give".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                give(sender, args[1], null, false);
                return;
            }
            if (args.length == 3) {
                if ("-silent".equalsIgnoreCase(args[2])) {
                    give(sender, args[1], null, true);
                } else {
                    give(sender, args[1], args[2], false);
                }
                return;
            }
            if (args.length == 4 && "-silent".equalsIgnoreCase(args[3])) {
                give(sender, args[1], args[2], true);
                return;
            }
        }
        sendUsage(sender);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (!source.getSender().isOp()) {
            return List.of();
        }
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("give", "reload").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (!"give".equalsIgnoreCase(args[0])) {
            return List.of();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        if (args.length == 3) {
            return List.of("1", "-silent").stream().filter(value -> value.startsWith(args[2])).toList();
        }
        if (args.length == 4 && !"-silent".equalsIgnoreCase(args[2])) {
            return "-silent".startsWith(args[3]) ? List.of("-silent") : List.of();
        }
        return List.of();
    }

    private void reload(CommandSender sender) {
        PluginMessages messages = messagesSupplier.get();
        if (!sender.isOp()) {
            messages.send(sender, messages.noPermission(), "", 0);
            return;
        }
        if (configurationReloader.getAsBoolean()) {
            PluginMessages reloadedMessages = messagesSupplier.get();
            reloadedMessages.send(sender, reloadedMessages.reloadSuccess(), "", 0);
        } else {
            messages.send(sender, messages.reloadFailure(), "", 0);
        }
    }

    private void sendUsage(CommandSender sender) {
        PluginMessages messages = messagesSupplier.get();
        if (sender.isOp()) {
            messages.send(sender, messages.usage(), "", 0);
        } else {
            messages.send(sender, messages.noPermission(), "", 0);
        }
    }

    private void give(CommandSender sender, String playerName, String rawAmount, boolean silent) {
        PluginMessages messages = messagesSupplier.get();
        if (!isAuthorized(sender, messages)) {
            return;
        }

        Player target = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
        if (target == null) {
            messages.send(sender, messages.playerNotFound(), playerName, 0);
            return;
        }

        int amount = parseAmount(sender, target, rawAmount, messages);
        if (amount <= 0) {
            return;
        }

        giveItems(target, amount, itemFactorySupplier.get());
        messages.send(sender, messages.staffConfirmation(), target.getName(), amount);
        if (!silent) {
            messages.send(target, messages.playerReceived(), target.getName(), amount);
        }
    }

    private void giveItems(Player target, int amount, CustomFrameItemFactory itemFactory) {
        int maxStackSize = itemFactory.create(1).getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(maxStackSize, remaining);
            ItemStack stack = itemFactory.create(stackSize);
            var leftovers = target.getInventory().addItem(stack);
            remaining -= stackSize;
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(leftover -> target.getWorld().dropItem(target.getLocation(), leftover));
                dropRemaining(target, remaining, maxStackSize, itemFactory);
                return;
            }
        }
    }
}
