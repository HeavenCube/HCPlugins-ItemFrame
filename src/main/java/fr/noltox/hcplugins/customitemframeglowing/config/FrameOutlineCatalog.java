package fr.noltox.hcplugins.customitemframeglowing.config;

import fr.noltox.hcglowprofiles.GlowProfiles;
import fr.noltox.hcplugins.customitemframeglowing.support.MiniMessages;
import fr.noltox.hcplugins.customitemframeglowing.state.CustomFrameState;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameOutline;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Ordered, strictly validated outline options used by persistence, dialogs and rendering. */
public final class FrameOutlineCatalog {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9_-]*");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    private final Map<String, FrameOutline> entries;

    private FrameOutlineCatalog(Map<String, FrameOutline> outlines) {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(outlines));
    }

    public static FrameOutlineCatalog from(FileConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("outlines");
        if (section == null || section.getKeys(false).isEmpty()) {
            throw invalid("La section 'outlines' doit contenir au moins un contour.");
        }

        Map<String, FrameOutline> outlines = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            if (!IDENTIFIER.matcher(id).matches()) {
                throw invalid("L'identifiant de contour '" + id + "' est invalide.");
            }
            outlines.put(id, readOutline(configuration, section, id));
        }
        if (!outlines.containsKey(CustomFrameState.DEFAULT_OUTLINE_ID)) {
            throw invalid("Le contour par défaut '" + CustomFrameState.DEFAULT_OUTLINE_ID + "' est obligatoire.");
        }
        return new FrameOutlineCatalog(outlines);
    }

    private static FrameOutline readOutline(
            FileConfiguration configuration,
            ConfigurationSection parent,
            String id
    ) {
        String path = "outlines." + id;
        if (parent.getConfigurationSection(id) == null) {
            throw invalid("Le contour '" + path + "' doit être une section YAML.");
        }
        Object rawColor = configuration.get(path + ".color");
        Object rawProfile = configuration.get(path + ".profile");
        if ((rawColor == null) == (rawProfile == null)) {
            throw invalid("Le contour '" + id + "' doit déclarer exactement 'color' ou 'profile'.");
        }
        var buttonName = MiniMessages.parse(requiredString(configuration, path + ".button-name"));
        if (rawColor != null) {
            String color = requiredString(configuration, path + ".color");
            if (!HEX.matcher(color).matches()) {
                throw invalid("La couleur de '" + id + "' doit respecter #RRGGBB.");
            }
            int rgb = Integer.parseInt(color.substring(1), 16);
            if (GlowProfiles.isReservedCarrierRgb(rgb)) {
                throw invalid("La couleur " + color + " de '" + id
                        + "' est réservée au transport d'un profil shader.");
            }
            return new FrameOutline(id, buttonName, rgb, null);
        }
        String profileId = requiredString(configuration, path + ".profile");
        GlowProfiles.Profile profile = GlowProfiles.find(profileId)
                .orElseThrow(() -> invalid("Le profil shader '" + profileId + "' de '" + id + "' est inconnu."));
        return new FrameOutline(id, buttonName, profile.carrier().rgb(), profileId);
    }

    private static String requiredString(FileConfiguration configuration, String path) {
        Object value = configuration.get(path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid("La clé '" + path + "' doit être un texte non vide.");
        }
        return text;
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException("Configuration invalide : " + message);
    }

    public Map<String, FrameOutline> outlines() {
        return entries;
    }

    public FrameOutline outline(String id) {
        return entries.get(id);
    }
}
