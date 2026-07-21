package com.solar.launcher;

import android.os.SystemClock;
import android.util.Log;

/**
 * 2026-07-20 — Debug session 9d0a7a: long library list focus hop / slow refresh (esp. first minutes).
 * Layman: while you spin All Songs, count jumps, blank-shelf fills, and letter-index rebuilds.
 * Technical: logcat-only aggregated NDJSON (~4×/sec); no SD/HTTP on wheel (poison).
 * Pull: adb logcat -d -s SolarDbg9d0a7a:I → .cursor/debug-9d0a7a.log
 * Reversal: ENABLED=false; delete class + #region agent log call sites after fix confirmed.
 */
public final class Debug9d0a7aLog {
    private static final String TAG = "SolarDbg9d0a7a";
    private static final String SESSION = "9d0a7a";

    /**
     * 2026-07-21 — OFF after dual-line Approach A (wheel flush path). Was true during 9d0a7a hunt.
     * Reversal: true while re-metering All Songs hop.
     */
    public static final boolean ENABLED = false;

    private static volatile String runId = "pre-fix";
    private static volatile String deviceTag = "?";
    private static long windowStartMs = -1L;
    private static int offers;
    private static int flushes;
    private static int sectionOks;
    private static int rowsMoved;
    private static int blockNotifies;
    private static int blockNotifiesSpinning;
    private static int sectionIndexSchedules;
    private static long sumSectionIndexUiMs;
    private static long maxSectionIndexUiMs;
    private static int blockLoads;
    private static long sumBlockLoadMs;
    private static long maxBlockLoadMs;
    private static long sumApplyMs;
    private static long maxApplyMs;
    private static int lastListCount;
    private static int lastSegBlocks;
    private static int lastSegCount;
    private static String lastMode = "?";
    private static long lastFreeKb = -1L;
    /** 2026-07-20 — {@link SystemClock#uptimeMillis()} when logBoot ran (age baseline). */
    private static long bootUptimeMs = -1L;

    private Debug9d0a7aLog() {}

    /** 2026-07-20 — Tag family for A/B samples. */
    public static void setDeviceTag(String tag) {
        if (tag != null && tag.length() > 0) deviceTag = tag;
    }

    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — Boot breadcrumb: poison loggers + process age.
     * Layman: note which leftover debug switches are still on when Solar starts.
     */
    public static void logBoot() {
        if (!ENABLED) return;
        bootUptimeMs = SystemClock.uptimeMillis();
        sampleHeap();
        // #region agent log
        StringBuilder sb = new StringBuilder(240);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"Debug9d0a7aLog.logBoot\"");
        sb.append(",\"message\":\"lib scroll hunt boot\"");
        sb.append(",\"hypothesisId\":\"H4\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"uptimeMs\":0");
        sb.append(",\"dbg0705\":").append(Debug0705ffLog.ENABLED);
        sb.append(",\"dbgFb1\":").append(DebugFb1dc1Log.ENABLED);
        sb.append(",\"dbgA177\":").append(DebugA177c4Log.ENABLED);
        sb.append(",\"dbg6a\":").append(Debug6a626eLog.ENABLED);
        sb.append(",\"freeKb\":").append(lastFreeKb);
        sb.append("}}");
        Log.i(TAG, sb.toString());
        // #endregion
    }

    /**
     * 2026-07-20 — Dial flush sample (selection paint).
     * Layman: one highlight jump finished — how far and how long.
     * Technical: H2 section hops + H5 apply cost.
     */
    public static void onWheelFlush(int absSteps, int rows, boolean sectionOk, long applyMs,
            int listCount, int segBlocks, int segCount, String mode, boolean spinning) {
        if (!ENABLED) return;
        bumpWindow();
        flushes++;
        rowsMoved += Math.max(0, rows);
        if (sectionOk) sectionOks++;
        long a = Math.max(0L, applyMs);
        sumApplyMs += a;
        if (a > maxApplyMs) maxApplyMs = a;
        lastListCount = listCount;
        lastSegBlocks = segBlocks;
        lastSegCount = segCount;
        if (mode != null) lastMode = mode;
        sampleHeap();
        maybeEmit();
    }

    /** 2026-07-20 — Dial KEY offer (count only). */
    public static void onWheelOffer() {
        if (!ENABLED) return;
        bumpWindow();
        offers++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — SEGMENTED page landed → notifyDataSetChanged.
     * Layman: a song shelf arrived and the whole list was told to redraw.
     * Technical: H1 — notify while spinning correlates with focus hop.
     */
    public static void onBlockNotify(int blockId, int rowCount, boolean spinning, int segBlocks) {
        if (!ENABLED) return;
        bumpWindow();
        blockNotifies++;
        if (spinning) blockNotifiesSpinning++;
        lastSegBlocks = segBlocks;
        // #region agent log
        if (blockNotifies == 1 || blockNotifies % 4 == 0 || spinning) {
            StringBuilder sb = new StringBuilder(220);
            sb.append("{\"sessionId\":\"").append(SESSION).append('"');
            sb.append(",\"runId\":\"").append(runId).append('"');
            sb.append(",\"timestamp\":").append(System.currentTimeMillis());
            sb.append(",\"location\":\"Debug9d0a7aLog.onBlockNotify\"");
            sb.append(",\"message\":\"segment notify\"");
            sb.append(",\"hypothesisId\":\"H1\"");
            sb.append(",\"data\":{");
            sb.append("\"dev\":\"").append(deviceTag).append('"');
            sb.append(",\"blockId\":").append(blockId);
            sb.append(",\"rows\":").append(rowCount);
            sb.append(",\"spinning\":").append(spinning);
            sb.append(",\"segBlocks\":").append(segBlocks);
            sb.append(",\"notifies\":").append(blockNotifies);
            sb.append(",\"spinNotifies\":").append(blockNotifiesSpinning);
            sb.append("}}");
            Log.i(TAG, sb.toString());
        }
        // #endregion
        maybeEmit();
    }

    /**
     * 2026-07-20 — Letter-index rebuild scheduled (often from notify).
     * Layman: Solar is rebuilding the A–Z jump table for the whole song list.
     * Technical: H2 — UI label-loop ms + posts WHEEL_IO build (contends with page loads).
     */
    public static void onSectionIndexSchedule(int count, boolean useBrowseLabels, long uiLoopMs) {
        if (!ENABLED) return;
        bumpWindow();
        sectionIndexSchedules++;
        long u = Math.max(0L, uiLoopMs);
        sumSectionIndexUiMs += u;
        if (u > maxSectionIndexUiMs) maxSectionIndexUiMs = u;
        lastListCount = count;
        // #region agent log
        if (sectionIndexSchedules == 1 || sectionIndexSchedules % 3 == 0 || u >= 8L) {
            StringBuilder sb = new StringBuilder(240);
            sb.append("{\"sessionId\":\"").append(SESSION).append('"');
            sb.append(",\"runId\":\"").append(runId).append('"');
            sb.append(",\"timestamp\":").append(System.currentTimeMillis());
            sb.append(",\"location\":\"Debug9d0a7aLog.onSectionIndexSchedule\"");
            sb.append(",\"message\":\"section index rebuild\"");
            sb.append(",\"hypothesisId\":\"H2\"");
            sb.append(",\"data\":{");
            sb.append("\"dev\":\"").append(deviceTag).append('"');
            sb.append(",\"count\":").append(count);
            sb.append(",\"useBrowse\":").append(useBrowseLabels);
            sb.append(",\"uiLoopMs\":").append(u);
            sb.append(",\"schedules\":").append(sectionIndexSchedules);
            sb.append("}}");
            Log.i(TAG, sb.toString());
        }
        // #endregion
        maybeEmit();
    }

    /**
     * 2026-07-20 — BG SQLite page load finished.
     * Layman: how long one song shelf took to fetch from the library DB.
     * Technical: H3 — slow loads early (scan/IO) vs after idle.
     */
    public static void onBlockLoad(int blockId, long loadMs, String reason, int inFlight) {
        if (!ENABLED) return;
        bumpWindow();
        blockLoads++;
        long m = Math.max(0L, loadMs);
        sumBlockLoadMs += m;
        if (m > maxBlockLoadMs) maxBlockLoadMs = m;
        // #region agent log
        if (blockLoads == 1 || blockLoads % 4 == 0 || m >= 40L) {
            StringBuilder sb = new StringBuilder(220);
            sb.append("{\"sessionId\":\"").append(SESSION).append('"');
            sb.append(",\"runId\":\"").append(runId).append('"');
            sb.append(",\"timestamp\":").append(System.currentTimeMillis());
            sb.append(",\"location\":\"Debug9d0a7aLog.onBlockLoad\"");
            sb.append(",\"message\":\"segment block load\"");
            sb.append(",\"hypothesisId\":\"H3\"");
            sb.append(",\"data\":{");
            sb.append("\"dev\":\"").append(deviceTag).append('"');
            sb.append(",\"blockId\":").append(blockId);
            sb.append(",\"loadMs\":").append(m);
            sb.append(",\"reason\":\"").append(reason != null ? reason : "?").append('"');
            sb.append(",\"inFlight\":").append(inFlight);
            sb.append(",\"loads\":").append(blockLoads);
            sb.append(",\"uptimeMs\":").append(processUptimeMs());
            sb.append("}}");
            Log.i(TAG, sb.toString());
        }
        // #endregion
        maybeEmit();
    }

    /** 2026-07-20 — Ms since logBoot (process age for early-vs-idle samples). */
    private static long processUptimeMs() {
        if (bootUptimeMs < 0L) return -1L;
        return SystemClock.uptimeMillis() - bootUptimeMs;
    }

    private static void sampleHeap() {
        try {
            Runtime rt = Runtime.getRuntime();
            lastFreeKb = rt.freeMemory() / 1024L;
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
        int f = flushes;
        // #region agent log
        StringBuilder sb = new StringBuilder(400);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"Debug9d0a7aLog.emit\"");
        sb.append(",\"message\":\"lib scroll sample\"");
        sb.append(",\"hypothesisId\":\"H1,H2,H3,H4,H5\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"winMs\":").append(win);
        sb.append(",\"offers\":").append(offers);
        sb.append(",\"flushes\":").append(f);
        sb.append(",\"sectionOk\":").append(sectionOks);
        sb.append(",\"rows\":").append(rowsMoved);
        sb.append(",\"avgApplyMs\":").append(f > 0 ? (sumApplyMs / f) : -1);
        sb.append(",\"maxApplyMs\":").append(maxApplyMs);
        sb.append(",\"blockNotify\":").append(blockNotifies);
        sb.append(",\"blockNotifySpin\":").append(blockNotifiesSpinning);
        sb.append(",\"secIdxSched\":").append(sectionIndexSchedules);
        sb.append(",\"avgSecIdxUiMs\":").append(sectionIndexSchedules > 0
                ? (sumSectionIndexUiMs / sectionIndexSchedules) : -1);
        sb.append(",\"maxSecIdxUiMs\":").append(maxSectionIndexUiMs);
        sb.append(",\"blockLoads\":").append(blockLoads);
        sb.append(",\"avgBlockMs\":").append(blockLoads > 0 ? (sumBlockLoadMs / blockLoads) : -1);
        sb.append(",\"maxBlockMs\":").append(maxBlockLoadMs);
        sb.append(",\"listCount\":").append(lastListCount);
        sb.append(",\"segBlocks\":").append(lastSegBlocks);
        sb.append(",\"segCount\":").append(lastSegCount);
        sb.append(",\"mode\":\"").append(lastMode).append('"');
        sb.append(",\"freeKb\":").append(lastFreeKb);
        sb.append(",\"uptimeMs\":").append(processUptimeMs());
        sb.append("}}");
        Log.i(TAG, sb.toString());
        // #endregion
        windowStartMs = now;
        offers = 0;
        flushes = 0;
        sectionOks = 0;
        rowsMoved = 0;
        blockNotifies = 0;
        blockNotifiesSpinning = 0;
        sectionIndexSchedules = 0;
        sumSectionIndexUiMs = 0L;
        maxSectionIndexUiMs = 0L;
        blockLoads = 0;
        sumBlockLoadMs = 0L;
        maxBlockLoadMs = 0L;
        sumApplyMs = 0L;
        maxApplyMs = 0L;
    }
}
