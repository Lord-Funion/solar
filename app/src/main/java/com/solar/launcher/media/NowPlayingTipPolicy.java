package com.solar.launcher.media;

/**
 * 2026-07-20 — Now Playing first-play tip ladder (volume → Options → Flow → none).
 * Was: Options → Flow×3 remaining volume pulses ({@link FlowHoldHintPolicy} count).
 * Layman: teach volume arrows, then Back for Options, then hold Play for Flow — each once.
 * Tech: prefs for seen/used; Flow stays until opened from NP when enabled.
 * Reversal: restore FlowHoldHintPolicy.playerHintMode remaining-count only.
 */
public final class NowPlayingTipPolicy {

    public static final String NONE = "NONE";
    public static final String VOLUME_ARROWS = "VOLUME_ARROWS";
    public static final String HOLD_BACK_OPTIONS = "HOLD_BACK_OPTIONS";
    public static final String HOLD_PLAY_FLOW = "HOLD_PLAY_FLOW";

    /** How long a session tip stays readable before fade-out (loading-style). */
    public static final long SESSION_TIP_VISIBLE_MS = 2800L;
    public static final long SESSION_TIP_FADE_MS = TransportHintChromePolicy.LIVE_HOLD_FADE_MS;

    private NowPlayingTipPolicy() {}

    /**
     * 2026-07-20 — Which NP tip to show given prefs + Flow feature flag.
     * Volume until seen; Options until used from NP; Flow until opened from NP (if enabled).
     */
    public static String playerHintMode(boolean volumeTipSeen, boolean optionsUsedFromNp,
            boolean flowOpenedFromNp, boolean flowEnabled) {
        if (!volumeTipSeen) return VOLUME_ARROWS;
        if (!optionsUsedFromNp) return HOLD_BACK_OPTIONS;
        if (flowEnabled && !flowOpenedFromNp) return HOLD_PLAY_FLOW;
        return NONE;
    }

    /**
     * 2026-07-20 — Bridge old FlowHoldHintPolicy prefs into the new ladder.
     * Layman: if you already finished Options/Flow teaching, skip those tips.
     * Tech: holdBackDismissed → optionsUsed; flowHintDone → flowOpened; volume starts unseen.
     */
    public static String playerHintModeCompat(boolean volumeTipSeen, boolean holdBackDismissed,
            boolean flowHintDone, boolean flowEnabled) {
        return playerHintMode(volumeTipSeen, holdBackDismissed, flowHintDone, flowEnabled);
    }
}
