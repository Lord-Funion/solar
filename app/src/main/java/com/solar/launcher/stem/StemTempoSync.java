package com.solar.launcher.stem;

/**
 * Song-to-song tempo match helpers — Song 1 is master.
 * Layman: speed other songs so they sit on Song 1’s pulse without chipmunking.
 * Technical: StemBpm.rateToMatch → IJK SoundTouch when |rate-1| > epsilon; else 1.0 MediaPlayer.
 * Was: no cross-song rate. Reversal: always leave rate=1 and rely on seek drift only.
 * 2026-07-19
 */
public final class StemTempoSync {
    /** Ignore tiny BPM estimate noise. */
    public static final float RATE_EPSILON = 0.02f;

    private StemTempoSync() {}

    /**
     * Rate for songIndex to match master (song 0). Song 0 always 1.0.
     * 2026-07-19
     */
    public static float rateForSong(float masterBpm, float songBpm, int songIndex) {
        if (songIndex <= 0) return 1f;
        float r = StemBpm.rateToMatch(masterBpm, songBpm);
        if (Math.abs(r - 1f) < RATE_EPSILON) return 1f;
        return r;
    }

    /** True when this song needs pitch-preserving stretch (IJK SoundTouch). */
    public static boolean needsSoundTouch(float rate) {
        return Math.abs(rate - 1f) >= RATE_EPSILON;
    }

    /**
     * Combine song tempo bus with pad screw (classic Houston feel on top of match).
     * Layman: slow the pad while the song still tries to sit on Song 1’s pulse.
     * Technical: tempoRate * screwRate; clamp to SCREW floor / MAX_RATE.
     * Was: hold screw replaced tempoRate. Reversal: return screw only.
     * 2026-07-20
     */
    public static float composePadRate(float tempoRate, float screwRate) {
        float t = tempoRate > 0.1f ? tempoRate : 1f;
        float s = screwRate > 0.1f ? screwRate : 1f;
        float r = t * s;
        if (r < 0.5f) return 0.5f;
        if (r > StemBpm.MAX_RATE) return StemBpm.MAX_RATE;
        return r;
    }

    /**
     * Expected media position for a slave song given Song 1 lead position + tempo rate.
     * Layman: where song 2/3 should be if they started together at matched speed.
     * Technical: leadPosMs * tempoRate (IJK setSpeed advances media clock by rate).
     * Was: compare raw getPositionMs across songs. Reversal: return leadPosMs.
     * 2026-07-20
     */
    public static int expectedSlavePosMs(int leadPosMs, float tempoRate) {
        int lead = Math.max(0, leadPosMs);
        float r = tempoRate > 0.1f ? tempoRate : 1f;
        return Math.max(0, Math.round(lead * r));
    }
}
