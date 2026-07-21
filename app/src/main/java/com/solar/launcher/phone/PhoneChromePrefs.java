package com.solar.launcher.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 2026-07-20 — Body/wheel colour + texture URIs + optional storage folder for phone chrome.
 * Defaults to solid colours (cheap on low RAM); photos are subsampled ≤512px and copied in-app.
 * Was: no chrome prefs. Reversal: clear prefs / delete filesDir/phone_chrome — stock look returns.
 */
public final class PhoneChromePrefs {

    private static final String PREFS = "solar_phone_chrome";
    private static final String KEY_BODY_COLOR = "body_color";
    private static final String KEY_WHEEL_COLOR = "wheel_color";
    private static final String KEY_BODY_TEXTURE = "body_texture_file";
    private static final String KEY_WHEEL_TEXTURE = "wheel_texture_file";
    private static final String KEY_STORAGE_ROOT = "storage_root";
    /** 2026-07-20 — SAF tree URI kept for later; File I/O still uses storage_root. */
    private static final String KEY_STORAGE_TREE_URI = "storage_tree_uri";
    private static final String KEY_FLIP_OPEN = "customize_open";

    /** Classic white body. */
    public static final int DEFAULT_BODY = 0xFFE8E8E8;
    /** Dark ring. */
    public static final int DEFAULT_WHEEL = 0xFF2A2A2A;

    /** Curated body+wheel pairs for the customize strip. */
    public static final int[][] CURATED_PAIRS = new int[][] {
            { 0xFFE8E8E8, 0xFF2A2A2A }, // white / black
            { 0xFF1A1A1A, 0xFFCCCCCC }, // black / silver
            { 0xFFB8C4CE, 0xFF3A3A3A }, // blue-grey
            { 0xFFF5D0C8, 0xFF4A3030 }, // blush
            { 0xFFC8E6C9, 0xFF2E4A32 }, // mint
    };

    private static final int MAX_TEXTURE_EDGE = 512;

    private PhoneChromePrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Body panel ARGB colour. */
    public static int bodyColor(Context ctx) {
        return prefs(ctx).getInt(KEY_BODY_COLOR, DEFAULT_BODY);
    }

    /** Wheel ring ARGB colour. */
    public static int wheelColor(Context ctx) {
        return prefs(ctx).getInt(KEY_WHEEL_COLOR, DEFAULT_WHEEL);
    }

    /** Persist a curated or custom colour pair. */
    public static void setColors(Context ctx, int bodyArgb, int wheelArgb) {
        prefs(ctx).edit()
                .putInt(KEY_BODY_COLOR, bodyArgb)
                .putInt(KEY_WHEEL_COLOR, wheelArgb)
                .apply();
    }

    /** Absolute path of copied body texture, or null. */
    public static String bodyTexturePath(Context ctx) {
        String p = prefs(ctx).getString(KEY_BODY_TEXTURE, null);
        return (p != null && p.length() > 0) ? p : null;
    }

    /** Absolute path of copied wheel texture, or null. */
    public static String wheelTexturePath(Context ctx) {
        String p = prefs(ctx).getString(KEY_WHEEL_TEXTURE, null);
        return (p != null && p.length() > 0) ? p : null;
    }

    /** Clear body photo — fall back to solid colour. */
    public static void clearBodyTexture(Context ctx) {
        deleteQuiet(bodyTexturePath(ctx));
        prefs(ctx).edit().remove(KEY_BODY_TEXTURE).apply();
    }

    /** Clear wheel photo. */
    public static void clearWheelTexture(Context ctx) {
        deleteQuiet(wheelTexturePath(ctx));
        prefs(ctx).edit().remove(KEY_WHEEL_TEXTURE).apply();
    }

    /**
     * 2026-07-20 — Copy + subsample an image into app files for body or wheel.
     * Drops large decode buffers after write. Returns local file path or null on failure.
     */
    public static String importTexture(Context ctx, Uri uri, boolean forBody) {
        if (ctx == null || uri == null) return null;
        InputStream in = null;
        FileOutputStream out = null;
        Bitmap bmp = null;
        try {
            in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            try { in.close(); } catch (Throwable ignored) {}
            in = ctx.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_TEXTURE_EDGE);
            bmp = BitmapFactory.decodeStream(in, null, opts);
            if (bmp == null) return null;
            File dir = new File(ctx.getFilesDir(), "phone_chrome");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File dest = new File(dir, forBody ? "body.jpg" : "wheel.jpg");
            out = new FileOutputStream(dest);
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            out.flush();
            String path = dest.getAbsolutePath();
            prefs(ctx).edit()
                    .putString(forBody ? KEY_BODY_TEXTURE : KEY_WHEEL_TEXTURE, path)
                    .apply();
            return path;
        } catch (Throwable t) {
            return null;
        } finally {
            if (bmp != null) {
                try { bmp.recycle(); } catch (Throwable ignored) {}
            }
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
        }
    }

    /** Decode a local texture path with a second safety subsample; caller recycles. */
    public static Bitmap loadTextureBitmap(String path) {
        if (path == null || path.length() == 0) return null;
        File f = new File(path);
        if (!f.isFile()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_TEXTURE_EDGE);
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 2026-07-20 — Power-of-two inSampleSize so the long edge ≤ maxEdge.
     * Pure math for tests without Bitmap.
     */
    public static int sampleSizeFor(int width, int height, int maxEdge) {
        if (maxEdge <= 0) return 1;
        int longEdge = Math.max(width, height);
        if (longEdge <= maxEdge) return 1;
        int sample = 1;
        while (longEdge / (sample * 2) >= maxEdge) {
            sample *= 2;
        }
        // Need at least one more step if still over
        if (longEdge / sample > maxEdge) sample *= 2;
        return Math.max(1, sample);
    }

    /** User-chosen storage parent folder, or null. */
    public static String storageRootPath(Context ctx) {
        String p = prefs(ctx).getString(KEY_STORAGE_ROOT, null);
        return (p != null && p.length() > 0) ? p : null;
    }

    /** Persist storage parent; PhoneStorageRoots creates Internal/ + MicroSD/ under it. */
    public static void setStorageRootPath(Context ctx, String absolutePath) {
        if (absolutePath == null || absolutePath.length() == 0) {
            // 2026-07-20 — Clearing folder also drops saved SAF URI.
            prefs(ctx).edit().remove(KEY_STORAGE_ROOT).remove(KEY_STORAGE_TREE_URI).apply();
            return;
        }
        prefs(ctx).edit().putString(KEY_STORAGE_ROOT, absolutePath).apply();
    }

    /**
     * 2026-07-20 — Persist SAF tree URI string for future DocumentFile use (not required yet).
     * Clear with null. Reversal: remove key — File ladder still works from storage_root.
     */
    public static void setStorageTreeUri(Context ctx, String uriString) {
        if (uriString == null || uriString.length() == 0) {
            prefs(ctx).edit().remove(KEY_STORAGE_TREE_URI).apply();
            return;
        }
        prefs(ctx).edit().putString(KEY_STORAGE_TREE_URI, uriString).apply();
    }

    /** Last SAF tree URI, or null. */
    public static String storageTreeUri(Context ctx) {
        String p = prefs(ctx).getString(KEY_STORAGE_TREE_URI, null);
        return (p != null && p.length() > 0) ? p : null;
    }

    /** Remember whether the customize flip face is showing (session hint only). */
    public static void setCustomizeOpen(Context ctx, boolean open) {
        prefs(ctx).edit().putBoolean(KEY_FLIP_OPEN, open).apply();
    }

    public static boolean isCustomizeOpen(Context ctx) {
        return prefs(ctx).getBoolean(KEY_FLIP_OPEN, false);
    }

    private static void deleteQuiet(String path) {
        if (path == null) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        } catch (Throwable ignored) {}
    }
}
