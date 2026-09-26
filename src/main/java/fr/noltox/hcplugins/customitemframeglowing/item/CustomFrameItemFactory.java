package fr.noltox.hcplugins.customitemframeglowing.item;

import fr.noltox.hcplugins.customitemframeglowing.support.MiniMessages;
import fr.noltox.hcplugins.customitemframeglowing.config.FrameOutlineCatalog;
import fr.noltox.hcplugins.customitemframeglowing.state.CustomFrameState;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameVariant;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates and identifies the custom frame item and its placed entity.
 */
public final class CustomFrameItemFactory {

    private static final byte MARKER_VALUE = 1;
    private static final String ITEM_LORE_PATH = "item.lore";
    private static final String NATIVE_OUTLINE_VALUE = "none";

    private final NamespacedKey markerKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey outlineDyeKey;
    private final FrameOutlineCatalog outlineCatalog;
    private final Component itemName;
    private final List<Component> lore;

    private CustomFrameItemFactory(
            NamespacedKey markerKey,
            NamespacedKey variantKey,
            NamespacedKey outlineDyeKey,
            FrameOutlineCatalog outlineCatalog,
            Component itemName,
            List<Component> lore
    ) {
        this.markerKey = markerKey;
        this.variantKey = variantKey;
        this.outlineDyeKey = outlineDyeKey;
        this.outlineCatalog = outlineCatalog;
        this.itemName = itemName;
        this.lore = List.copyOf(lore);
    }

    public static CustomFrameItemFactory from(
            Plugin plugin,
            FileConfiguration config,
            FrameOutlineCatalog outlineCatalog
    ) {
        Object configuredNameValue = config.get("item.name");
        if (!(configuredNameValue instanceof String configuredName)) {
            throw new IllegalStateException("La clé de configuration 'item.name' doit être un texte.");
        }
        if (!config.isList(ITEM_LORE_PATH)) {
            throw new IllegalStateException(
                    "La clé de configuration '" + ITEM_LORE_PATH + "' doit être une liste de textes."
            );
        }
        List<?> rawLore = Objects.requireNonNull(config.getList(ITEM_LORE_PATH), ITEM_LORE_PATH);
        List<Component> configuredLore = new ArrayList<>(rawLore.size());
        for (Object loreLine : rawLore) {
            if (!(loreLine instanceof String stringLine)) {
                throw new IllegalStateException(
                        "Chaque entrée de configuration '" + ITEM_LORE_PATH + "' doit être un texte."
                );
            }
            configuredLore.add(MiniMessages.parse(stringLine));
        }
        return new CustomFrameItemFactory(
                new NamespacedKey(plugin, "custom_invisible_frame"),
                new NamespacedKey(plugin, "frame_variant"),
                new NamespacedKey(plugin, "outline_dye"),
                outlineCatalog,
                MiniMessages.parse(configuredName),
                configuredLore
        );
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(CustomFrameState.defaultState().variant().material(), amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(itemName);
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, MARKER_VALUE);
        if (!item.setItemMeta(meta)) {
            throw new IllegalStateException("Impossible d'appliquer les métadonnées au cadre personnalisé.");
        }
        return item;
    }

    /**
     * Converts marked items into the canonical item given by the command.
     *
     * <p>Variant and outline are deliberately stored only on placed frame entities,
     * never on the item returned to a player.</p>
     */
    public ItemStack canonicalize(ItemStack item) {
        if (!isCustom(item)) {
            return item;
        }
        return create(item.getAmount());
    }

    public boolean isCustom(ItemStack item) {
        return item != null && !item.isEmpty()
                && hasMarker(item.getPersistentDataContainer());
    }

    public boolean isCustom(ItemFrame frame) {
        return hasMarker(frame.getPersistentDataContainer());
    }

    /**
     * Writes the marker and complete persistent state.
     */
    public void mark(ItemFrame frame, CustomFrameState state) {
        frame.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, MARKER_VALUE);
        writeState(frame.getPersistentDataContainer(), state);
    }

    /**
     * Canonical items deliberately carry no placed-entity variant or outline state.
     */
    public CustomFrameState stateOf(ItemStack item) {
        return CustomFrameState.defaultState();
    }

    /**
     * Reads a placed frame and repairs missing or invalid state with the canonical default.
     */
    public CustomFrameState stateOf(ItemFrame frame) {
        StateRead read = readState(frame.getPersistentDataContainer());
        if (read.needsMigration()) {
            writeState(frame.getPersistentDataContainer(), read.state());
        }
        return read.state();
    }

    public void writeState(ItemFrame frame, CustomFrameState state) {
        mark(frame, state);
    }

    private StateRead readState(PersistentDataContainerView container) {
        String storedVariant = container.get(variantKey, PersistentDataType.STRING);
        if (storedVariant == null) {
            return new StateRead(CustomFrameState.defaultState(), true);
        }

        try {
            FrameVariant variant = FrameVariant.valueOf(storedVariant);
            String storedDye = container.get(outlineDyeKey, PersistentDataType.STRING);
            if (storedDye != null) {
                String outlineId = NATIVE_OUTLINE_VALUE.equals(storedDye) ? null : storedDye;
                if (outlineId != null && outlineCatalog.outline(outlineId) == null) {
                    return new StateRead(CustomFrameState.defaultState(), true);
                }
                return new StateRead(new CustomFrameState(variant, outlineId), false);
            }

            return new StateRead(CustomFrameState.defaultState(), true);
        } catch (IllegalArgumentException exception) {
            return new StateRead(CustomFrameState.defaultState(), true);
        }
    }

    private void writeState(PersistentDataContainer container, CustomFrameState state) {
        container.set(variantKey, PersistentDataType.STRING, state.variant().name());
        String color = state.outlineId() == null
                ? NATIVE_OUTLINE_VALUE
                : state.outlineId();
        container.set(outlineDyeKey, PersistentDataType.STRING, color);
    }

    private boolean hasMarker(PersistentDataContainerView container) {
        return Byte.valueOf(MARKER_VALUE).equals(container.get(markerKey, PersistentDataType.BYTE));
    }

    private record StateRead(CustomFrameState state, boolean needsMigration) {
    }
}
