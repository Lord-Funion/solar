package com.solar.launcher;

/**
 * 2026-07-20 — Shared CW/CCW menu nav contract (home + settings root gold feel).
 * Layman: one dial click moves one highlight; the page only shifts at the edges; heavy work waits.
 * Technical: ±1 on KEY; {@link FocusScrollHelper#requestFocusEdgeOnly} / home index hop;
 * decoration via {@link Y1RowChromePolicy} {@code setSelected}; no paced paint;
 * short haptic whenever focus moved. Long song ListViews keep coalesce + edge sticky separately.
 * Reversal: delete; restore per-screen one-offs.
 */
public final class WheelMenuNavPolicy {

    /** 2026-07-20 — Short buzz per focus hop (ms). Plan: 1–5. */
    public static final int HAPTIC_MS = 4;

    /** 2026-07-20 — Min gap between buzzes so binder/motor do not stack. */
    public static final long HAPTIC_MIN_GAP_MS = 28L;

    private WheelMenuNavPolicy() {}

    /**
     * 2026-07-20 — Whether this notch should click/vibrate.
     * Layman: every real highlight move gets a tiny tick.
     * Technical: {@code moved} only — wrap-suppress no longer skips haptic (was soft-spin gate).
     * Reversal: {@code return moved && !suppressWrapAround}.
     */
    public static boolean shouldHaptic(boolean moved, boolean suppressWrapAroundIgnored) {
        return moved;
    }

    /**
     * 2026-07-20 — Short ScrollView menus use edge-only focus (settings root path).
     */
    public static boolean useEdgeOnlyFocus() {
        return true;
    }
}
