package com.solar.launcher.stem;

/**
 * Hallmark · component: stem-dial · genre: playful · theme: Solar ThemeManager
 *
 * Corner dial geometry for Stem mashup face complications (watch chronograph).
 * Layman: four round dials peek from the screen corners around the pads.
 * Technical: protruding centres, visible arc, 33 RPM path speed, upright glyph rot.
 * Was: StemPadArchLabel top/bottom arches only. Reversal: restore arch helpers + face draw.
 * Layout: Track A TL · Track B TR · Up Next BR · Prep chronograph BL.
 * 2026-07-21
 */
public final class StemComplicationGeometry {
    /** Vinyl-style orbit speed for circular marquees (slow enough to read on 2.4"). 2026-07-21 */
    public static final float RPM = 12f;

    /** Corner identity for dial placement. 2026-07-21 */
    public enum Corner {
        /** Track A — top-left. 2026-07-21 */
        TL,
        /** Track B — top-right. 2026-07-21 */
        TR,
        /** Up Next — bottom-right. 2026-07-21 */
        BR,
        /** Prep chronograph — bottom-left. 2026-07-21 */
        BL
    }

    private StemComplicationGeometry() {}

    /**
     * Dial radius for a face min-side (protrudes past bezel).
     * Layman: how big each corner watch dial is.
     * 2026-07-21
     */
    public static float dialRadius(float minSide) {
        float r = minSide * 0.22f;
        if (r < 42f) r = 42f;
        if (r > 78f) r = 78f;
        return r;
    }

    /**
     * Inner art bubble radius inside the dial ring.
     * Layman: the album-cover circle in the middle of the dial.
     * 2026-07-21
     */
    public static float artRadius(float dialR) {
        // Larger centre hint so art / initial reads on Y1. Was: 0.42f. Reversal: 0.42f.
        // 2026-07-21
        return dialR * 0.52f;
    }

    /**
     * How far the dial centre sits outside the visible corner (fraction of r).
     * Layman: only a slice of the dial shows on screen.
     * 2026-07-21
     */
    public static float protrudeFrac() {
        return 0.38f;
    }

    /**
     * Dial centre X/Y for a corner (centre may be off-canvas).
     * Layman: park each dial so it peeks in from that corner.
     * @return float[2] {cx, cy}
     * 2026-07-21
     */
    public static float[] dialCenter(Corner corner, float width, float height, float radius) {
        float p = radius * protrudeFrac();
        float[] out = new float[2];
        if (corner == Corner.TL) {
            out[0] = -p;
            out[1] = -p;
        } else if (corner == Corner.TR) {
            out[0] = width + p;
            out[1] = -p;
        } else if (corner == Corner.BR) {
            out[0] = width + p;
            out[1] = height + p;
        } else {
            // BL prep chronograph. 2026-07-21
            out[0] = -p;
            out[1] = height + p;
        }
        return out;
    }

    /**
     * Marquee path speed in px/s at {@link #RPM} around a circle of this radius.
     * Layman: text crawls around the dial like a 33⅓ record.
     * Technical: 2πr × (33/60).
     * 2026-07-21
     */
    public static float pathPxPerSec(float radius) {
        if (radius < 1f) return 0f;
        return (float) (2.0 * Math.PI * radius * (RPM / 60.0));
    }

    /**
     * Visible arc sweep (degrees) for the on-screen dial slice.
     * Layman: how much of the circle’s rim you can see.
     * 2026-07-21
     */
    public static float visibleSweepDeg() {
        return 118f;
    }

    /**
     * Start angle (Canvas degrees, 0=east, CW+) for the readable rim facing the pads.
     * Top dials use the lower visible slice inverted so titles read upright toward pads.
     * Layman: where the curved title begins on each corner dial.
     * Was: TL=10 TR=100 (top arcs read upside-down). Reversal: those starts.
     * 2026-07-21
     */
    public static float rimStartDeg(Corner corner) {
        // Top: start mid-right/left of the on-screen lower arc, sweep CCW via negative sign.
        if (corner == Corner.TL) return 95f;
        if (corner == Corner.TR) return 85f;
        if (corner == Corner.BR) return 190f;
        return 280f; // BL
    }

    /**
     * Sweep sign: top dials reverse so glyphs crawl right-side-up along the lower lip.
     * Layman: flip the top arches so letters aren’t upside-down.
     * Was: always +1. Reversal: return 1f always.
     * 2026-07-21
     */
    public static float rimSweepSign(Corner corner) {
        if (corner == Corner.TL || corner == Corner.TR) return -1f;
        return 1f;
    }

    /**
     * Glyph rotation so letters stay upright for the reader.
     * Layman: spin each letter so you can read it without tipping your head.
     * Technical: tangent + 90°, flip when upside-down; top dials get an extra 180° invert.
     * Was: always tangent deg+90 without top invert. Reversal: drop invertTop.
     * 2026-07-21
     */
    public static float uprightGlyphRotationDeg(float tangentDeg) {
        return uprightGlyphRotationDeg(tangentDeg, false);
    }

    /**
     * @param invertTopArch true for TL/TR — letters face the pads, not the ceiling.
     * 2026-07-21
     */
    public static float uprightGlyphRotationDeg(float tangentDeg, boolean invertTopArch) {
        float rot = tangentDeg + 90f;
        while (rot > 180f) rot -= 360f;
        while (rot < -180f) rot += 360f;
        if (rot > 90f || rot < -90f) {
            rot += 180f;
            if (rot > 180f) rot -= 360f;
        }
        if (invertTopArch) {
            rot += 180f;
            if (rot > 180f) rot -= 360f;
            if (rot < -180f) rot += 360f;
        }
        return rot;
    }

    /**
     * Arc length in px for radius + sweep.
     * 2026-07-21
     */
    public static float arcLengthPx(float radius, float sweepDeg) {
        return (float) (Math.PI * radius * Math.abs(sweepDeg) / 180.0);
    }

    /**
     * Title text size on dial rim.
     * 2026-07-21
     */
    public static float titleTextSize(float minSide) {
        // Larger rim titles for Y1 readability. Was: 0.036 / 9–13. Reversal: that scale.
        // 2026-07-21
        float s = minSide * 0.055f;
        if (s < 13f) s = 13f;
        if (s > 20f) s = 20f;
        return s;
    }

    /**
     * Prep dial label for real status only (no invented %).
     * Layman: show Ready / Waiting / the live prep marquee from the queue.
     * Technical: busy key → QueuePrepStatus.marqueeFor; else Ready/Waiting.
     * 2026-07-21
     */
    public static String prepDialLabel(String prepKey, boolean anyOverflowWaiting) {
        String m = QueuePrepStatus.marqueeFor(prepKey);
        if (m != null && m.length() > 0) return m;
        if (anyOverflowWaiting) return "Waiting";
        return "Ready";
    }

    /**
     * Prep dial fill 0..1 from busy flag only (no fake progress %).
     * Layman: ring fills while something is cooking; empty when idle.
     * Was: invent percent. Reversal: always return 0.
     * 2026-07-21
     */
    public static float prepDialFraction(boolean prepBusy) {
        return prepBusy ? 0.65f : 0f;
    }
}
