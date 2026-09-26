package fr.noltox.hcplugins.customitemframeglowing.state;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * The two vanilla entity and item variants supported by a custom frame.
 */
public enum FrameVariant {

    ITEM_FRAME(Material.ITEM_FRAME, EntityType.ITEM_FRAME),
    GLOW_ITEM_FRAME(Material.GLOW_ITEM_FRAME, EntityType.GLOW_ITEM_FRAME);

    private final Material material;
    private final EntityType entityType;

    FrameVariant(Material material, EntityType entityType) {
        this.material = material;
        this.entityType = entityType;
    }

    public Material material() {
        return material;
    }

    public EntityType entityType() {
        return entityType;
    }
}
