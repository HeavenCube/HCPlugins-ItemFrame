package fr.noltox.hcplugins.customitemframeglowing.state;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;

/** One configured static RGB outline or shader profile carrier. */
public record FrameOutline(String id, Component buttonName, int rgb, String profileId) {

    public boolean shaderProfile() {
        return profileId != null;
    }

    public Color bukkitColor() {
        return Color.fromRGB(rgb);
    }
}
