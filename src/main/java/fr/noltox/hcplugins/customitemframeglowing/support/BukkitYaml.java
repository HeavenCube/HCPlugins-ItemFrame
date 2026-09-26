package fr.noltox.hcplugins.customitemframeglowing.support;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads Bukkit YAML while rejecting duplicate administrator keys. */
public final class BukkitYaml {

    private BukkitYaml() {
    }

    public static YamlConfiguration load(Path path) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            rejectDuplicateKeys(path, content);
            configuration.loadFromString(content);
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Impossible de lire " + path.getFileName() + ".", exception);
        }
    }

    /** Loads a file and applies bundled defaults. The supplied stream is consumed and closed. */
    public static YamlConfiguration load(Path path, InputStream defaultsStream) {
        try (InputStream stream = defaultsStream;
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            YamlConfiguration configuration = load(path);
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.load(reader);
            configuration.setDefaults(defaults);
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Impossible de lire les valeurs par défaut de "
                    + path.getFileName() + ".", exception);
        }
    }

    private static void rejectDuplicateKeys(Path path, String content) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try {
            new Yaml(new SafeConstructor(options)).load(content);
        } catch (YAMLException exception) {
            throw new IllegalStateException(
                    path.getFileName() + " contient un YAML invalide ou des clés dupliquées.",
                    exception
            );
        }
    }
}
