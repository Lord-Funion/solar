package com.solar.launcher;

/**
 * 2026-07-20 — Decide short vs long Back using physical key times.
 * Layman: a quick tap stays a quick tap even when the player was busy loading a song.
 * Technical: prefer KeyEvent down/event times over wall-clock; undo timer-opened Options on short UP.
 * Was: System.currentTimeMillis()-backKeyDownTime → busy main thread looked like a long hold.
 * Reversal: always use wall clock; never force-dismiss on enter player.
 */
public final class BackHoldPolicy {
    private BackHoldPolicy() {}

    /**
     * Hold duration for Back short/long classification.
     * @param eventDownMs KeyEvent.getDownTime() (0 if unknown)
     * @param eventUpMs KeyEvent.getEventTime() on UP (0 if unknown)
     * @param wallDownMs wall clock when KEY_DOWN was handled
     * @param wallNowMs wall clock now
     */
    public static long physicalHoldMs(long eventDownMs, long eventUpMs,
            long wallDownMs, long wallNowMs) {
        if (eventDownMs > 0L && eventUpMs >= eventDownMs) {
            return eventUpMs - eventDownMs;
        }
        if (wallDownMs <= 0L) return 0L;
        long w = wallNowMs - wallDownMs;
        return w > 0L ? w : 0L;
    }

    /**
     * Timer opened Options while KEY_UP was still queued; physical tap was short.
     * Layman: Options popped by accident — treat this Back as “go back” instead.
     */
    public static boolean shouldUndoSpuriousContextOpen(boolean menuShowing,
            boolean longPressHandled, long physicalHoldMs, long longPressMs) {
        return menuShowing && longPressHandled && physicalHoldMs < longPressMs;
    }

    /**
     * Entering Now Playing must drop any leftover Options sheet (e.g. Play Instrumental).
     * Layman: song screen should not keep the library popup open behind it.
     */
    public static boolean shouldForceDismissContextOnEnterPlayer(boolean menuShowing) {
        return menuShowing;
    }
}
