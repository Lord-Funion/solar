package com.solar.launcher.media;

/**
 * 2026-07-18 — Pure remaining/done math for NP Flow Play/Pause hold tip.
 * Layman: after Options tip goes away, show Flow tip up to three volume pulses.
 * Tech: no Android deps — unit-tested ladder for Options → Flow → none.
 * 2026-07-20 — Prefer {@link NowPlayingTipPolicy} for volume → Options → Flow (seen/used prefs).
 * Reversal: inline remaining-- in MainActivity only.
 */
public final class FlowHoldHintPolicy {

    public static final int DEFAULT_REMAINING = 3;

    private FlowHoldHintPolicy() {}

    /**
     * 2026-07-18 — Which NP tip to show given prefs + Flow feature flag.
     * Options until dismissed; then Flow while remaining &gt; 0; else none.
     */
    public static String playerHintMode(boolean holdBackDismissed, boolean flowHintDone,
            int flowRemaining, boolean flowEnabled) {
        if (!holdBackDismissed) return "HOLD_BACK_OPTIONS";
        if (!flowHintDone && flowRemaining > 0 && flowEnabled) return "HOLD_PLAY_FLOW";
        return "NONE";
    }

    /**
     * 2026-07-18 — After one Flow tip volume-pulse show: new remaining and whether done.
     * Returns { remaining, doneFlag } where doneFlag is 1 if exhausted.
     */
    public static int[] afterPresented(int remainingBefore) {
        int r = remainingBefore - 1;
        if (r < 0) r = 0;
        return new int[] { r, r <= 0 ? 1 : 0 };
    }

    /** 2026-07-18 — Clamp loaded remaining pref. */
    public static int clampRemaining(int remaining) {
        if (remaining < 0) return 0;
        if (remaining > DEFAULT_REMAINING) return DEFAULT_REMAINING;
        return remaining;
    }
}
