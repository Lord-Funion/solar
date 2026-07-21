package com.solar.launcher;

import android.os.SystemClock;
import android.util.Log;

/**
 * 2026-07-20 — Debug session a177c4: Y1/Y2 scroll + context-menu feel killed.
 * Layman: count dial clicks, highlight jumps, and how long leftover debug writes take.
 * Technical: logcat-only aggregated NDJSON (no sync SD/HTTP on wheel — that was the poison).
 * Pull: adb logcat -d -s SolarDbgA177c4:I
 * Reversal: ENABLED=false; delete class + #region agent log call sites after fix confirmed.
 */
public final class DebugA177c4Log {
    private static final String TAG = "SolarDbgA177c4";
    private static final String SESSION = "a177c4";
    /**
     * 2026-07-21 — OFF after dual-line Approach A. Was true during a177c4 hunt.
     * Reversal: true while re-debugging wheel offer/flush poison.
     */
    public static final boolean ENABLED = false;

    private static volatile String runId = "pre-fix";
    private static volatile String deviceTag = "?";
    private static long windowStartMs = -1L;
    private static int offers;
    private static int stales;
    private static int flushes;
    private static int homePreviews;
    private static int dbg6aWrites;
    private static int ctxOpens;
    private static long sumOfferGapMs;
    private static long sumAgeMs;
    private static long sumApplyMs;
    private static long sumHomePreviewMs;
    private static long sumDbg6aMs;
    private static long sumCtxOpenMs;
    private static long lastOfferMs = -1L;
    private static int lastPending;
    private static long lastMinFlush;
    private static int lastScreen;

    private DebugA177c4Log() {}

    /** 2026-07-20 — Tag device family/serial short for samples. */
    public static void setDeviceTag(String tag) {
        if (tag != null && tag.length() > 0) deviceTag = tag;
    }

    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — Once at boot: which leftover agent loggers are still armed.
     * Layman: list the debug switches that can still slow the dial.
     */
    public static void logPoisonFlags() {
        if (!ENABLED) return;
        // #region agent log
        StringBuilder sb = new StringBuilder(180);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"DebugA177c4Log.logPoisonFlags\"");
        sb.append(",\"message\":\"poison logger flags\"");
        sb.append(",\"hypothesisId\":\"H-A\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"dbg6a\":").append(Debug6a626eLog.ENABLED);
        sb.append(",\"dbgFb1\":").append(DebugFb1dc1Log.ENABLED);
        sb.append(",\"dbg10d\":").append(Debug10d371Log.ENABLED);
        sb.append(",\"minFlushList\":").append(ListWheelCoalescer.listMinFlushMsForDevice());
        sb.append(",\"minFlushMenu\":").append(ListWheelCoalescer.menuMinFlushMsForDevice());
        sb.append(",\"staleMs\":90");
        sb.append("}}");
        Log.i(TAG, sb.toString());
        // #endregion
    }

    /**
     * 2026-07-20 — One list/menu wheel KEY before apply.
     * Layman: dial clicked — how late, and was it thrown away as “too old”?
     */
    public static void onWheelOffer(long ageMs, boolean stale, int pending, int screen) {
        if (!ENABLED) return;
        bumpWindow();
        offers++;
        lastPending = pending;
        lastScreen = screen;
        long now = SystemClock.uptimeMillis();
        if (lastOfferMs >= 0L) sumOfferGapMs += Math.max(0L, now - lastOfferMs);
        lastOfferMs = now;
        sumAgeMs += Math.max(0L, ageMs);
        if (stale) stales++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — One highlight paint after coalescer flush.
     * Layman: the bar actually moved — how long did that take?
     */
    public static void onWheelFlush(long applyMs, long minFlushMs, int pending) {
        if (!ENABLED) return;
        bumpWindow();
        flushes++;
        sumApplyMs += Math.max(0L, applyMs);
        lastMinFlush = minFlushMs;
        lastPending = pending;
        maybeEmit();
    }

    /**
     * 2026-07-20 — Home preview paint (includes any armed Debug6a626e write).
     * Layman: right-side cover update timing while browsing home.
     */
    public static void onHomePreview(long totalMs, long dbg6aMs) {
        if (!ENABLED) return;
        bumpWindow();
        homePreviews++;
        sumHomePreviewMs += Math.max(0L, totalMs);
        sumDbg6aMs += Math.max(0L, dbg6aMs);
        maybeEmit();
    }

    /**
     * 2026-07-20 — Time spent inside Debug6a626eLog.log (disk/HTTP).
     * Layman: how long leftover debug writing blocked the UI thread.
     */
    public static void onDbg6aWrite(long writeMs) {
        if (!ENABLED) return;
        bumpWindow();
        dbg6aWrites++;
        sumDbg6aMs += Math.max(0L, writeMs);
        maybeEmit();
    }

    /**
     * 2026-07-20 — Context menu became visible after hold start.
     * Layman: how long from press to Options on screen.
     */
    public static void onContextMenuOpen(long holdToShowMs) {
        if (!ENABLED) return;
        bumpWindow();
        ctxOpens++;
        sumCtxOpenMs += Math.max(0L, holdToShowMs);
        // Always emit context opens — rare and high signal.
        emit(SystemClock.uptimeMillis());
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
        StringBuilder sb = new StringBuilder(280);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"DebugA177c4Log.emit\"");
        sb.append(",\"message\":\"scroll sample\"");
        sb.append(",\"hypothesisId\":\"H-A,H-B,H-C,H-D,H-E\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"winMs\":").append(win);
        sb.append(",\"offers\":").append(o);
        sb.append(",\"stales\":").append(stales);
        sb.append(",\"flushes\":").append(flushes);
        sb.append(",\"homePrev\":").append(homePreviews);
        sb.append(",\"dbg6a\":").append(dbg6aWrites);
        sb.append(",\"ctxOpen\":").append(ctxOpens);
        sb.append(",\"avgGapMs\":").append(o > 1 ? (sumOfferGapMs / (o - 1)) : -1);
        sb.append(",\"avgAgeMs\":").append(o > 0 ? (sumAgeMs / o) : -1);
        sb.append(",\"avgApplyMs\":").append(flushes > 0 ? (sumApplyMs / flushes) : -1);
        sb.append(",\"avgHomeMs\":").append(homePreviews > 0 ? (sumHomePreviewMs / homePreviews) : -1);
        sb.append(",\"avgDbg6aMs\":").append(dbg6aWrites > 0 ? (sumDbg6aMs / dbg6aWrites) : -1);
        sb.append(",\"avgCtxMs\":").append(ctxOpens > 0 ? (sumCtxOpenMs / ctxOpens) : -1);
        sb.append(",\"pending\":").append(lastPending);
        sb.append(",\"minFlush\":").append(lastMinFlush);
        sb.append(",\"screen\":").append(lastScreen);
        sb.append("}}");
        Log.i(TAG, sb.toString());
        windowStartMs = now;
        offers = 0;
        stales = 0;
        flushes = 0;
        homePreviews = 0;
        dbg6aWrites = 0;
        ctxOpens = 0;
        sumOfferGapMs = 0L;
        sumAgeMs = 0L;
        sumApplyMs = 0L;
        sumHomePreviewMs = 0L;
        sumDbg6aMs = 0L;
        sumCtxOpenMs = 0L;
    }
}
