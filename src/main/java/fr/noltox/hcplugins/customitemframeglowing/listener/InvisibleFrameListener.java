package fr.noltox.hcplugins.customitemframeglowing.listener;

import fr.noltox.hcplugins.customitemframeglowing.dialog.FrameCustomizationDialog;
import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.message.PluginMessages;
import fr.noltox.hcplugins.customitemframeglowing.service.CustomFrameService;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Maintains custom-frame state while retaining normal vanilla item-frame interaction.
 */
public final class InvisibleFrameListener implements Listener {

    private final Plugin plugin;
    private final CustomFrameItemFactory itemFactory;
    private final CustomFrameService frameService;
    private final FrameCustomizationDialog customizationDialog;
    private final PluginMessages messages;
    private final Set<DialogTarget> openingDialogs = new HashSet<>();
    private final Map<UUID, Long> lastCustomizeHints = new HashMap<>();
    private boolean active = true;

    public InvisibleFrameListener(
            Plugin plugin,
            CustomFrameItemFactory itemFactory,
            CustomFrameService frameService,
            FrameCustomizationDialog customizationDialog,
            PluginMessages messages
    ) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.frameService = frameService;
        this.customizationDialog = customizationDialog;
        this.messages = messages;
    }

    private static boolean shouldDropItems(HangingBreakEvent event, ItemFrame frame) {
        if (!Boolean.TRUE.equals(frame.getWorld().getGameRuleValue(GameRules.ENTITY_DROPS))) {
            return false;
        }
        return !(event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player
                && player.getGameMode() == GameMode.CREATIVE);
    }

    private static void dropAtRest(World world, Location location, ItemStack itemStack) {
        Item droppedItem = world.dropItem(location, itemStack);
        droppedItem.setVelocity(new Vector());
    }

    public void synchronizeLoadedFrames() {
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(ItemFrame.class).stream()
                .filter(itemFactory::isCustom)
                .forEach(frameService::synchronize));
    }

    public void shutdown() {
        active = false;
        customizationDialog.shutdown();
        openingDialogs.clear();
        lastCustomizeHints.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFramePlaced(HangingPlaceEvent event) {
        ItemStack placedItem = event.getItemStack();
        if (event.getEntity() instanceof ItemFrame frame
                && placedItem != null
                && itemFactory.isCustom(placedItem)) {
            itemFactory.mark(frame, itemFactory.stateOf(placedItem));
            frameService.synchronize(frame);
            sendCustomizeHint(event.getPlayer());
        }
    }

    /**
     * A sneaking left-click on a filled frame is emitted as REMOVE before the item is taken out.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFilledFrameCustomize(PlayerItemFrameChangeEvent event) {
        ItemFrame frame = event.getItemFrame();
        Player player = event.getPlayer();
        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE
                && itemFactory.isCustom(frame)
                && player.isSneaking()
                && player.hasPermission(FrameCustomizationDialog.PERMISSION)) {
            event.setCancelled(true);
            requestDialog(player, frame);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameItemChanged(PlayerItemFrameChangeEvent event) {
        ItemFrame frame = event.getItemFrame();
        if (!itemFactory.isCustom(frame)) {
            return;
        }

        if (event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.ROTATE) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active && frame.isValid() && itemFactory.isCustom(frame)) {
                    frameService.updateVisualStateAfterItemChange(frame);
                }
            });
            return;
        }

        ItemStack itemAfterChange = event.getAction() == PlayerItemFrameChangeEvent.ItemFrameChangeAction.REMOVE
                ? new ItemStack(Material.AIR)
                : event.getItemStack().clone();
        frameService.updateVisualStateAfterItemChange(frame, itemAfterChange);
    }

    /**
     * Handles every physical removal, including the empty-frame customization gesture.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFrameBroken(HangingBreakEvent event) {
        if (!(event.getEntity() instanceof ItemFrame frame) || !itemFactory.isCustom(frame)) {
            return;
        }
        if (frameService.isReplacing(frame.getUniqueId())) {
            return;
        }

        if (event instanceof HangingBreakByEntityEvent byEntity
                && byEntity.getRemover() instanceof Player player
                && frame.getItem().isEmpty()
                && player.isSneaking()
                && player.hasPermission(FrameCustomizationDialog.PERMISSION)) {
            event.setCancelled(true);
            requestDialog(player, frame);
            return;
        }

        event.setCancelled(true);
        ItemStack displayedItem = frame.getItem().clone();
        float itemDropChance = frame.getItemDropChance();
        boolean shouldDropItems = shouldDropItems(event, frame);
        World dropWorld = frame.getWorld();
        Location dropLocation = frame.getLocation().clone();
        frameService.forget(frame);
        frame.setItem(null, false);
        frame.remove();
        dropWorld.playSound(dropLocation, Sound.ENTITY_ITEM_FRAME_BREAK, 1.0F, 1.0F);

        if (!shouldDropItems) {
            return;
        }
        dropAtRest(dropWorld, dropLocation, itemFactory.create(1));
        if (!displayedItem.isEmpty() && ThreadLocalRandom.current().nextFloat() < itemDropChance) {
            dropAtRest(dropWorld, dropLocation, displayedItem);
        }
    }

    @EventHandler
    public void onEntitiesLoaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (frameService.discardOrphanedProxy(entity)) {
                continue;
            }
            if (entity instanceof ItemFrame frame && itemFactory.isCustom(frame)) {
                frameService.synchronize(frame);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoved(EntityRemoveEvent event) {
        Entity removedEntity = event.getEntity();
        if (removedEntity instanceof ItemFrame frame && itemFactory.isCustom(frame)) {
            frameService.forget(frame);
            return;
        }

        if (!(removedEntity instanceof ItemDisplay removedDisplay)) {
            return;
        }
        UUID ownerId = frameService.detachRemovedProxy(removedDisplay);
        if (ownerId != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!active) {
                    return;
                }
                Entity owner = Bukkit.getEntity(ownerId);
                if (owner instanceof ItemFrame frame && frame.isValid() && itemFactory.isCustom(frame)) {
                    frameService.synchronize(frame);
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastCustomizeHints.remove(event.getPlayer().getUniqueId());
    }

    private void requestDialog(Player player, ItemFrame frame) {
        DialogTarget target = new DialogTarget(player.getUniqueId(), frame.getUniqueId());
        if (!openingDialogs.add(target)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            openingDialogs.remove(target);
            if (customizationDialog.tryOpen(player, frame)) {
                player.playSound(player.getLocation(), Sound.BLOCK_COPPER_CHEST_OPEN, 1.0F, 2.0F);
            }
        });
    }

    private void sendCustomizeHint(Player player) {
        if (player == null || !player.hasPermission(FrameCustomizationDialog.PERMISSION)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastCustomizeHints.get(player.getUniqueId());
        if (previous != null && now - previous < messages.customizeHintCooldownMillis()) {
            return;
        }
        lastCustomizeHints.put(player.getUniqueId(), now);
        messages.send(player, messages.customizeHint());
    }

    private record DialogTarget(UUID playerId, UUID frameId) {
    }
}
