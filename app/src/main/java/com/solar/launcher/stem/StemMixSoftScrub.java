package com.solar.launcher.stem;

/**
 * Soft scrub time math — linear slider + circular pad seek cursor.
 * Layman: the scrub needle shows where you are and where you’ll land.
 * Technical: pure ms / angle helpers for overlay + Hold-OK face scrub.
 * Was: Mix hard seek on mute-hold. Reversal: MixDeck.seek only.
 * 2026-07-21 — circular frac↔angle + beat-match commit snap.
 */
public final class StemMixSoftScrub {
    /** Canvas degrees: 0 = 3 o’clock, −90 = 12 o’clock (track start). 2026-07-21 */
    public static final float ANGLE_AT_START_DEG = -90f;

    private StemMixSoftScrub() {}

    /** Clamp target ms into [0, duration]. 2026-07-21 */
    public static int clampSeekMs(int targetMs, int durationMs) {
        if (durationMs <= 0) return 0;
        if (targetMs < 0) return 0;
        // 2026-07-24 — Prevent EOF seek crash/hang on scrub to end by clamping slightly before duration
        int maxSeek = durationMs - 100;
        if (maxSeek < 0) maxSeek = 0;
        if (targetMs > maxSeek) return maxSeek;
        return targetMs;
    }

    /**
     * Wheel nudge: one notch ≈ 1% of duration (min 500ms, max 10s).
     * Layman: turn the wheel to slide the needle.
     * 2026-07-21 — reversed sign for StemFM scrollwheel convention.
     */
    public static int wheelDeltaMs(int durationMs, int wheelSteps) {
        if (wheelSteps == 0 || durationMs <= 0) return 0;
        int step = durationMs / 40;
        if (step < 500) step = 500;
        if (step > 10_000) step = 10_000;
        return -step * wheelSteps;
    }

    /** Format mm:ss (handles hours as mmm:ss). 2026-07-21 */
    public static String formatMmSs(int ms) {
        if (ms < 0) ms = 0;
        int totalSec = ms / 1000;
        int m = totalSec / 60;
        int s = totalSec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    /** Status line “cur / dur”. 2026-07-21 */
    public static String statusLine(int cursorMs, int durationMs) {
        return formatMmSs(cursorMs) + " / " + formatMmSs(durationMs);
    }

    /** Slider thumb 0..1 from cursor. 2026-07-21 */
    public static float thumbFrac(int cursorMs, int durationMs) {
        if (durationMs <= 0) return 0f;
        float f = cursorMs / (float) durationMs;
        if (f < 0f) return 0f;
        if (f > 1f) return 1f;
        return f;
    }

    /** Cursor from thumb 0..1. 2026-07-21 */
    public static int cursorFromThumb(float frac, int durationMs) {
        if (durationMs <= 0) return 0;
        if (frac < 0f) frac = 0f;
        if (frac > 1f) frac = 1f;
        return Math.round(frac * durationMs);
    }

    /**
     * Clamp scrub fraction into 0..1.
     * Layman: keep the seek dial between start and end.
     * 2026-07-21
     */
    public static float clampFrac(float frac) {
        if (frac < 0f) return 0f;
        if (frac > 1f) return 1f;
        return frac;
    }

    /**
     * Track fraction → canvas angle (degrees), start at 12 o’clock, CW = later.
     * Layman: zero time sits at the top of the bubble; turning clockwise goes later.
     * Technical: −90 + frac*360 (Android drawArc / cos/sin convention).
     * Was: linear ProgressBar only. Reversal: ignore; use thumbFrac alone.
     * 2026-07-21
     */
    public static float angleDegFromFrac(float frac) {
        return ANGLE_AT_START_DEG + clampFrac(frac) * 360f;
    }

    /**
     * Canvas angle → track fraction 0..1 (wraps into one turn).
     * Layman: where the little seek ball sits around the pad = song time.
     * Was: linear only. Reversal: return 0.
     * 2026-07-21
     */
    public static float fracFromAngleDeg(float angleDeg) {
        float rel = angleDeg - ANGLE_AT_START_DEG;
        rel = rel % 360f;
        if (rel < 0f) rel += 360f;
        return clampFrac(rel / 360f);
    }

    /**
     * XY on a circle for a scrub fraction (out-params [x,y]).
     * Layman: put the seek ball on the rim at that song time.
     * 2026-07-21
     */
    public static void cursorXy(float cx, float cy, float radius, float frac, float[] outXy) {
        if (outXy == null || outXy.length < 2) return;
        double rad = Math.toRadians(angleDegFromFrac(frac));
        outXy[0] = cx + radius * (float) Math.cos(rad);
        outXy[1] = cy + radius * (float) Math.sin(rad);
    }

    /**
     * Focus halo scale while Hold-OK scrub is armed (narrower ring).
     * Layman: the yellow ring shrinks so the seek ball has room.
     * Was: full focus halo always. Reversal: return 1f.
     * 2026-07-21
     */
    public static float scrubFocusHaloScale() {
        return 0.62f;
    }

    /**
     * Seek cursor radius as a fraction of pad radius.
     * Layman: how big the little time ball is on the bubble.
     * 2026-07-21
     */
    public static float scrubCursorRadiusFrac() {
        return 0.14f;
    }

    /**
     * Commit seek: clamp then snap to nearest beat for mashup meet-ups.
     * Layman: land on the pulse so sections can line up when you confirm.
     * Technical: StemBpm.snapToBeatMs after clamp; sibling mixers untouched by this helper.
     * Was: hard seek without snap. Reversal: return clampSeekMs only.
     * 2026-07-21
     */
    public static int beatMatchSeekMs(int cursorMs, int durationMs, float bpm) {
        int clamped = clampSeekMs(cursorMs, durationMs);
        int snapped = StemBpm.snapToBeatMs(clamped, bpm);
        return clampSeekMs(snapped, durationMs);
    }
}
