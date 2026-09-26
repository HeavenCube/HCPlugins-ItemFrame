package fr.noltox.hcplugins.customitemframeglowing.render;

import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.state.CustomFrameState;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Renders only selected outlines as RGB {@link ItemDisplay} entities.
 *
 * <p>The real item frame remains the interaction and drop owner. Its visual state is hidden only
 * on clients while the ItemDisplay proxy renders the RGB outline and matching item appearance.</p>
 */
public final class HexFrameDisplayRenderer {

    private static final byte PROXY_MARKER_VALUE = 1;
    private static final double EMPTY_PROXY_BACK_OFFSET = 0.0625D;
    private static final double CONTENT_FORWARD_OFFSET = 0.03125D;
    private static final float EMPTY_FRAME_SCALE = 0.75F;
    private static final float CONTENT_SCALE = 0.5F;

    private final NamespacedKey proxyMarkerKey;
    private final Map<UUID, UUID> proxyIds = new HashMap<>();
    private final FrameItemMetadataMasker itemMasker = new FrameItemMetadataMasker();

    public HexFrameDisplayRenderer(Plugin plugin) {
        proxyMarkerKey = new NamespacedKey(plugin, "hex_outline_proxy");
        itemMasker.start();
    }

    private static Location proxyLocation(ItemFrame frame, boolean empty) {
        Location location = frame.getLocation().clone();
        Vector direction = frame.getFacing().getDirection();
        location.add(direction.multiply(empty ? -EMPTY_PROXY_BACK_OFFSET : CONTENT_FORWARD_OFFSET));
        return location;
    }

    private static Transformation transformation(Rotation rotation, float scale) {
        // ItemDisplayRenderer adds a 180° Y turn compared with ItemFrameRenderer, which reverses
        // the apparent Z axis. Negating the angle preserves vanilla's clockwise rotation order.
        Quaternionf itemRotation = new Quaternionf().rotateZ((float) Math.toRadians(-rotationDegrees(rotation)));
        return new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                itemRotation,
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
    }

    private static int rotationDegrees(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_45 -> 45;
            case CLOCKWISE -> 90;
            case CLOCKWISE_135 -> 135;
            case FLIPPED -> 180;
            case FLIPPED_45 -> 225;
            case COUNTER_CLOCKWISE -> 270;
            case COUNTER_CLOCKWISE_45 -> 315;
        };
    }

    private static void restoreNative(ItemFrame frame, boolean empty) {
        frame.setGlowing(true);
        frame.setVisible(empty);
    }

    public void apply(ItemFrame frame, CustomFrameState state, Color outlineColor) {
        apply(frame, state, frame.getItem(), outlineColor);
    }

    /**
     * Applies the post-change item when Paper has not yet committed the item-frame event.
     */
    public void apply(ItemFrame frame, CustomFrameState state, ItemStack displayedItem, Color outlineColor) {
        ItemStack item = displayedItem == null ? new ItemStack(org.bukkit.Material.AIR) : displayedItem;
        if (outlineColor == null) {
            removeProxy(frame.getUniqueId());
            restoreNative(frame, item.isEmpty());
            itemMasker.unmask(frame);
            return;
        }

        boolean empty = item.isEmpty();
        ItemDisplay proxy = proxy(frame);
        configureProxy(proxy, frame, state, item, empty, outlineColor);
        frame.setGlowing(false);
        frame.setVisible(empty);
        if (empty) {
            itemMasker.unmask(frame);
        } else {
            itemMasker.mask(frame);
        }
    }

    /**
     * Removes a proxy before entity replacement, entity unload, or physical destruction.
     */
    public void forget(ItemFrame frame) {
        removeProxy(frame.getUniqueId());
        itemMasker.forget(frame);
    }

    /**
     * Drops a proxy persisted by an interrupted shutdown before its owning frame is synchronized.
     */
    public boolean discardOrphanedProxy(Entity entity) {
        if (!(entity instanceof ItemDisplay display) || !isProxy(display)) {
            return false;
        }
        proxyIds.values().removeIf(display.getUniqueId()::equals);
        display.remove();
        return true;
    }

    /**
     * Detaches an externally removed proxy and returns the frame that must be rendered again.
     */
    public UUID detachRemovedProxy(ItemDisplay display) {
        if (!isProxy(display)) {
            return null;
        }
        UUID removedId = display.getUniqueId();
        Iterator<Map.Entry<UUID, UUID>> iterator = proxyIds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (entry.getValue().equals(removedId)) {
                iterator.remove();
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Removes stale proxies left behind by an interrupted plugin shutdown.
     */
    public void removeOrphanedProxies() {
        List<ItemDisplay> proxies = new ArrayList<>();
        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(ItemDisplay.class).stream()
                .filter(this::isProxy)
                .forEach(proxies::add));
        // Detach first so synchronous EntityRemoveEvent callbacks cannot recreate proxies during shutdown.
        proxyIds.clear();
        proxies.forEach(Entity::remove);
    }

    /**
     * Restores real frames before disabling the plugin and removes every managed visual proxy.
     */
    public void shutdown(CustomFrameItemFactory itemFactory) {
        try {
            Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(ItemFrame.class).stream()
                    .filter(itemFactory::isCustom)
                    .forEach(frame -> {
                        restoreNative(frame, frame.getItem().isEmpty());
                        itemMasker.unmask(frame);
                    }));
        } finally {
            shutdown();
        }
    }

    /**
     * Releases the packet listener even if startup failed before the item factory was created.
     */
    public void shutdown() {
        try {
            removeOrphanedProxies();
        } finally {
            itemMasker.shutdown();
        }
    }

    private ItemDisplay proxy(ItemFrame frame) {
        UUID frameId = frame.getUniqueId();
        UUID proxyId = proxyIds.get(frameId);
        if (proxyId != null) {
            Entity existing = Bukkit.getEntity(proxyId);
            if (existing instanceof ItemDisplay display && display.isValid()) {
                return display;
            }
            proxyIds.remove(frameId);
        }

        Entity spawned = frame.getWorld().spawnEntity(frame.getLocation(), EntityType.ITEM_DISPLAY);
        if (!(spawned instanceof ItemDisplay display)) {
            throw new IllegalStateException("Impossible de créer l'entité ItemDisplay du contour HEX.");
        }
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        display.setGravity(false);
        display.setNoPhysics(true);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display.setDisplayWidth(1.0F);
        display.setDisplayHeight(1.0F);
        display.setViewRange(1.0F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
        display.getPersistentDataContainer().set(proxyMarkerKey, PersistentDataType.BYTE, PROXY_MARKER_VALUE);
        proxyIds.put(frameId, display.getUniqueId());
        return display;
    }

    private void configureProxy(
            ItemDisplay proxy,
            ItemFrame frame,
            CustomFrameState state,
            ItemStack displayedItem,
            boolean empty,
            Color color
    ) {
        ItemStack visualItem = empty
                ? new ItemStack(state.variant().material())
                : displayedItem.clone();
        proxy.setItemStack(visualItem);
        proxy.setTransformation(transformation(
                empty ? Rotation.NONE : frame.getRotation(),
                empty ? EMPTY_FRAME_SCALE : CONTENT_SCALE
        ));
        proxy.teleport(proxyLocation(frame, empty));
        proxy.setGlowColorOverride(color);
        proxy.setGlowing(true);
    }

    private void removeProxy(UUID frameId) {
        UUID proxyId = proxyIds.remove(frameId);
        if (proxyId == null) {
            return;
        }
        Entity proxy = Bukkit.getEntity(proxyId);
        if (proxy != null && proxy.isValid()) {
            proxy.remove();
        }
    }

    private boolean isProxy(ItemDisplay display) {
        return Byte.valueOf(PROXY_MARKER_VALUE).equals(
                display.getPersistentDataContainer().get(proxyMarkerKey, PersistentDataType.BYTE)
        );
    }
}
