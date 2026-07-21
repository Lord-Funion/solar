package com.solar.launcher.phone;

import org.junit.Test;

/**
 * 2026-07-20 — Ideal body skin size for phone chrome textures.
 */
public class PhoneSkinSizeTest {

    @Test
    public void w480IdealIsBodyBandOnly() {
        PhoneChromePolicy.LayoutMetrics m =
                PhoneChromePolicy.layoutMetrics(480, 800, true);
        int[] px = PhoneSkinSize.idealBodySkinPx(m);
        if (px[0] != 480) throw new AssertionError("w=" + px[0]);
        // Solar viewport 360 → band 440
        if (px[1] != 440) throw new AssertionError("h=" + px[1]);
        String hint = PhoneSkinSize.idealBodySkinHint(m);
        if (hint.indexOf("480×440") < 0) {
            throw new AssertionError("hint=" + hint);
        }
    }

    @Test
    public void borderedIdealIsFullScreen() {
        PhoneChromePolicy.LayoutMetrics m =
                PhoneChromePolicy.layoutMetrics(720, 1280, false);
        if (!m.bodyFill) throw new AssertionError("expected bodyFill");
        int[] px = PhoneSkinSize.idealBodySkinPx(m);
        if (px[0] != 720) throw new AssertionError("w=" + px[0]);
        if (px[1] != 1280) throw new AssertionError("h=" + px[1]);
    }

    @Test
    public void nullMetricsSafe() {
        int[] px = PhoneSkinSize.idealBodySkinPx(null);
        if (px[0] != 0 || px[1] != 0) throw new AssertionError("expected zeros");
        if (PhoneSkinSize.idealBodySkinHint((PhoneChromePolicy.LayoutMetrics) null).length() != 0) {
            throw new AssertionError("expected empty hint");
        }
    }
}
