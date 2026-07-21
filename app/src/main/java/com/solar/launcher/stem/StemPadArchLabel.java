package com.solar.launcher.stem;

/**
 * Geometry for Stem mashup track labels along an arch around the pad cluster.
 * Layman: curve song names around the bubbles so they don’t sit on top of them.
 * Technical: arc radius / sweep helpers for canvas char-by-char draw.
 * Was: straight corner strips (maxW≈0.52w) overlapping pads. Reversal: corner x/y layout.
 * 2026-07-21
 */
public final class StemPadArchLabel {
    private StemPadArchLabel() {}

    /**
     * Outer radius for song title arches — clear of pad reach.
     * Layman: how far out from centre the curved name sits.
     * 2026-07-21
     */
    public static float archRadius(float minSide, float padReach) {
        // Sit outside the loudest pad reach with calm margin on 480×360. 2026-07-21
        float r = padReach * 1.72f;
        float cap = minSide * 0.46f;
        if (r > cap) r = cap;
        float floor = minSide * 0.34f;
        if (r < floor) r = floor;
        return r;
    }

    /**
     * Sweep degrees for one song title arch (readable without wrapping into pads).
     * Layman: how wide the curved name band is.
     * 2026-07-21
     */
    public static float archSweepDeg() {
        return 118f;
    }

    /**
     * Top arch start angle (degrees, Canvas: 0=east, CW positive) for Track 1.
     * Layman: Track 1 curves over the top of the pads.
     * 2026-07-21
     */
    public static float topArchStartDeg() {
        // ~201° → sweep 118° ends ~319° (upper half). 2026-07-21
        return 201f;
    }

    /**
     * Bottom arch start for Track 2 (lower curve under Melody).
     * Layman: Track 2 curves under the pads.
     * 2026-07-21
     */
    public static float bottomArchStartDeg() {
        // ~21° → sweep under the diamond. 2026-07-21
        return 21f;
    }

    /** Arc length in px for a radius + sweep. 2026-07-21 */
    public static float arcLengthPx(float radius, float sweepDeg) {
        return (float) (Math.PI * radius * Math.abs(sweepDeg) / 180.0);
    }

    /**
     * Title text size for arch labels on a given face.
     * Layman: big enough to read, small enough to stay off the pads.
     * 2026-07-21
     */
    public static float titleTextSize(float minSide) {
        float s = minSide * 0.042f;
        if (s < 10f) s = 10f;
        if (s > 15f) s = 15f;
        return s;
    }

    /**
     * Artist secondary size under / along the same arch.
     * 2026-07-21
     */
    public static float artistTextSize(float titleSize) {
        return titleSize * 0.82f;
    }
}
