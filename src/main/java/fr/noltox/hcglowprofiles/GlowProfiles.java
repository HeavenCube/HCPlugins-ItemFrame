package fr.noltox.hcglowprofiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Profile carriers from the HeavenCube 26.2 resource pack catalogue. */
public final class GlowProfiles {

    public enum EffectType { STATIC, GRADIENT, RAINBOW, PULSE, ANIMATED_GRADIENT }

    public record Carrier(String name, int rgb, char legacyCode) { }

    public record Profile(String id, Carrier carrier, EffectType effectType) { }

    private static final List<Profile> PROFILES = List.of(
            new Profile("pink-static", new Carrier("LIGHT_PURPLE", 0xFF55FF, 'd'), EffectType.STATIC),
            new Profile("heaven-gradient", new Carrier("AQUA", 0x55FFFF, 'b'), EffectType.ANIMATED_GRADIENT),
            new Profile("rainbow", new Carrier("RED", 0xFF5555, 'c'), EffectType.RAINBOW),
            new Profile("emerald-pulse", new Carrier("GREEN", 0x55FF55, 'a'), EffectType.PULSE),
            new Profile("sunset-gradient", new Carrier("GOLD", 0xFFAA00, '6'), EffectType.GRADIENT)
    );
    private static final Map<String, Profile> BY_ID = PROFILES.stream()
            .collect(Collectors.toUnmodifiableMap(Profile::id, Function.identity()));

    private GlowProfiles() { }

    public static List<Profile> all() { return PROFILES; }

    public static Optional<Profile> find(String id) { return Optional.ofNullable(BY_ID.get(id)); }

    public static boolean isReservedCarrierRgb(int rgb) {
        return PROFILES.stream().anyMatch(profile -> profile.carrier().rgb() == (rgb & 0xFFFFFF));
    }
}
