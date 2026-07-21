package com.solar.launcher;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session 0705ff: large-lib scroll must match empty-lib speed.
 * Layman: while you spin the dial, count how long each jump took and how full RAM/library is.
 * Technical: aggregate ~4×/sec; logcat + async HTTP ingest (no sync SD on wheel).
 * Pull host: .cursor/debug-0705ff.log (via ingest) or adb logcat -d -s SolarDbg0705ff:I
 * Reversal: ENABLED=false; delete class + #region agent log call sites after fix confirmed.
 */
public final class Debug0705ffLog {
    private static final String TAG = "SolarDbg0705ff";
    private static final String SESSION = "0705ff";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-0705ff.log";

    /**
     * 2026-07-21 — OFF after dual-line Approach A. Was true during 0705ff large-lib hunt.
     * Reversal: true while re-comparing large vs empty library dial cost.
     */
    public static final boolean ENABLED = false;

    private static volatile String runId = "pre-fix";
    private static volatile String deviceTag = "?";
    private static long windowStartMs = -1L;
    private static int offers;
    private static int flushes;
    private static int stales;
    private static int choreYields;
    private static long sumOfferGapMs;
    private static long sumApplyMs;
    private static long maxApplyMs;
    private static long lastOfferMs = -1L;
    private static int lastScreen;
    private static int lastLibSize = -1;
    private static int lastRamTracks = -1;
    private static int lastArtists = -1;
    private static int lastAlbums = -1;
    private static String lastMode = "?";
    private static long lastFreeKb = -1L;
    private static long lastTotalKb = -1L;
    private static int lastPending;
    private static long lastMinFlush;
    private static boolean lastInputBusy;
    private static int lastListCount;

    private Debug0705ffLog() {}

    /** 2026-07-20 — Tag family + short serial for A/B samples. */
    public static void setDeviceTag(String tag) {
        if (tag != null && tag.length() > 0) deviceTag = tag;
    }

    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — One-shot library residency (hydrate / reconcile).
     * Layman: after load, note how many songs sit in RAM vs DB index.
     * Technical: H1 — customLibrary vs SEGMENTED Tier-0 counts + heap.
     */
    public static void onLibraryResidency(String where, int customLibSize, int ramTracks,
            String mode, int artists, int albums) {
        if (!ENABLED) return;
        sampleHeap();
        // #region agent log
        StringBuilder sb = new StringBuilder(280);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"").append(where != null ? where : "residency").append('"');
        sb.append(",\"message\":\"library residency\"");
        sb.append(",\"hypothesisId\":\"H1,H5\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"customLib\":").append(customLibSize);
        sb.append(",\"ramTracks\":").append(ramTracks);
        sb.append(",\"mode\":\"").append(mode != null ? mode : "?").append('"');
        sb.append(",\"artists\":").append(artists);
        sb.append(",\"albums\":").append(albums);
        sb.append(",\"freeKb\":").append(lastFreeKb);
        sb.append(",\"totalKb\":").append(lastTotalKb);
        sb.append("}}");
        String line = sb.toString();
        Log.i(TAG, line);
        postIngestAsync(line);
        // #endregion
        lastLibSize = customLibSize;
        lastRamTracks = ramTracks;
        lastMode = mode != null ? mode : "?";
        lastArtists = artists;
        lastAlbums = albums;
    }

    /**
     * 2026-07-20 — Dial KEY / menu offer before apply.
     * Layman: click counted — was it thrown away as stale?
     */
    public static void onWheelOffer(long ageMs, boolean stale, int pending, int screen,
            int customLibSize, int ramTracks, String mode, boolean inputBusy) {
        if (!ENABLED) return;
        bumpWindow();
        offers++;
        lastPending = pending;
        lastScreen = screen;
        lastLibSize = customLibSize;
        lastRamTracks = ramTracks;
        if (mode != null) lastMode = mode;
        lastInputBusy = inputBusy;
        long now = SystemClock.uptimeMillis();
        if (lastOfferMs >= 0L) sumOfferGapMs += Math.max(0L, now - lastOfferMs);
        lastOfferMs = now;
        if (stale) stales++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — Highlight / selection paint finished.
     * Layman: how long the jump took; heap after paint (H2/H5).
     */
    public static void onWheelFlush(long applyMs, long minFlushMs, int pending, int listCount,
            int customLibSize, int ramTracks, String mode, int artists, int albums) {
        if (!ENABLED) return;
        bumpWindow();
        flushes++;
        long a = Math.max(0L, applyMs);
        sumApplyMs += a;
        if (a > maxApplyMs) maxApplyMs = a;
        lastMinFlush = minFlushMs;
        lastPending = pending;
        lastListCount = listCount;
        lastLibSize = customLibSize;
        lastRamTracks = ramTracks;
        if (mode != null) lastMode = mode;
        lastArtists = artists;
        lastAlbums = albums;
        sampleHeap();
        maybeEmit();
    }

    /**
     * 2026-07-20 — Art/scan chore slept because dial was busy (H3).
     * Layman: cover baking waited so scrolling could stay snappy.
     */
    public static void onChoreYield(long waitMs, int gen) {
        if (!ENABLED) return;
        bumpWindow();
        choreYields++;
        // #region agent log
        if (choreYields == 1 || choreYields % 8 == 0) {
            StringBuilder sb = new StringBuilder(200);
            sb.append("{\"sessionId\":\"").append(SESSION).append('"');
            sb.append(",\"runId\":\"").append(runId).append('"');
            sb.append(",\"timestamp\":").append(System.currentTimeMillis());
            sb.append(",\"location\":\"Debug0705ffLog.onChoreYield\"");
            sb.append(",\"message\":\"chore yield to input\"");
            sb.append(",\"hypothesisId\":\"H3\"");
            sb.append(",\"data\":{");
            sb.append("\"dev\":\"").append(deviceTag).append('"');
            sb.append(",\"waitMs\":").append(waitMs);
            sb.append(",\"gen\":").append(gen);
            sb.append(",\"yields\":").append(choreYields);
            sb.append(",\"inputBusy\":").append(lastInputBusy);
            sb.append("}}");
            String line = sb.toString();
            Log.i(TAG, line);
            postIngestAsync(line);
        }
        // #endregion
        maybeEmit();
    }

    /** 2026-07-20 — Boot: which leftover debug writers are still armed (H4). */
    public static void logPoisonFlags() {
        if (!ENABLED) return;
        // #region agent log
        StringBuilder sb = new StringBuilder(220);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"Debug0705ffLog.logPoisonFlags\"");
        sb.append(",\"message\":\"poison logger flags\"");
        sb.append(",\"hypothesisId\":\"H4\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"dbgFb1\":").append(DebugFb1dc1Log.ENABLED);
        sb.append(",\"dbgA177\":").append(DebugA177c4Log.ENABLED);
        sb.append(",\"dbgE0\":").append(DebugE0de2eLog.ENABLED);
        sb.append(",\"dbg712\":").append(Debug712c71Log.ENABLED);
        sb.append(",\"dbgA3e\":").append(DebugA3e8ffLog.ENABLED);
        sb.append(",\"dbg310\":").append(Debug3103d7Log.ENABLED);
        sb.append(",\"minFlushList\":").append(ListWheelCoalescer.listMinFlushMsForDevice());
        sb.append(",\"minFlushMenu\":").append(ListWheelCoalescer.menuMinFlushMsForDevice());
        sb.append("}}");
        String line = sb.toString();
        Log.i(TAG, line);
        postIngestAsync(line);
        // #endregion
    }

    private static void sampleHeap() {
        try {
            Runtime rt = Runtime.getRuntime();
            lastFreeKb = rt.freeMemory() / 1024L;
            lastTotalKb = rt.totalMemory() / 1024L;
        } catch (Throwable ignored) {}
    }

    private static void bumpWindow() {
        if (windowStartMs < 0L) windowStartMs = SystemClock.uptimeMillis();
    }

    private static void maybeEmit() {
        long now = SystemClock.uptimeMillis();
        if (windowStartMs < 0L) return;
        if (now - windowStartMs < 250L) return;
        emit(now);
    }

    private static void emit(long now) {
        long win = Math.max(1L, now - windowStartMs);
        int o = offers;
        int f = flushes;
        // #region agent log
        StringBuilder sb = new StringBuilder(360);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"Debug0705ffLog.emit\"");
        sb.append(",\"message\":\"scroll sample\"");
        sb.append(",\"hypothesisId\":\"H1,H2,H3,H4,H5\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"winMs\":").append(win);
        sb.append(",\"offers\":").append(o);
        sb.append(",\"flushes\":").append(f);
        sb.append(",\"stales\":").append(stales);
        sb.append(",\"choreYields\":").append(choreYields);
        sb.append(",\"avgGapMs\":").append(o > 1 ? (sumOfferGapMs / (o - 1)) : -1);
        sb.append(",\"avgApplyMs\":").append(f > 0 ? (sumApplyMs / f) : -1);
        sb.append(",\"maxApplyMs\":").append(maxApplyMs);
        sb.append(",\"pending\":").append(lastPending);
        sb.append(",\"minFlush\":").append(lastMinFlush);
        sb.append(",\"screen\":").append(lastScreen);
        sb.append(",\"listCount\":").append(lastListCount);
        sb.append(",\"customLib\":").append(lastLibSize);
        sb.append(",\"ramTracks\":").append(lastRamTracks);
        sb.append(",\"mode\":\"").append(lastMode).append('"');
        sb.append(",\"artists\":").append(lastArtists);
        sb.append(",\"albums\":").append(lastAlbums);
        sb.append(",\"freeKb\":").append(lastFreeKb);
        sb.append(",\"totalKb\":").append(lastTotalKb);
        sb.append(",\"inputBusy\":").append(lastInputBusy);
        sb.append("}}");
        String line = sb.toString();
        Log.i(TAG, line);
        postIngestAsync(line);
        // #endregion
        windowStartMs = now;
        offers = 0;
        flushes = 0;
        stales = 0;
        choreYields = 0;
        sumOfferGapMs = 0L;
        sumApplyMs = 0L;
        maxApplyMs = 0L;
    }

    /** Async ingest so wheel path never blocks on HTTP/SD. 2026-07-20 */
    private static void postIngestAsync(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    append(new File(HOST_PATH), line);
                } catch (Exception ignoredHost) {}
                try {
                    URL url = new URL(INGEST);
                    HttpURLConnection c = (HttpURLConnection) url.openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    byte[] body = line.getBytes("UTF-8");
                    c.setFixedLengthStreamingMode(body.length);
                    OutputStream os = new BufferedOutputStream(c.getOutputStream());
                    os.write(body);
                    os.flush();
                    os.close();
                    c.getResponseCode();
                    c.disconnect();
                } catch (Exception ignored) {}
            }
        }, "dbg0705ff").start();
    }

    private static void append(File f, String line) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }
}
