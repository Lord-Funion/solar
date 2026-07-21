package com.solar.launcher;

import android.os.SystemClock;
import android.util.Log;

/**
 * 2026-07-20 — Debug session fb1dc1: slow wheel on small-lib Y1 + Y2 vs snappy large-lib Y1.
 * Layman: count dial clicks vs highlight jumps so we see why some players feel stuck in molasses.
 * Technical: logcat-only aggregated samples (no SD/HTTP — those poisoned Y1 scroll before).
 * Pull: adb logcat -d -s SolarDbgFb1dc1:I
 * Reversal: ENABLED=false; delete class + #region agent log call sites after fix confirmed.
 */
public final class DebugFb1dc1Log {
    private static final String TAG = "SolarDbgFb1dc1";
    private static final String SESSION = "fb1dc1";
    /**
     * 2026-07-21 — OFF after dual-line Approach A. Was true during fb1dc1 verify.
     * Reversal: true while re-checking small-lib dial snappiness.
     */
    public static final boolean ENABLED = false;

    private static volatile String runId = "pre-fix";
    private static volatile String deviceTag = "?";
    private static long windowStartMs = -1L;
    private static int offers;
    private static int flushes;
    private static int stales;
    private static int idleClears;
    private static int immediateOnes;
    private static int coalesceOffers;
    private static int frameDrops;
    private static int previewHits;
    private static int sectionOks;
    private static int rowsMoved;
    private static long sumOfferGapMs;
    private static long sumEventAgeMs;
    private static long sumApplyMs;
    private static long lastOfferMs = -1L;
    private static int lastListCount;
    private static long lastMinFlushMs;
    private static float lastVel;
    private static int lastPending;

    private DebugFb1dc1Log() {}

    /** 2026-07-20 — Tag this device in samples (serial short / family). */
    public static void setDeviceTag(String tag) {
        if (tag != null && tag.length() > 0) deviceTag = tag;
    }

    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — Note one live list-wheel KEY (before stale/apply).
     * Layman: dial clicked — record how late the click was and how hard the flywheel is spinning.
     */
    public static void onListKey(long eventAgeMs, int signedSteps, float velocity,
            int pending, int listCount, boolean stale) {
        if (!ENABLED) return;
        bumpWindow();
        offers++;
        sumEventAgeMs += Math.max(0L, eventAgeMs);
        lastVel = velocity;
        lastPending = pending;
        lastListCount = listCount;
        long now = SystemClock.uptimeMillis();
        if (lastOfferMs >= 0L) {
            sumOfferGapMs += Math.max(0L, now - lastOfferMs);
        }
        lastOfferMs = now;
        if (stale) stales++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — Note immediate |steps|==1 apply (bypasses coalescer).
     * Layman: one click → one highlight move right now, no batching.
     */
    public static void onImmediateOne(int listCount) {
        if (!ENABLED) return;
        bumpWindow();
        immediateOnes++;
        lastListCount = listCount;
        maybeEmit();
    }

    /** 2026-07-20 — Note coalescer.offerSteps path. */
    public static void onCoalesceOffer(int signedSteps) {
        if (!ENABLED) return;
        bumpWindow();
        coalesceOffers++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — Note coalescer idle-clear (≥50 ms gap wiped pending).
     * Layman: finger paused long enough that leftover dial clicks were thrown away.
     */
    public static void onIdleClear(long gapMs) {
        if (!ENABLED) return;
        bumpWindow();
        idleClears++;
        maybeEmit();
    }

    /**
     * 2026-07-20 — Note one selection paint flush.
     * Layman: the highlight actually jumped — how far, and did we skip heavy art?
     */
    public static void onFlush(int absSteps, int rows, boolean frameDrop, boolean sectionOk,
            boolean didPreview, long applyMs, long minFlushMs, int listCount, int pending) {
        if (!ENABLED) return;
        bumpWindow();
        flushes++;
        rowsMoved += Math.max(0, rows);
        if (frameDrop) frameDrops++;
        if (sectionOk) sectionOks++;
        if (didPreview) previewHits++;
        sumApplyMs += Math.max(0L, applyMs);
        lastMinFlushMs = minFlushMs;
        lastListCount = listCount;
        lastPending = pending;
        maybeEmit();
    }

    private static void bumpWindow() {
        if (windowStartMs < 0L) {
            windowStartMs = SystemClock.uptimeMillis();
        }
    }

    private static void maybeEmit() {
        long now = SystemClock.uptimeMillis();
        if (windowStartMs < 0L) return;
        // Emit ~4×/sec while spinning; cheap logcat line only.
        if (now - windowStartMs < 250L) return;
        emit(now);
    }

    private static void emit(long now) {
        long win = Math.max(1L, now - windowStartMs);
        int o = offers;
        StringBuilder sb = new StringBuilder(220);
        sb.append("{\"sessionId\":\"").append(SESSION).append('"');
        sb.append(",\"runId\":\"").append(runId).append('"');
        sb.append(",\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"location\":\"DebugFb1dc1Log.emit\"");
        sb.append(",\"message\":\"wheel sample\"");
        sb.append(",\"hypothesisId\":\"H-A,H-B,H-C,H-D,H-E\"");
        sb.append(",\"data\":{");
        sb.append("\"dev\":\"").append(deviceTag).append('"');
        sb.append(",\"winMs\":").append(win);
        sb.append(",\"offers\":").append(o);
        sb.append(",\"flushes\":").append(flushes);
        sb.append(",\"stales\":").append(stales);
        sb.append(",\"idleClears\":").append(idleClears);
        sb.append(",\"imm1\":").append(immediateOnes);
        sb.append(",\"coal\":").append(coalesceOffers);
        sb.append(",\"frameDrop\":").append(frameDrops);
        sb.append(",\"preview\":").append(previewHits);
        sb.append(",\"sectionOk\":").append(sectionOks);
        sb.append(",\"rows\":").append(rowsMoved);
        sb.append(",\"avgGapMs\":").append(o > 1 ? (sumOfferGapMs / (o - 1)) : -1);
        sb.append(",\"avgAgeMs\":").append(o > 0 ? (sumEventAgeMs / o) : -1);
        sb.append(",\"avgApplyMs\":").append(flushes > 0 ? (sumApplyMs / flushes) : -1);
        sb.append(",\"listCount\":").append(lastListCount);
        sb.append(",\"minFlush\":").append(lastMinFlushMs);
        sb.append(",\"vel\":").append(lastVel);
        sb.append(",\"pending\":").append(lastPending);
        sb.append("}}");
        Log.i(TAG, sb.toString());
        // Reset window.
        windowStartMs = now;
        offers = 0;
        flushes = 0;
        stales = 0;
        idleClears = 0;
        immediateOnes = 0;
        coalesceOffers = 0;
        frameDrops = 0;
        previewHits = 0;
        sectionOks = 0;
        rowsMoved = 0;
        sumOfferGapMs = 0L;
        sumEventAgeMs = 0L;
        sumApplyMs = 0L;
    }
}
