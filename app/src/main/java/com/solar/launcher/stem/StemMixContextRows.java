package com.solar.launcher.stem;

/**
 * Slot / session context row labels for Stem/Mix jam holds.
 * Layman: hold Prev/Next → options for that track; Play → jam menu; Pause / Home leave.
 * Was: parallel transitionPanel modal. Reversal: showContextRows in StemPlayerHost.
 * Technical: labels only — MainActivity wires ThemedContextMenu actions.
 * 2026-07-21 — Pause + Home rows (Exit was Home chip only).
 */
public final class StemMixContextRows {
    /** Hold Prev/Next slot — Replace / Queue / Start next / Scrub. 2026-07-21 */
    public static final int SLOT_REPLACE = 0;
    public static final int SLOT_PLAY_QUEUE = 1;
    public static final int SLOT_START_NEXT = 2;
    public static final int SLOT_SCRUB = 3;
    public static final int SLOT_TRANSITION_LONG = 4;
    public static final int SLOT_TRANSITION_OVERLAP = 5;
    public static final int SLOT_TRANSITION_WAVE = 6;
    public static final int SLOT_TRANSITION_INSTANT = 7;
    public static final int SLOT_ROW_COUNT = 8;

    /**
     * Session / Play context — queue, Pause, TRANSITION, Home exit.
     * Was: queue + TRANSITION only (Exit via Home chip). Reversal: drop PAUSE/HOME indices.
     * 2026-07-21
     */
    public static final int SESSION_PLAY_QUEUE = 0;
    public static final int SESSION_PAUSE = 1;
    public static final int SESSION_TRANSITION_LONG = 2;
    public static final int SESSION_TRANSITION_OVERLAP = 3;
    public static final int SESSION_TRANSITION_WAVE = 4;
    public static final int SESSION_HOME = 5;
    public static final int SESSION_TRANSITION_INSTANT = 6;
    public static final int SESSION_ROW_COUNT = 7;

    private StemMixContextRows() {}

    /**
     * Track context rows for hold pad → song Options.
     * Layman: swap the track on this pad, manage queue, jump next, or soft-scrub.
     * Was: Replace Track N only. Reversal: slotRows with "Replace Track " + n.
     * 2026-07-21
     */
    public static String[] slotRows(int trackOneBased) {
        return new String[] {
                "Replace focused track",
                "Play queue",
                "Start next track",
                "Scrub",
                "TRANSITION · LONG (~4s)",
                "TRANSITION · ∞ (~8s)",
                "TRANSITION · waveform (~0.4s)",
                "TRANSITION · INSTANT"
        };
    }

    /**
     * Session / Play context — Pause interrupt + Home leave + TRANSITION.
     * Layman: stop the jam, go home, or pick blend length.
     * Was: no Pause/Home rows. Reversal: sessionRows without those two strings.
     * 2026-07-21
     */
    public static String[] sessionRows(boolean mixMode) {
        return new String[] {
                "Play queue",
                "Pause",
                "TRANSITION · LONG (~4s)",
                "TRANSITION · ∞ (~8s)",
                "TRANSITION · waveform (~0.4s)",
                "Home",
                "TRANSITION · INSTANT"
        };
    }

    public static int transitionPresetForSlotRow(int row) {
        if (row == SLOT_TRANSITION_LONG) return StemControls.TRANSITION_PRESET_LONG;
        if (row == SLOT_TRANSITION_OVERLAP) return StemControls.TRANSITION_PRESET_OVERLAP;
        if (row == SLOT_TRANSITION_WAVE) return StemControls.TRANSITION_PRESET_WAVE;
        if (row == SLOT_TRANSITION_INSTANT) return StemControls.TRANSITION_PRESET_INSTANT;
        return -1;
    }

    /** Map session row → transition preset (−1 = not a preset). 2026-07-21 */
    public static int transitionPresetForSessionRow(int row) {
        if (row == SESSION_TRANSITION_LONG) return StemControls.TRANSITION_PRESET_LONG;
        if (row == SESSION_TRANSITION_OVERLAP) return StemControls.TRANSITION_PRESET_OVERLAP;
        if (row == SESSION_TRANSITION_WAVE) return StemControls.TRANSITION_PRESET_WAVE;
        if (row == SESSION_TRANSITION_INSTANT) return StemControls.TRANSITION_PRESET_INSTANT;
        return -1;
    }

    /** True when session row pauses the jam (all decks/songs). 2026-07-21 */
    public static boolean isSessionPauseRow(int row) {
        return row == SESSION_PAUSE;
    }

    /** True when session row exits to Solar Home. 2026-07-21 */
    public static boolean isSessionHomeRow(int row) {
        return row == SESSION_HOME;
    }
}
