package fr.noltox.hcplugins.customitemframeglowing.state;

/**
 * Persistent visual state of a custom item frame. A null outline means the native white outline.
 */
public record CustomFrameState(FrameVariant variant, String outlineId) {

    public static final String DEFAULT_OUTLINE_ID = "white";

    public static CustomFrameState defaultState() {
        return new CustomFrameState(FrameVariant.GLOW_ITEM_FRAME, DEFAULT_OUTLINE_ID);
    }
}
