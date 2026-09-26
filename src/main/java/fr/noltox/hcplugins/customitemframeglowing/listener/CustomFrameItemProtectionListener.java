package fr.noltox.hcplugins.customitemframeglowing.listener;

import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Objects;

/**
 * Canonicalizes marked frames and blocks only transformations that would alter their item data.
 */
public final class CustomFrameItemProtectionListener implements Listener {

    private final CustomFrameItemFactory itemFactory;

    public CustomFrameItemProtectionListener(CustomFrameItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    private static boolean isTransformationInventory(InventoryType type) {
        return switch (type) {
            case ANVIL, GRINDSTONE, SMITHING, ENCHANTING, WORKBENCH, CRAFTING -> true;
            default -> false;
        };
    }

    public void synchronizeLoadedItems() {
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Item.class).forEach(this::canonicalizeDroppedItem));
        Bukkit.getOnlinePlayers().forEach(player -> canonicalizeInventory(player.getInventory()));
    }

    @EventHandler
    public void onEntitiesLoaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Item item) {
                canonicalizeDroppedItem(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        canonicalizeInventory(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (itemFactory.isCustom(dropped)) {
            event.getItemDrop().setItemStack(itemFactory.canonicalize(dropped));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        canonicalizeClickItems(event);
        if (!isTransformationInventory(event.getView().getTopInventory().getType())) {
            return;
        }

        boolean touchesTopInventory = event.getClickedInventory() == event.getView().getTopInventory()
                || event.isShiftClick();
        if (touchesTopInventory && (itemFactory.isCustom(event.getCurrentItem())
                || itemFactory.isCustom(event.getCursor())
                || hotbarItemIsCustom(event))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (itemFactory.isCustom(event.getOldCursor())) {
            ItemStack remainingCursor = event.getCursor();
            if (remainingCursor != null) {
                event.setCursor(itemFactory.canonicalize(remainingCursor));
            }
            int topSize = event.getView().getTopInventory().getSize();
            if (isTransformationInventory(event.getView().getTopInventory().getType())
                    && event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        preventResultWhenCustom(event.getInventory(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        preventResultWhenCustom(event.getInventory(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        preventResultWhenCustom(event.getInventory(), event::setResult);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (matrixHasCustom(event.getInventory())) {
            canonicalizeMatrix(event.getInventory());
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (matrixHasCustom(event.getInventory())) {
            canonicalizeMatrix(event.getInventory());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (itemFactory.isCustom(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (itemFactory.isCustom(event.getItem())) {
            event.setItem(itemFactory.canonicalize(event.getItem()));
            event.setCancelled(true);
        }
    }

    private void canonicalizeClickItems(InventoryClickEvent event) {
        if (itemFactory.isCustom(event.getCurrentItem())) {
            event.setCurrentItem(itemFactory.canonicalize(event.getCurrentItem()));
        }
        if (itemFactory.isCustom(event.getCursor())) {
            event.getView().setCursor(itemFactory.canonicalize(event.getCursor()));
        }
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(hotbarButton);
            if (itemFactory.isCustom(hotbarItem)) {
                event.getWhoClicked().getInventory().setItem(hotbarButton, itemFactory.canonicalize(hotbarItem));
            }
        }
    }

    private boolean hotbarItemIsCustom(InventoryClickEvent event) {
        int hotbarButton = event.getHotbarButton();
        return hotbarButton >= 0 && itemFactory.isCustom(event.getWhoClicked().getInventory().getItem(hotbarButton));
    }

    private void preventResultWhenCustom(Inventory inventory, java.util.function.Consumer<ItemStack> clearResult) {
        if (Arrays.stream(inventory.getContents()).anyMatch(itemFactory::isCustom)) {
            canonicalizeInventory(inventory);
            clearResult.accept(null);
        }
    }

    private boolean matrixHasCustom(CraftingInventory inventory) {
        return Arrays.stream(inventory.getMatrix()).anyMatch(itemFactory::isCustom);
    }

    private void canonicalizeMatrix(CraftingInventory inventory) {
        ItemStack[] matrix = Objects.requireNonNull(inventory.getMatrix(), "matrix");
        for (int index = 0; index < matrix.length; index++) {
            ItemStack item = matrix[index];
            if (item != null && itemFactory.isCustom(item)) {
                matrix[index] = itemFactory.canonicalize(item);
            }
        }
        inventory.setMatrix(matrix);
    }

    private void canonicalizeInventory(Inventory inventory) {
        ItemStack[] contents = Objects.requireNonNull(inventory.getContents(), "contents");
        boolean changed = false;
        for (int index = 0; index < contents.length; index++) {
            ItemStack item = contents[index];
            if (item != null && itemFactory.isCustom(item)) {
                contents[index] = itemFactory.canonicalize(item);
                changed = true;
            }
        }
        if (changed) {
            inventory.setContents(contents);
        }
    }

    private void canonicalizeDroppedItem(Item item) {
        if (itemFactory.isCustom(item.getItemStack())) {
            item.setItemStack(itemFactory.canonicalize(item.getItemStack()));
        }
    }
}
