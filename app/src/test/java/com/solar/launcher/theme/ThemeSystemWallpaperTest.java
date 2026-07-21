package com.solar.launcher.theme;

import org.junit.Test;

/**
 * ThemeSystemWallpaper policy — Bitmap APIs need Robolectric; keep JVM-safe checks here.
 * 2026-07-19
 */
public class ThemeSystemWallpaperTest {

    @Test
    public void scaleNullSafe() {
        if (ThemeSystemWallpaper.scaleForSystemWallpaper(null) != null) {
            throw new AssertionError("null in → null out");
        }
    }

    @Test
    public void clearTokenDoesNotThrow() {
        ThemeSystemWallpaper.clearAppliedToken();
    }
}
