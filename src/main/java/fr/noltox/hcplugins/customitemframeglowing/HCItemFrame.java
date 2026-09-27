package fr.noltox.hcplugins.customitemframeglowing;

import fr.noltox.hcplugins.core.api.HCPluginsCore;
import fr.noltox.hcplugins.core.api.command.CoreCommandRegistration;
import fr.noltox.hcplugins.core.api.config.BukkitYaml;
import fr.noltox.hcplugins.core.api.config.HCPluginFiles;
import fr.noltox.hcplugins.customitemframeglowing.command.GiveInvisibleFrameCommand;
import fr.noltox.hcplugins.customitemframeglowing.config.FrameOutlineCatalog;
import fr.noltox.hcplugins.customitemframeglowing.dialog.FrameCustomizationDialog;
import fr.noltox.hcplugins.customitemframeglowing.dialog.FrameDialogMessages;
import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.listener.CustomFrameItemProtectionListener;
import fr.noltox.hcplugins.customitemframeglowing.listener.InvisibleFrameListener;
import fr.noltox.hcplugins.customitemframeglowing.message.PluginMessages;
import fr.noltox.hcplugins.customitemframeglowing.render.HexFrameDisplayRenderer;
import fr.noltox.hcplugins.customitemframeglowing.service.CustomFrameService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;

public final class HCItemFrame extends JavaPlugin {

    private static final String CONFIG_FILE = "config.yml";

    private HexFrameDisplayRenderer hexRenderer;
    private File configurationFile;
    private RuntimeComponents runtimeComponents;
    private CoreCommandRegistration commandRegistration;

    @Override
    public void onEnable() {
        hexRenderer = new HexFrameDisplayRenderer(this);
        hexRenderer.removeOrphanedProxies();
        configurationFile = HCPluginFiles.singleConfiguration(this).toFile();
        if (!reloadRuntimeComponents()) {
            throw new IllegalStateException("Impossible de charger la configuration initiale du plugin.");
        }

        GiveInvisibleFrameCommand commands = new GiveInvisibleFrameCommand(
                this,
                HCPluginsCore.translations(this),
                this::itemFactory,
                this::messages,
                this::reloadRuntimeComponents
        );
        commandRegistration = HCPluginsCore.require(this).register(
                this,
                "itemframe",
                "Cadres invisibles personnalisés",
                List.of(),
                commands
        );
    }

    @Override
    public void onDisable() {
        try {
            if (commandRegistration != null) {
                commandRegistration.close();
            }
        } finally {
            if (hexRenderer != null && runtimeComponents != null) {
                try {
                    runtimeComponents.shutdown();
                } finally {
                    hexRenderer.shutdown(runtimeComponents.itemFactory());
                }
            } else if (hexRenderer != null) {
                hexRenderer.shutdown();
            }
        }
    }

    /**
     * Rebuilds every configuration-dependent service.
     */
    private boolean reloadRuntimeComponents() {
        try {
            ensureConfigurationFile();
            FileConfiguration configuration = loadConfiguration();

            FrameOutlineCatalog outlineCatalog = FrameOutlineCatalog.from(configuration);
            CustomFrameItemFactory itemFactory = CustomFrameItemFactory.from(this, configuration, outlineCatalog);
            PluginMessages messages = PluginMessages.from(configuration);
            FrameDialogMessages dialogMessages = FrameDialogMessages.from(configuration);
            CustomFrameService frameService = new CustomFrameService(this, itemFactory, hexRenderer, outlineCatalog);
            FrameCustomizationDialog customizationDialog = new FrameCustomizationDialog(
                    this,
                    itemFactory,
                    frameService,
                    dialogMessages,
                    outlineCatalog
            );
            InvisibleFrameListener listener = new InvisibleFrameListener(
                    this,
                    itemFactory,
                    frameService,
                    customizationDialog,
                    messages
            );
            CustomFrameItemProtectionListener protectionListener = new CustomFrameItemProtectionListener(itemFactory);
            RuntimeComponents reloadedComponents = new RuntimeComponents(
                    itemFactory,
                    messages,
                    listener,
                    protectionListener
            );

            replaceRuntimeComponents(reloadedComponents);
            return true;
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Impossible de recharger " + CONFIG_FILE + ".", exception);
            return false;
        }
    }

    private void ensureConfigurationFile() {
        HCPluginFiles.copyDefault(this, CONFIG_FILE, configurationFile.toPath());
    }

    /**
     * Uses bundled defaults for new keys while rejecting malformed administrator configuration.
     */
    private FileConfiguration loadConfiguration() {
        InputStream resource = getResource(CONFIG_FILE);
        if (resource == null) {
            throw new IllegalStateException("La ressource " + CONFIG_FILE + " est introuvable dans le JAR.");
        }
        return BukkitYaml.load(configurationFile.toPath(), resource);
    }

    /**
     * Installs a complete listener set, rolling back to the active one if initialization fails.
     */
    private void replaceRuntimeComponents(RuntimeComponents reloadedComponents) {
        RuntimeComponents previousComponents = runtimeComponents;
        if (previousComponents != null) {
            previousComponents.unregister();
        }

        try {
            reloadedComponents.register(this);
            reloadedComponents.invisibleFrameListener().synchronizeLoadedFrames();
            reloadedComponents.itemProtectionListener().synchronizeLoadedItems();
        } catch (RuntimeException exception) {
            reloadedComponents.unregister();
            reloadedComponents.shutdown();
            if (previousComponents != null) {
                try {
                    previousComponents.register(this);
                    previousComponents.invisibleFrameListener().synchronizeLoadedFrames();
                    previousComponents.itemProtectionListener().synchronizeLoadedItems();
                } catch (RuntimeException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
            }
            throw exception;
        }

        runtimeComponents = reloadedComponents;
        if (previousComponents != null) {
            previousComponents.shutdown();
        }
    }

    private CustomFrameItemFactory itemFactory() {
        return runtimeComponents.itemFactory();
    }

    private PluginMessages messages() {
        return runtimeComponents.messages();
    }

    private record RuntimeComponents(
            CustomFrameItemFactory itemFactory,
            PluginMessages messages,
            InvisibleFrameListener invisibleFrameListener,
            CustomFrameItemProtectionListener itemProtectionListener
    ) {

        private void register(JavaPlugin plugin) {
            Bukkit.getPluginManager().registerEvents(invisibleFrameListener, plugin);
            Bukkit.getPluginManager().registerEvents(itemProtectionListener, plugin);
        }

        private void unregister() {
            HandlerList.unregisterAll(invisibleFrameListener);
            HandlerList.unregisterAll(itemProtectionListener);
        }

        private void shutdown() {
            invisibleFrameListener.shutdown();
        }
    }
}
