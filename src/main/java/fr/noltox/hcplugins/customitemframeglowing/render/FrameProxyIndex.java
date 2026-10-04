package fr.noltox.hcplugins.customitemframeglowing.render;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bidirectional UUID ownership, accessed only on the server thread. */
final class FrameProxyIndex {

    private final Map<UUID, UUID> byFrame = new HashMap<>();
    private final Map<UUID, UUID> byProxy = new HashMap<>();

    UUID proxy(UUID frameId) {
        return byFrame.get(frameId);
    }

    void put(UUID frameId, UUID proxyId) {
        removeFrame(frameId);
        removeProxy(proxyId);
        byFrame.put(frameId, proxyId);
        byProxy.put(proxyId, frameId);
    }

    UUID removeFrame(UUID frameId) {
        UUID proxyId = byFrame.remove(frameId);
        if (proxyId != null) {
            byProxy.remove(proxyId);
        }
        return proxyId;
    }

    UUID removeProxy(UUID proxyId) {
        UUID frameId = byProxy.remove(proxyId);
        if (frameId != null) {
            byFrame.remove(frameId);
        }
        return frameId;
    }

    void clear() {
        byFrame.clear();
        byProxy.clear();
    }
}
