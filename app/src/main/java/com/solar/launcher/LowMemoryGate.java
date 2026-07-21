package com.solar.launcher;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;

/**
 * 2026-07-16/20 — Memory-pressure gate for heavy background work (256MB / ~64MB-avail floor).
 * Layman: when free RAM is tight, Solar pauses expensive scans and art baking
 * so the UI and music keep working instead of thrashing into restarts.
 * Technical: {@link ActivityManager.MemoryInfo} + optional {@code /proc/meminfo}
 * MemFree; fraction of totalMem when known; {@link #onSystemTrim} from Application.
 * Was: fixed 48/32MB floors + “Y1 ~512MB” comments. Reversal: AVAIL 48 / MEMFREE 32 / DEFER 12s.
 */
public final class LowMemoryGate {
    /**
     * 2026-07-20 — Default availMem floor when totalMem unknown (~28MB for 256MB class).
     * Was 48MB (too high when ~64MB free is normal). Reversal: 48MB.
     */
    public static final long AVAIL_FLOOR_BYTES = 28L * 1024L * 1024L;
    /**
     * 2026-07-20 — MemFree alone under this is also pressure.
     * Was 32MB. Reversal: 32MB.
     */
    public static final long MEMFREE_FLOOR_BYTES = 16L * 1024L * 1024L;
    /**
     * 2026-07-20 — Defer heavy work this long when pressured (then re-check).
     * Was 12s — chores resume sooner once free. Reversal: 12_000L.
     */
    public static final long DEFER_MS = 6_000L;
    /** Soft fraction of totalMem for avail floor (clamped). 2026-07-20 */
    public static final double AVAIL_TOTAL_FRACTION = 0.10;
    public static final long AVAIL_FLOOR_MIN_BYTES = 20L * 1024L * 1024L;
    public static final long AVAIL_FLOOR_MAX_BYTES = 40L * 1024L * 1024L;

    private static volatile int lastTrimLevel = -1;
    private static volatile long pressureGen;
    private static volatile Boolean lastPressuredLogged;
    private static volatile long lastProcessStartMs;
    private static volatile Context appContext;

    private LowMemoryGate() {}

    /** Call once from Application.onCreate so context-less callers can sample MemoryInfo. */
    public static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    /** Bump generation from {@link android.app.Application#onTrimMemory}. */
    public static void onSystemTrim(int level) {
        lastTrimLevel = level;
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || level == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            pressureGen++;
        }
    }

    public static void onLowMemory() {
        lastTrimLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
        pressureGen++;
    }

    public static long pressureGeneration() {
        return pressureGen;
    }

    public static int lastTrimLevel() {
        return lastTrimLevel;
    }

    /**
     * 2026-07-20 — availMem pressure floor from totalMem (256MB → ~26MB; clamp 20–40MB).
     * Layman: smaller phones use a lower “almost out of RAM” line; never invent huge floors.
     * Technical: max(MIN, min(MAX, total×10%)). Reversal: always return {@link #AVAIL_FLOOR_BYTES}.
     */
    public static long availFloorBytes(long totalMem) {
        if (totalMem <= 0L) return AVAIL_FLOOR_BYTES;
        long byFrac = (long) (totalMem * AVAIL_TOTAL_FRACTION);
        if (byFrac < AVAIL_FLOOR_MIN_BYTES) return AVAIL_FLOOR_MIN_BYTES;
        if (byFrac > AVAIL_FLOOR_MAX_BYTES) return AVAIL_FLOOR_MAX_BYTES;
        return byFrac;
    }

    /**
     * True when system reports low memory or free RAM is under safe floors.
     * Null-safe: false when context/services unavailable.
     */
    public static boolean isPressured(Context context) {
        return evaluate(context != null ? context : appContext).pressured;
    }

    /**
     * Defer heavy background work when RAM is tight (callers reschedule).
     * Input-busy deferral stays on {@link InputPriorityGate} — combine at call site.
     * 2026-07-18 — Also defer while Stem Player mixes (exclusive session).
     */
    public static boolean shouldDeferHeavyWork(Context context) {
        // 2026-07-19 — Stem or Mix exclusive jam owns the CPU.
        if (StemOrMixSession.isActive()) return true;
        if (com.solar.launcher.stem.StemPlayerHost.isSessionActive()) return true;
        return isPressured(context != null ? context : appContext);
    }

    /** Snapshot for logs / unit-style pure checks. */
    public static Snapshot evaluate(Context context) {
        Context ctx = context != null ? context : appContext;
        Snapshot s = new Snapshot();
        s.trimLevel = lastTrimLevel;
        if (lastTrimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || lastTrimLevel == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            s.pressured = true;
            s.reason = "trim=" + lastTrimLevel;
        }
        if (ctx != null) {
            try {
                ActivityManager am = (ActivityManager) ctx.getApplicationContext()
                        .getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    s.availMem = mi.availMem;
                    s.threshold = mi.threshold;
                    s.totalMem = mi.totalMem;
                    s.systemLowMemory = mi.lowMemory;
                    long floor = availFloorBytes(mi.totalMem);
                    if (mi.lowMemory) {
                        s.pressured = true;
                        s.reason = "lowMemory_flag";
                    } else if (mi.availMem > 0L && mi.availMem < floor) {
                        s.pressured = true;
                        s.reason = "availMem=" + mi.availMem;
                    }
                }
            } catch (Throwable ignored) {}
        }
        long memFree = readMemFreeBytes();
        s.memFree = memFree;
        if (memFree > 0L && memFree < MEMFREE_FLOOR_BYTES) {
            s.pressured = true;
            if (s.reason == null) s.reason = "memFree=" + memFree;
        }
        maybeLogFlip(s.pressured, s.reason);
        return s;
    }

    /**
     * Pure threshold helper for tests — no Context.
     * @param availMem ActivityManager availMem (0 = unknown)
     * @param memFreeBytes /proc MemFree (0 = unknown)
     * @param systemLowMemory MemoryInfo.lowMemory
     * @param trimLevel last ComponentCallbacks2 level (-1 = none)
     */
    public static boolean isPressuredSnapshot(long availMem, long memFreeBytes,
            boolean systemLowMemory, int trimLevel) {
        return isPressuredSnapshot(availMem, memFreeBytes, systemLowMemory, trimLevel, 0L);
    }

    /**
     * 2026-07-20 — Pure threshold with optional totalMem for fractional avail floor.
     * Layman: same pressure rules as on-device, but tests pass fake RAM numbers.
     */
    public static boolean isPressuredSnapshot(long availMem, long memFreeBytes,
            boolean systemLowMemory, int trimLevel, long totalMem) {
        if (systemLowMemory) return true;
        if (trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            return true;
        }
        long floor = availFloorBytes(totalMem);
        if (availMem > 0L && availMem < floor) return true;
        if (memFreeBytes > 0L && memFreeBytes < MEMFREE_FLOOR_BYTES) return true;
        return false;
    }

    /** Process-start thrash: second start within 10s → log once. */
    public static void noteProcessStart() {
        long now = System.currentTimeMillis();
        long prev = lastProcessStartMs;
        lastProcessStartMs = now;
        if (prev > 0L && now - prev < 10_000L) {
            try {
                com.solar.launcher.diag.SolarDiagFeatureLog.warn("app",
                        "process_restart_thrash dtMs=" + (now - prev)
                                + " " + snapshotOneLine(null));
            } catch (Throwable ignored) {}
        }
    }

    public static String snapshotOneLine(Context context) {
        Snapshot s = evaluate(context);
        return "pressured=" + s.pressured
                + " avail=" + s.availMem
                + " memFree=" + s.memFree
                + " thr=" + s.threshold
                + " lowFlag=" + s.systemLowMemory
                + " trim=" + s.trimLevel
                + (s.reason != null ? " reason=" + s.reason : "");
    }

    private static void maybeLogFlip(boolean pressured, String reason) {
        Boolean prev = lastPressuredLogged;
        if (prev != null && prev.booleanValue() == pressured) return;
        lastPressuredLogged = pressured;
        try {
            if (pressured) {
                com.solar.launcher.diag.SolarDiagFeatureLog.warn("app",
                        "mem_pressure_on " + (reason != null ? reason : ""));
            } else {
                // 2026-07-20 — Resume opportunistic YT cache growth when pressure clears.
                try {
                    com.solar.launcher.youtube.YouTubeProgressiveCache.setGrowthPaused(false);
                } catch (Throwable ignored) {}
                com.solar.launcher.diag.SolarDiagFeatureLog.event("app", "mem_pressure_off");
            }
        } catch (Throwable ignored) {}
    }

    /** Parse MemFree from /proc/meminfo → bytes; 0 if unavailable. */
    public static long readMemFreeBytes() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/meminfo")));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemFree:")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 2) {
                            long kb = Long.parseLong(parts[1]);
                            return kb * 1024L;
                        }
                    }
                }
            } finally {
                br.close();
            }
        } catch (Throwable ignored) {}
        return 0L;
    }

    public static final class Snapshot {
        public boolean pressured;
        public long availMem;
        public long threshold;
        public long memFree;
        /** 2026-07-20 — MemoryInfo.totalMem when sampled. */
        public long totalMem;
        public boolean systemLowMemory;
        public int trimLevel = -1;
        public String reason;
    }
}
