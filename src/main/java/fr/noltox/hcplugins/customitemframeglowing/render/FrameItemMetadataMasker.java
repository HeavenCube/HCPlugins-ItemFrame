package fr.noltox.hcplugins.customitemframeglowing.render;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemModel;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hides the real ItemFrame item only on clients while its visible ItemDisplay proxy is active.
 *
 * <p>The replacement stack deliberately remains non-empty and uses the vanilla invisible
 * {@code minecraft:air} item model. The client therefore retains occupied-frame interaction
 * semantics while rendering only the visual proxy. The membership lookup is limited to a
 * concurrent set of entity ids so the Netty packet thread never touches Bukkit state.</p>
 */
public final class FrameItemMetadataMasker {

    private static final ResourceLocation INVISIBLE_MODEL = new ResourceLocation("minecraft:air");

    private final Set<Integer> maskedFrameIds = ConcurrentHashMap.newKeySet();
    private final PacketListenerCommon listener = new SimplePacketListenerAbstract(PacketListenerPriority.HIGHEST) {
        @Override
        public void onPacketPlaySend(PacketPlaySendEvent event) {
            if (maskedFrameIds.isEmpty() || event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA) {
                return;
            }

            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (!maskedFrameIds.contains(packet.getEntityId())) {
                return;
            }

            List<EntityData<?>> metadata = packet.getEntityMetadata();
            List<EntityData<?>> maskedMetadata = maskItemMetadata(metadata);
            if (maskedMetadata == metadata) {
                return;
            }
            packet.setEntityMetadata(maskedMetadata);
            event.markForReEncode(true);
        }
    };
    private boolean registered;

    private static List<EntityData<?>> itemMetadata(ItemFrame frame, boolean masked) {
        List<EntityData<?>> metadata = SpigotConversionUtil.getEntityMetadata(frame);
        List<EntityData<?>> itemMetadata = new ArrayList<>();
        for (EntityData<?> data : metadata) {
            if (data.getType() == EntityDataTypes.ITEMSTACK) {
                itemMetadata.add(masked
                        ? new EntityData<>(data.getIndex(), EntityDataTypes.ITEMSTACK,
                        invisibleOccupiedItem((ItemStack) data.getValue()))
                        : data);
            }
        }
        return itemMetadata;
    }

    private static List<EntityData<?>> maskItemMetadata(List<EntityData<?>> metadata) {
        List<EntityData<?>> maskedMetadata = null;
        for (int index = 0; index < metadata.size(); index++) {
            EntityData<?> data = metadata.get(index);
            if (data.getType() != EntityDataTypes.ITEMSTACK) {
                continue;
            }
            if (maskedMetadata == null) {
                maskedMetadata = new ArrayList<>(metadata);
            }
            maskedMetadata.set(index, new EntityData<>(data.getIndex(), EntityDataTypes.ITEMSTACK,
                    invisibleOccupiedItem((ItemStack) data.getValue())));
        }
        return maskedMetadata == null ? metadata : maskedMetadata;
    }

    private static ItemStack invisibleOccupiedItem(ItemStack original) {
        ItemStack masked = original.isEmpty()
                ? ItemStack.builder().type(ItemTypes.PAPER).amount(1).build()
                : original.copy();
        masked.setComponent(ComponentTypes.ITEM_MODEL, new ItemModel(INVISIBLE_MODEL));
        return masked;
    }

    public void start() {
        if (registered) {
            return;
        }
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().isTerminated()) {
            throw new IllegalStateException("PacketEvents doit être initialisé avant HCItemFrame.");
        }
        PacketEvents.getAPI().getEventManager().registerListener(listener);
        registered = true;
    }

    public void mask(ItemFrame frame) {
        if (maskedFrameIds.add(frame.getEntityId())) {
            sendItemMetadata(frame, true);
        }
    }

    public void unmask(ItemFrame frame) {
        if (maskedFrameIds.remove(frame.getEntityId())) {
            sendItemMetadata(frame, false);
        }
    }

    public void forget(ItemFrame frame) {
        maskedFrameIds.remove(frame.getEntityId());
    }

    public void shutdown() {
        maskedFrameIds.clear();
        try {
            if (registered && PacketEvents.getAPI() != null && !PacketEvents.getAPI().isTerminated()) {
                PacketEvents.getAPI().getEventManager().unregisterListener(listener);
            }
        } finally {
            registered = false;
        }
    }

    private void sendItemMetadata(ItemFrame frame, boolean masked) {
        if (PacketEvents.getAPI() == null || PacketEvents.getAPI().isTerminated()) {
            return;
        }
        List<EntityData<?>> itemMetadata = itemMetadata(frame, masked);
        if (itemMetadata.isEmpty()) {
            return;
        }
        for (Player viewer : frame.getTrackedBy()) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(
                    viewer,
                    new WrapperPlayServerEntityMetadata(frame.getEntityId(), itemMetadata)
            );
        }
    }
}
