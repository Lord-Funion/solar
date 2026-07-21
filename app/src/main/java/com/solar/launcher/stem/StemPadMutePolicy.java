package com.solar.launcher.stem;

/**
 * Stem pad mute hygiene — gain 0 is silence only, never seek/restart.
 * Layman: turn a pad down and up and the song is still where it was.
 * Technical: volume/gain path only; no seekTo / re-prepare / loop re-arm on mute cycle.
 * Was: pause + seekTo(getPositionMs) on unmute (could jump). Reversal: restore seek/pause path.
 * 2026-07-21
 */
public final class StemPadMutePolicy {

    private StemPadMutePolicy() {}

    /**
     * Mute must not pause the player — setVolume(0) while timeline keeps running.
     * 2026-07-21
     */
    public static boolean shouldPauseWhenSilent() {
        return false;
    }

    /**
     * Unmute must not seek — raise volume only; keep the same playhead.
     * 2026-07-21
     */
    public static boolean shouldSeekOnUnmute() {
        return false;
    }

    /**
     * Unmute must not re-start from zero / re-prepare.
     * 2026-07-21
     */
    public static boolean shouldRestartOnUnmute() {
        return false;
    }

    /**
     * Position after mute→unmute cycle (policy check for unit tests).
     * Layman: if we never seeked, the clock stays put.
     * 2026-07-21
     */
    public static int positionAfterMuteCycle(int positionBeforeMs, boolean didSeek, boolean didRestart) {
        if (didSeek || didRestart) return -1;
        return positionBeforeMs < 0 ? 0 : positionBeforeMs;
    }
}
