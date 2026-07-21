package com.solar.launcher.youtube;

import android.content.Context;
import android.util.Log;

import com.solar.launcher.net.SolarHttp;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 2026-07-16 — Cache progressive YouTube MP4 for reliable Y1 playback.
 * Layman: download the stream to a file, then play it like a normal video.
 * Tech: MUST use SolarHttp (OkHttp + TlsHelper). Raw HttpURLConnection fails TLS on API 17
 * for googlevideo / ytapi HTTPS, so cache never completed and play always failed.
 * 2026-07-20 — Pause new cache growth under MemoryRelease / LowMemoryGate pressure (reuse OK).
 * Reversal: stream-only openUrl without cache; drop growthPaused.
 */
public final class YouTubeProgressiveCache {
    private static final String TAG = "SolarYouTubeCache";
    private static final long MAX_CACHE_BYTES = 120L * 1024L * 1024L;
    private static final long MIN_USABLE_BYTES = 256L * 1024L;
    /** 2026-07-20 — When true, skip new downloads (reuse existing cache files). */
    private static volatile boolean growthPaused;

    public interface Progress {
        void onProgress(int percent, long done, long total);
    }

    private YouTubeProgressiveCache() {}

    /**
     * 2026-07-20 — MemoryRelease / pressure: stop growing the YT cache on disk.
     * Layman: don’t fill storage with new video downloads while RAM is unhappy.
     * Reversal: always allow download.
     */
    public static void setGrowthPaused(boolean paused) {
        growthPaused = paused;
    }

    /**
     * 2026-07-20 — Alias for {@link #setGrowthPaused} (MemoryRelease naming).
     * Layman: same pause switch under a longer name the release ladder uses.
     */
    public static void setPauseOpportunisticGrowth(boolean paused) {
        setGrowthPaused(paused);
    }

    /** True when new progressive downloads should not start. */
    public static boolean isGrowthPaused() {
        return growthPaused;
    }

    /** 2026-07-20 — Alias for {@link #isGrowthPaused} (MemoryRelease self-check). */
    public static boolean isOpportunisticGrowthPaused() {
        return isGrowthPaused();
    }

    public static File cacheDir(Context ctx) {
        File d = new File(ctx.getApplicationContext().getCacheDir(), "yt_prog");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    public static File cacheFile(Context ctx, String videoId, String quality) {
        String id = videoId != null ? videoId.replaceAll("[^A-Za-z0-9_-]", "") : "x";
        String q = quality != null ? quality.replaceAll("[^0-9]", "") : "360";
        return new File(cacheDir(ctx), id + "_" + q + ".mp4");
    }

    public static boolean isUsable(File f) {
        return f != null && f.isFile() && f.length() >= MIN_USABLE_BYTES;
    }

    /**
     * Download progressive URL via SolarHttp into cache. Follows redirects + modern TLS.
     */
    public static File download(Context ctx, String url, String videoId, String quality,
            AtomicBoolean cancel, Progress progress) throws Exception {
        if (ctx == null || url == null || url.length() == 0) {
            throw new IllegalArgumentException("no url");
        }
        File out = cacheFile(ctx, videoId, quality);
        if (isUsable(out) && out.length() > 1024L * 1024L) {
            if (progress != null) progress.onProgress(100, out.length(), out.length());
            Log.i(TAG, "reuse cache " + out.getName() + " bytes=" + out.length());
            return out;
        }
        // 2026-07-20 — Under pressure: reuse only; do not grow disk cache. Reversal: remove gate.
        boolean pressured = false;
        try {
            pressured = com.solar.launcher.LowMemoryGate.isPressured(ctx);
        } catch (Throwable ignored) {}
        if (growthPaused || pressured) {
            throw new java.io.IOException("cache growth paused under memory pressure");
        }
        File tmp = new File(out.getAbsolutePath() + ".part");
        if (tmp.exists() && tmp.length() < MIN_USABLE_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }

        Log.i(TAG, "download start " + (url.length() > 100 ? url.substring(0, 100) : url)
                + " → " + out.getName());
        SolarHttp.downloadToFile(url, tmp, new SolarHttp.DownloadProgress() {
            @Override
            public void onProgress(long bytesRead, long totalBytes) {
                if (totalBytes > MAX_CACHE_BYTES) {
                    // Soft cap — SolarHttp may still finish; check after.
                }
                if (progress != null) {
                    int pct;
                    if (totalBytes > 0) {
                        pct = (int) Math.min(99, (bytesRead * 100L) / totalBytes);
                    } else {
                        // Unknown length: pulse by MB downloaded.
                        pct = (int) Math.min(95, bytesRead / (512L * 1024L));
                    }
                    progress.onProgress(pct, bytesRead, totalBytes);
                }
            }
        }, 0L, null, cancel);

        if (cancel != null && cancel.get()) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new java.io.IOException("cancelled");
        }
        if (!tmp.isFile() || tmp.length() < MIN_USABLE_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new java.io.IOException("download too small: "
                    + (tmp.isFile() ? tmp.length() : 0));
        }
        if (tmp.length() > MAX_CACHE_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new java.io.IOException("stream too large: " + tmp.length());
        }
        if (out.exists()) //noinspection ResultOfMethodCallIgnored
            out.delete();
        if (!tmp.renameTo(out)) {
            copyFile(tmp, out);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        if (!isUsable(out)) {
            throw new java.io.IOException("cache unusable after rename");
        }
        if (progress != null) progress.onProgress(100, out.length(), out.length());
        Log.i(TAG, "cached " + out.getName() + " bytes=" + out.length());
        return out;
    }

    private static void copyFile(File from, File to) throws Exception {
        java.io.FileInputStream in = null;
        java.io.FileOutputStream out = null;
        try {
            in = new java.io.FileInputStream(from);
            out = new java.io.FileOutputStream(to);
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }
}
