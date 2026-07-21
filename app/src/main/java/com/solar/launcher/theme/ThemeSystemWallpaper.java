package com.solar.launcher.theme;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-07-19 — Push theme {@code globalWallpaper} into Android’s system wallpaper.
 * Layman: stock USB storage screen uses the same backdrop as your Solar theme.
 * Tech: {@link WallpaperManager#setBitmap} off the UI thread; scaled copy only; never recycle
 * ThemeManager cache bitmaps. Fail-open on any error (no crash).
 * Reversal: stop calling {@link #syncAsync}; clear wallpaper via Settings if needed.
 */
public final class ThemeSystemWallpaper {

    private static final String TAG = "ThemeSysWallpaper";
    /** Y1/Y2 panel — keep setBitmap payload small on MT6572 heap. */
    private static final int MAX_SIDE_PX = 480;
    private static final AtomicBoolean sInFlight = new AtomicBoolean(false);
    private static volatile String sLastToken = "";

    private ThemeSystemWallpaper() {}

    /**
     * Schedule a background sync when the active theme’s global (or desktop) wallpaper changes.
     * Safe to call from UI; no-ops while a sync is already running.
     */
    public static void syncAsync(Context context) {
        if (context == null) return;
        final Context app = context.getApplicationContext();
        if (!sInFlight.compareAndSet(false, true)) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    syncNow(app);
                } catch (Throwable t) {
                    Log.w(TAG, "syncAsync: " + t.getClass().getSimpleName());
                } finally {
                    sInFlight.set(false);
                }
            }
        }, "ThemeSysWallpaper").start();
    }

    /**
     * Apply theme wallpaper to the system if the theme token changed.
     * Never throws; never touches ThemeManager’s cached bitmaps after scale.
     */
    static void syncNow(Context app) {
        if (app == null) return;
        ThemeManager.ThemeEntry entry = ThemeManager.getCurrentTheme();
        if (entry == null || entry.root == null) return;

        String globalPath = entry.root.optString("globalWallpaper", "").trim();
        String deskPath = entry.root.optString("desktopWallpaper", "").trim();
        String assetPath = !globalPath.isEmpty() ? globalPath : deskPath;
        if (assetPath.isEmpty()) return;

        String token = entry.folderName + "\0" + assetPath;
        if (token.equals(sLastToken)) return;

        Bitmap src = null;
        try {
            // Prefer globalWallpaper — matches non-home screens / SystemUI backdrop.
            src = ThemeManager.getWallpaper(entry, false);
            if (src == null || src.isRecycled()) {
                src = ThemeManager.getWallpaper(entry, true);
            }
            if (src == null || src.isRecycled()) return;

            Bitmap scaled = scaleForSystemWallpaper(src);
            if (scaled == null || scaled.isRecycled()) return;

            try {
                WallpaperManager wm = WallpaperManager.getInstance(app);
                if (wm == null) return;
                wm.setBitmap(scaled);
                sLastToken = token;
                Log.i(TAG, "system wallpaper set theme=" + entry.folderName);
            } finally {
                // Only recycle our scaled copy — never the ThemeManager cache bitmap.
                if (scaled != src && !scaled.isRecycled()) {
                    try {
                        scaled.recycle();
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "syncNow failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * Downscale for WallpaperManager — avoids OOM on large theme assets.
     * Returns a new bitmap (caller recycles) or null.
     */
    static Bitmap scaleForSystemWallpaper(Bitmap src) {
        if (src == null || src.isRecycled()) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        int maxSide = Math.max(w, h);
        if (maxSide <= MAX_SIDE_PX) {
            try {
                // Mutable copy so WallpaperManager never holds ThemeManager’s cache.
                return src.copy(Bitmap.Config.ARGB_8888, false);
            } catch (Throwable t) {
                return null;
            }
        }
        float scale = (float) MAX_SIDE_PX / (float) maxSide;
        try {
            Matrix m = new Matrix();
            m.setScale(scale, scale);
            return Bitmap.createBitmap(src, 0, 0, w, h, m, true);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Test / theme-switch hook — clear dedupe so next sync re-applies. */
    public static void clearAppliedToken() {
        sLastToken = "";
    }
}
