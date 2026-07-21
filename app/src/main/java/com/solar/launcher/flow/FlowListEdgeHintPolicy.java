package com.solar.launcher.flow;

/**
 * 2026-07-20 — When Flow flipped tracklist tip (↻/↺) may show at list ends.
 * Layman: only after you have scrolled the song list — not the first open — tip says
 * keep scrolling to leave the list and flip back to album covers.
 * Tech: pure gate; FlowFlipController/FlowView paint the label above the back face.
 * Reversal: always return NONE; delete tip draw in FlowView.
 */
public final class FlowListEdgeHintPolicy {

    public static final String NONE = "NONE";
    /** At first row — further up-scroll returns to covers (↺). */
    public static final String TOP = "TOP";
    /** At last row — further down-scroll returns to covers / next album (↻). */
    public static final String BOTTOM = "BOTTOM";

    private FlowListEdgeHintPolicy() {}

    /**
     * 2026-07-20 — Edge tip mode after the user has moved the flipped list at least once.
     * Layman: tip only when you wheel to the top or bottom yourself.
     * Tech: userScrolledBack false on first land (setBackContent) → NONE even at index 0.
     */
    public static String edgeHint(boolean userScrolledBack, boolean atTop, boolean atBottom) {
        if (!userScrolledBack) return NONE;
        if (atTop) return TOP;
        if (atBottom) return BOTTOM;
        return NONE;
    }

    /**
     * 2026-07-20 — Short label painted above the flipped cover.
     * Layman: spinning arrow = keep scrolling that way to leave the track list.
     * Same ↻/↺ sense as NP volume tips (↻ = up / past first row, ↺ = down / past last).
     */
    public static String hintLabel(String mode) {
        if (TOP.equals(mode)) return "\u21BB covers"; // ↻ continue up past first → covers
        if (BOTTOM.equals(mode)) return "\u21BA covers"; // ↺ continue down past last → covers
        return "";
    }

    /**
     * 2026-07-20 — True when index is first or last row of a non-empty list.
     */
    public static boolean atTop(int backIndex, int rowCount) {
        return rowCount > 0 && backIndex <= 0;
    }

    /** Last row of flipped tracklist. */
    public static boolean atBottom(int backIndex, int rowCount) {
        return rowCount > 0 && backIndex >= rowCount - 1;
    }
}
