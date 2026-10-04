package fr.noltox.hcplugins.customitemframeglowing.render;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FrameProxyIndexTest {

    @Test
    void externalRemovalFindsOwnerAndForgetsBothDirections() {
        var index = new FrameProxyIndex();
        UUID frame = UUID.randomUUID();
        UUID proxy = UUID.randomUUID();
        index.put(frame, proxy);
        assertEquals(frame, index.removeProxy(proxy));
        assertNull(index.proxy(frame));
        assertNull(index.removeProxy(proxy));
    }

    @Test
    void oldRemovalCannotDetachReplacementProxy() {
        var index = new FrameProxyIndex();
        UUID frame = UUID.randomUUID();
        UUID old = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        index.put(frame, old);
        index.put(frame, replacement);
        assertNull(index.removeProxy(old));
        assertEquals(replacement, index.proxy(frame));
        assertEquals(replacement, index.removeFrame(frame));
        assertNull(index.removeProxy(replacement));
    }

    @Test
    void reassignmentAndShutdownLeaveNoStaleOwnership() {
        var index = new FrameProxyIndex();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID proxy = UUID.randomUUID();
        index.put(first, proxy);
        index.put(second, proxy);
        assertNull(index.proxy(first));
        assertEquals(proxy, index.proxy(second));
        index.clear();
        assertNull(index.proxy(second));
        assertNull(index.removeProxy(proxy));
    }
}
