package fr.noltox.hcplugins.customitemframeglowing.service;

import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.config.FrameOutlineCatalog;
import fr.noltox.hcplugins.customitemframeglowing.render.HexFrameDisplayRenderer;
import fr.noltox.hcplugins.customitemframeglowing.state.CustomFrameState;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameOutline;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameVariant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Applies stored frame state and replaces the entity safely when its variant changes.
 */
public final class CustomFrameService {

    private final Plugin plugin;
    private final CustomFrameItemFactory itemFactory;
    private final HexFrameDisplayRenderer renderer;
    private final FrameOutlineCatalog outlineCatalog;
    private final Set<UUID> replacingFrames = new HashSet<>();

    public CustomFrameService(
            Plugin plugin,
            CustomFrameItemFactory itemFactory,
            HexFrameDisplayRenderer renderer,
            FrameOutlineCatalog outlineCatalog
    ) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.renderer = renderer;
        this.outlineCatalog = outlineCatalog;
    }

    private static byte[] serializePersistentData(ItemFrame frame) {
        try {
            return frame.getPersistentDataContainer().serializeToBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de conserver les données persistantes du cadre.", exception);
        }
    }

    private static void restorePersistentData(ItemFrame frame, byte[] persistentData) {
        try {
            frame.getPersistentDataContainer().readFromBytes(persistentData, true);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de restaurer les données persistantes du cadre.", exception);
        }
    }

    /**
     * Repairs stored state, corrects the entity type and then applies the visual rules.
     */
    public ItemFrame synchronize(ItemFrame frame) {
        CustomFrameState state = itemFactory.stateOf(frame);
        if (frame.getType() != state.variant().entityType()) {
            return replaceVariant(frame, state, state);
        }
        applyVisualState(frame, state);
        return frame;
    }

    public void updateOutline(ItemFrame frame, String outlineId) {
        CustomFrameState current = itemFactory.stateOf(frame);
        if (outlineId != null && outlineCatalog.outline(outlineId) == null) {
            throw new IllegalArgumentException("Contour inconnu : " + outlineId);
        }
        CustomFrameState updated = new CustomFrameState(current.variant(), outlineId);
        itemFactory.writeState(frame, updated);
        applyVisualState(frame, updated);
    }

    public ItemFrame updateVariant(ItemFrame frame, FrameVariant variant) {
        CustomFrameState current = itemFactory.stateOf(frame);
        CustomFrameState updated = new CustomFrameState(variant, current.outlineId());
        if (current.variant() == variant && frame.getType() == variant.entityType()) {
            itemFactory.writeState(frame, updated);
            applyVisualState(frame, updated);
            return frame;
        }
        return replaceVariant(frame, updated, current);
    }

    public void updateVisualStateAfterItemChange(ItemFrame frame) {
        applyVisualState(frame, itemFactory.stateOf(frame));
    }

    public void updateVisualStateAfterItemChange(ItemFrame frame, ItemStack itemAfterChange) {
        CustomFrameState state = itemFactory.stateOf(frame);
        renderer.apply(frame, state, itemAfterChange, outlineColor(state));
    }

    public boolean isReplacing(UUID frameId) {
        return replacingFrames.contains(frameId);
    }

    public void forget(ItemFrame frame) {
        renderer.forget(frame);
    }

    public boolean discardOrphanedProxy(Entity entity) {
        return renderer.discardOrphanedProxy(entity);
    }

    public UUID detachRemovedProxy(ItemDisplay display) {
        return renderer.detachRemovedProxy(display);
    }

    private ItemFrame replaceVariant(
            ItemFrame frame,
            CustomFrameState targetState,
            CustomFrameState previousState
    ) {
        Location location = frame.getLocation().clone();
        BlockFace facing = frame.getFacing();
        Rotation rotation = frame.getRotation();
        ItemStack displayedItem = frame.getItem().clone();
        float itemDropChance = frame.getItemDropChance();
        boolean fixed = frame.isFixed();
        UUID previousId = frame.getUniqueId();
        byte[] persistentData = serializePersistentData(frame);

        replacingFrames.add(previousId);
        ItemFrame replacement = null;
        boolean cleanupScheduled = false;
        try {
            Entity spawned = frame.getWorld().spawnEntity(location, targetState.variant().entityType());
            if (!(spawned instanceof ItemFrame spawnedFrame)) {
                spawned.remove();
                throw new IllegalStateException("La variante de cadre demandée n'a pas créé un ItemFrame.");
            }
            replacement = spawnedFrame;
            replacement.setFacingDirection(facing, true);
            replacement.setItem(displayedItem, false);
            replacement.setRotation(rotation);
            replacement.setItemDropChance(itemDropChance);
            restorePersistentData(replacement, persistentData);
            itemFactory.writeState(replacement, targetState);
            replacement.setFixed(fixed);
            applyVisualState(replacement, targetState);

            renderer.forget(frame);
            frame.remove();

            ItemFrame committedReplacement = replacement;
            Bukkit.getScheduler().runTask(plugin, () -> {
                replacingFrames.remove(previousId);
                if (committedReplacement.isValid()) {
                    applyVisualState(committedReplacement, targetState);
                }
            });
            cleanupScheduled = true;
            return replacement;
        } catch (RuntimeException exception) {
            if (frame.isValid()) {
                try {
                    removeReplacement(replacement);
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                try {
                    applyVisualState(frame, previousState);
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        } finally {
            if (!cleanupScheduled) {
                replacingFrames.remove(previousId);
            }
        }
    }

    private void removeReplacement(ItemFrame replacement) {
        if (replacement == null || !replacement.isValid()) {
            return;
        }
        UUID replacementId = replacement.getUniqueId();
        replacingFrames.add(replacementId);
        try {
            renderer.forget(replacement);
        } finally {
            try {
                replacement.setItem(null, false);
            } finally {
                try {
                    replacement.remove();
                } finally {
                    replacingFrames.remove(replacementId);
                }
            }
        }
    }

    private void applyVisualState(ItemFrame frame, CustomFrameState state) {
        renderer.apply(frame, state, outlineColor(state));
    }

    private org.bukkit.Color outlineColor(CustomFrameState state) {
        if (state.outlineId() == null) {
            return null;
        }
        FrameOutline outline = outlineCatalog.outline(state.outlineId());
        if (outline == null) {
            throw new IllegalStateException("Contour persisté inconnu : " + state.outlineId());
        }
        return outline.bukkitColor();
    }
}
