package com.solar.launcher.phone;

/**
 * 2026-07-20 — Ideal body-texture pixel size for the simulated hardware band.
 * Layman: how big a photo should be to cover the plastic under the screen.
 * Tech: body band = full width × (screenH − bodyTop); bordered still uses that band for the wheel face.
 * Y1/Y2/A5 never call this (no chrome). Reversal: delete — customize loses size hint only.
 */
public final class PhoneSkinSize {

    private PhoneSkinSize() {}

    /**
     * 2026-07-20 — Pixel size for a body photo that fills the visible hardware shell.
     * W480 hard-cut: band under Solar only. Bordered: full screen (margins + wheel).
     * Returns [width, height]; zeros if metrics missing.
     */
    public static int[] idealBodySkinPx(PhoneChromePolicy.LayoutMetrics m) {
        if (m == null || m.screenW <= 0 || m.screenH <= 0) {
            return new int[] { 0, 0 };
        }
        if (m.bodyFill) {
            // Bordered phones: body colour/texture wraps the scaled Solar panel.
            return new int[] { m.screenW, m.screenH };
        }
        int bandH = Math.max(0, m.screenH - m.bodyTop);
        return new int[] { m.screenW, bandH };
    }

    /**
     * 2026-07-20 — Human line for customize / onboarding (“Ideal skin: 480×440 px”).
     * Empty string when unknown so the UI can hide the row.
     */
    public static String idealBodySkinHint(PhoneChromePolicy.LayoutMetrics m) {
        int[] px = idealBodySkinPx(m);
        if (px[0] <= 0 || px[1] <= 0) return "";
        return "Ideal body skin: " + px[0] + "×" + px[1] + " px (scaled to fill)";
    }

    /**
     * 2026-07-20 — Same hint from live display (chrome active phones only).
     */
    public static String idealBodySkinHint(android.content.Context ctx) {
        if (ctx == null || !PhoneChromePolicy.active(ctx)) return "";
        int[] screen = PhoneChromePolicy.readDisplayPx(ctx);
        boolean w480 = PhoneChromePolicy.isW480(screen[0], screen[1]);
        PhoneChromePolicy.LayoutMetrics m =
                PhoneChromePolicy.layoutMetrics(screen[0], screen[1], w480);
        return idealBodySkinHint(m);
    }
}
