package fr.noltox.hcplugins.customitemframeglowing.config;

import fr.noltox.hcplugins.core.api.config.BukkitYaml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameOutlineCatalogTest {

    @TempDir
    Path directory;

    @Test
    void parsesStaticRgbAndShaderProfiles() throws IOException {
        var catalog = load(defaults());

        var white = catalog.outline("white");
        assertEquals(0xF9FFFE, white.rgb());
        assertFalse(white.shaderProfile());

        var rainbow = catalog.outline("rainbow");
        assertEquals("rainbow", rainbow.profileId());
        assertTrue(rainbow.shaderProfile());
    }

    @Test
    void rejectsInvalidHexUnknownProfilesAndAmbiguousEntries() throws IOException {
        String defaults = defaults();
        assertThrows(IllegalStateException.class,
                () -> load(defaults.replace("#F9FFFE", "#XYZXYZ")));
        assertThrows(IllegalStateException.class,
                () -> load(defaults.replace("profile: rainbow", "profile: absent")));
        assertThrows(IllegalStateException.class,
                () -> load(defaults.replace("    color: \"#F9FFFE\"", "    color: \"#F9FFFE\"\n    profile: rainbow")));
    }

    @Test
    void rejectsStaticRgbReservedForShaderTransport() throws IOException {
        assertThrows(IllegalStateException.class,
                () -> load(defaults().replace("#F9FFFE", "#FF5555")));
    }

    private FrameOutlineCatalog load(String text) throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, text);
        return FrameOutlineCatalog.from(BukkitYaml.load(file));
    }

    private String defaults() throws IOException {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            if (stream == null) {
                throw new IOException("Configuration embarquée absente.");
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
