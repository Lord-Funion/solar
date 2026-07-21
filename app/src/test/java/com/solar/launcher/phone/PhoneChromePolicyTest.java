package com.solar.launcher.phone;

import org.junit.Test;

/**
 * 2026-07-20 — Phone chrome gate + W480 / bordered layout metrics.
 */
public class PhoneChromePolicyTest {

    @Test
    public void y1DisplayNeverActive() {
        if (PhoneChromePolicy.active(480, 360, "")) {
            throw new AssertionError("Y1 landscape must not get chrome");
        }
        if (PhoneChromePolicy.active(360, 480, "")) {
            throw new AssertionError("Y1 portrait must not get chrome");
        }
    }

    @Test
    public void a5DisplayNeverActive() {
        if (PhoneChromePolicy.active(240, 320, "")) {
            throw new AssertionError("A5 must not get chrome");
        }
    }

    @Test
    public void familyPinBlocksChromeOnPhoneSkin() {
        if (PhoneChromePolicy.active(480, 800, "y1")) {
            throw new AssertionError("y1 pin must block chrome");
        }
        if (PhoneChromePolicy.active(480, 800, "y2")) {
            throw new AssertionError("y2 pin must block chrome");
        }
        if (PhoneChromePolicy.active(720, 1280, "a5")) {
            throw new AssertionError("a5 pin must block chrome");
        }
    }

    @Test
    public void phoneSkinsActivateWithoutPin() {
        if (!PhoneChromePolicy.active(480, 800, "")) {
            throw new AssertionError("480x800 should activate");
        }
        if (!PhoneChromePolicy.active(480, 854, "")) {
            throw new AssertionError("480x854 should activate");
        }
        if (!PhoneChromePolicy.active(720, 1280, "")) {
            throw new AssertionError("720x1280 should activate");
        }
        if (!PhoneChromePolicy.active(320, 480, "")) {
            throw new AssertionError("HVGA should activate");
        }
        if (!PhoneChromePolicy.active(1080, 1920, "")) {
            throw new AssertionError("1080p should activate");
        }
    }

    @Test
    public void w480Detection() {
        if (!PhoneChromePolicy.isW480(480, 800)) {
            throw new AssertionError("480x800 is W480");
        }
        if (!PhoneChromePolicy.isW480(480, 854)) {
            throw new AssertionError("480x854 is W480");
        }
        if (PhoneChromePolicy.isW480(720, 1280)) {
            throw new AssertionError("720p is not W480");
        }
        if (PhoneChromePolicy.isW480(320, 480)) {
            throw new AssertionError("HVGA is not W480");
        }
    }

    @Test
    public void w480LayoutIsFullWidthHardCut() {
        PhoneChromePolicy.LayoutMetrics m =
                PhoneChromePolicy.layoutMetrics(480, 800, true);
        if (m.viewportW != 480) throw new AssertionError("viewportW=" + m.viewportW);
        if (m.viewportH != 360) throw new AssertionError("viewportH=" + m.viewportH);
        if (m.bodyTop != 360) throw new AssertionError("bodyTop=" + m.bodyTop);
        if (m.bodyFill) throw new AssertionError("W480 must not body-fill");
        if (m.offsetX != 0 || m.offsetY != 0) throw new AssertionError("offsets must be 0");
    }

    @Test
    public void borderedLayoutScalesAndFills() {
        PhoneChromePolicy.LayoutMetrics m =
                PhoneChromePolicy.layoutMetrics(720, 1280, false);
        if (!m.bodyFill) throw new AssertionError("bordered needs bodyFill");
        if (m.viewportW > 720) throw new AssertionError("viewport wider than screen");
        if (m.viewportH > m.bodyTop) throw new AssertionError("viewport taller than top band");
        float aspect = m.viewportW / (float) m.viewportH;
        if (Math.abs(aspect - (480f / 360f)) > 0.02f) {
            throw new AssertionError("aspect=" + aspect);
        }
    }
}
