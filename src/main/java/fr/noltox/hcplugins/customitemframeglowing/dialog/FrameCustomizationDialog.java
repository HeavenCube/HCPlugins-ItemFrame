package fr.noltox.hcplugins.customitemframeglowing.dialog;

import fr.noltox.hcplugins.customitemframeglowing.item.CustomFrameItemFactory;
import fr.noltox.hcplugins.customitemframeglowing.config.FrameOutlineCatalog;
import fr.noltox.hcplugins.customitemframeglowing.permission.Permissions;
import fr.noltox.hcplugins.customitemframeglowing.service.CustomFrameService;
import fr.noltox.hcplugins.customitemframeglowing.state.CustomFrameState;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameOutline;
import fr.noltox.hcplugins.customitemframeglowing.state.FrameVariant;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds one native Paper multi-action dialog for a specific custom frame.
 */
public final class FrameCustomizationDialog {

    public static final String PERMISSION = Permissions.CUSTOMIZE;
    private static final double MAX_DISTANCE_SQUARED = 64.0D;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();
    private final Plugin plugin;
    private final CustomFrameItemFactory itemFactory;
    private final CustomFrameService frameService;
    private final FrameDialogMessages messages;
    private final FrameOutlineCatalog outlineCatalog;
    private boolean active = true;

    public FrameCustomizationDialog(
            Plugin plugin,
            CustomFrameItemFactory itemFactory,
            CustomFrameService frameService,
            FrameDialogMessages messages,
            FrameOutlineCatalog outlineCatalog
    ) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.frameService = frameService;
        this.messages = messages;
        this.outlineCatalog = outlineCatalog;
    }

    public void open(Player player, ItemFrame frame) {
        tryOpen(player, frame);
    }

    public boolean tryOpen(Player player, ItemFrame frame) {
        if (!canCustomize(player, frame, frame.getWorld().getUID())) {
            return false;
        }
        ItemFrame synchronizedFrame = frameService.synchronize(frame);
        CustomFrameState state = itemFactory.stateOf(synchronizedFrame);
        UUID frameId = synchronizedFrame.getUniqueId();
        UUID worldId = synchronizedFrame.getWorld().getUID();
        List<ActionButton> actions = new ArrayList<>();
        actions.add(variantButton(frameId, worldId, state, FrameVariant.ITEM_FRAME));
        actions.add(variantButton(frameId, worldId, state, FrameVariant.GLOW_ITEM_FRAME));
        for (FrameOutline outline : outlineCatalog.outlines().values()) {
            actions.add(outlineButton(frameId, worldId, state, outline));
        }

        ActionButton closeButton = ActionButton.create(
                messages.close(),
                messages.close(),
                120,
                DialogAction.customClick(
                        (response, audience) -> scheduleClose(audience, frameId, worldId),
                        CALLBACK_OPTIONS
                )
        );
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.title())
                        .externalTitle(messages.title())
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(List.of(DialogBody.plainMessage(messages.description(), 320)))
                        .build())
                .type(DialogType.multiAction(actions, closeButton, 4)));
        player.showDialog(dialog);
        return true;
    }

    /**
     * Invalidates callbacks belonging to a configuration generation that has been replaced.
     */
    public void shutdown() {
        active = false;
    }

    private ActionButton variantButton(UUID frameId, UUID worldId, CustomFrameState state, FrameVariant variant) {
        Component label = variant == FrameVariant.ITEM_FRAME ? messages.normalFrame() : messages.glowFrame();
        if (state.variant() == variant) {
            label = label.append(Component.space()).append(messages.selected());
        }
        return ActionButton.create(label, label, 170, DialogAction.customClick(
                (response, audience) -> scheduleVariantChange(audience, frameId, worldId, variant),
                CALLBACK_OPTIONS
        ));
    }

    private ActionButton outlineButton(UUID frameId, UUID worldId, CustomFrameState state, FrameOutline outline) {
        boolean selected = outline.id().equals(state.outlineId());
        Component label = outline.buttonName();
        if (selected) {
            label = label.append(Component.space()).append(messages.selected());
        }
        return ActionButton.create(label, label, 170, DialogAction.customClick(
                (response, audience) -> scheduleOutlineChange(audience, frameId, worldId, outline.id()),
                CALLBACK_OPTIONS
        ));
    }

    private void scheduleVariantChange(Object audience, UUID frameId, UUID worldId, FrameVariant variant) {
        if (active && audience instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> withFrame(player, frameId, worldId,
                    frame -> open(player, frameService.updateVariant(frame, variant))));
        }
    }

    private void scheduleOutlineChange(Object audience, UUID frameId, UUID worldId, String selectedOutlineId) {
        if (active && audience instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> withFrame(player, frameId, worldId, frame -> {
                CustomFrameState current = itemFactory.stateOf(frame);
                frameService.updateOutline(frame, selectedOutlineId.equals(current.outlineId()) ? null : selectedOutlineId);
                open(player, frame);
            }));
        }
    }

    private void scheduleClose(Object audience, UUID frameId, UUID worldId) {
        if (active && audience instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> withFrame(player, frameId, worldId, ignored -> player.closeDialog()));
        }
    }

    private void withFrame(Player player, UUID frameId, UUID worldId, java.util.function.Consumer<ItemFrame> action) {
        Entity entity = Bukkit.getEntity(frameId);
        if (!(entity instanceof ItemFrame frame) || !canCustomize(player, frame, worldId)) {
            return;
        }
        action.accept(frame);
    }

    private boolean canCustomize(Player player, ItemFrame frame, UUID worldId) {
        return active
                && player.isOnline()
                && player.hasPermission(PERMISSION)
                && player.getWorld().getUID().equals(worldId)
                && frame.isValid()
                && itemFactory.isCustom(frame)
                && frame.getWorld().getUID().equals(worldId)
                && player.getLocation().distanceSquared(frame.getLocation()) <= MAX_DISTANCE_SQUARED;
    }
}
