package com.solar.launcher.phone;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.solar.launcher.DeviceFeatures;

/**
 * 2026-07-20 — Decides when Solar wraps itself in the phone click-wheel shell.
 * Never on real Y1/Y2 (~480×360) or A5 (~240×320) panels, nor when a family pin says so.
 * Was: phones used full-bleed Solar with no wheel. Now: ClassiPod-style chrome on other sizes.
 * Reversal: make {@link #active(int, int, String)} always false — prior full-screen layout returns.
 */
public final class PhoneChromePolicy {

    /** Design Solar panel (mdpi px) — 4:3 landscape inside the shell. */
    public static final int SOLAR_W = 480;
    public static final int SOLAR_H = 360;

    /** Width band for W480 phones (480×800 / 854 / 720 era skins). */
    private static final int W480_MIN = 460;
    private static final int W480_MAX = 500;

    private PhoneChromePolicy() {}

    /**
     * 2026-07-20 — True when this process should host PhoneChromeHost.
     * Reads live display + optional persist.solar.device_family pin.
     */
    public static boolean active(Context ctx) {
        if (ctx == null) return false;
        int[] px = readDisplayPx(ctx);
        String pin = DeviceFeatures.readDeviceFamilyPinForPhoneChrome();
        return active(px[0], px[1], pin);
    }

    /**
     * 2026-07-20 — Pure gate for tests: display size + family pin only (no SoC).
     * Y1/Y2/A5 panels and y1|y2|a5 pins stay native; everything else gets chrome.
     */
    public static boolean active(int widthPx, int heightPx, String familyPin) {
        if (widthPx <= 0 || heightPx <= 0) return false;
        // Hardware-sized panels never wear the phone shell.
        if (DeviceFeatures.looksLikeY1Display(widthPx, heightPx)) return false;
        if (DeviceFeatures.looksLikeA5Display(widthPx, heightPx)) return false;
        // Emulator / lab pin forces native even on a tall skin.
        String pin = familyPin != null ? familyPin.trim().toLowerCase() : "";
        if ("y1".equals(pin) || "y2".equals(pin) || "a5".equals(pin)) return false;
        return true;
    }

    /** Convenience overload — empty pin. */
    public static boolean active(int widthPx, int heightPx) {
        return active(widthPx, heightPx, "");
    }

    /**
     * 2026-07-20 — ~480-wide portrait phones: Solar at 1:1 width, hard cut to body.
     * Uses the shorter side as width when the phone is portrait.
     */
    public static boolean isW480(int widthPx, int heightPx) {
        if (widthPx <= 0 || heightPx <= 0) return false;
        int shortSide = Math.min(widthPx, heightPx);
        return shortSide >= W480_MIN && shortSide <= W480_MAX;
    }

    /** Live display W480 check. */
    public static boolean isW480(Context ctx) {
        int[] px = readDisplayPx(ctx);
        return isW480(px[0], px[1]);
    }

    /**
     * 2026-07-20 — Layout numbers for the Solar viewport inside the chrome.
     * W480: full short-side width × 3/4 height of that width (480×360 at mdpi).
     * Other: scale 4:3 to fit width and leave room for the wheel body.
     */
    public static LayoutMetrics layoutMetrics(int screenW, int screenH, boolean w480) {
        LayoutMetrics m = new LayoutMetrics();
        if (screenW <= 0 || screenH <= 0) {
            m.viewportW = SOLAR_W;
            m.viewportH = SOLAR_H;
            m.bodyTop = SOLAR_H;
            m.scale = 1f;
            m.w480 = w480;
            return m;
        }
        m.w480 = w480;
        // Portrait stack assumes tall screen; swap if landscape phone.
        int w = screenW;
        int h = screenH;
        if (w > h) {
            // Rare: landscape phone — still stack using short side as width.
            w = screenH;
            h = screenW;
        }
        if (w480) {
            // 1:1 width Solar, hard cut — wheel fills remainder.
            m.viewportW = w;
            m.viewportH = Math.round(w * (SOLAR_H / (float) SOLAR_W));
            m.scale = m.viewportW / (float) SOLAR_W;
            m.offsetX = 0;
            m.offsetY = 0;
            m.bodyTop = m.viewportH;
            m.bodyFill = false;
        } else {
            // Leave ~45% of height for body+wheel; fit 4:3 in the top band.
            int topBand = Math.max(SOLAR_H / 2, (int) (h * 0.55f));
            float scaleW = w / (float) SOLAR_W;
            float scaleH = topBand / (float) SOLAR_H;
            float scale = Math.min(scaleW, scaleH);
            m.scale = scale;
            m.viewportW = Math.round(SOLAR_W * scale);
            m.viewportH = Math.round(SOLAR_H * scale);
            m.offsetX = (w - m.viewportW) / 2;
            m.offsetY = Math.max(0, (topBand - m.viewportH) / 2);
            m.bodyTop = topBand;
            m.bodyFill = true; // body colour fills around the scaled viewport
        }
        m.screenW = w;
        m.screenH = h;
        return m;
    }

    /** Read width/height in px; [0,0] if unavailable. */
    static int[] readDisplayPx(Context ctx) {
        int[] out = new int[] { 0, 0 };
        if (ctx == null) return out;
        try {
            WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                out[0] = dm.widthPixels;
                out[1] = dm.heightPixels;
                return out;
            }
        } catch (Throwable ignored) {}
        try {
            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            out[0] = dm.widthPixels;
            out[1] = dm.heightPixels;
        } catch (Throwable ignored) {}
        return out;
    }

    /** Viewport + body geometry produced by {@link #layoutMetrics}. */
    public static final class LayoutMetrics {
        public int screenW;
        public int screenH;
        public int viewportW;
        public int viewportH;
        public int offsetX;
        public int offsetY;
        public int bodyTop;
        public float scale;
        public boolean w480;
        /** When true, body colour/texture paints the margin around the Solar viewport. */
        public boolean bodyFill;
    }
}
