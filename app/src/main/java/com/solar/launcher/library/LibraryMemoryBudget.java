package com.solar.launcher.library;

/**
 * 2026-07-18/20 — Decide FULL_RESIDENT vs SEGMENTED for the music library in RAM.
 * Layman: tiny libraries keep songs in memory; a few hundred+ page from SQLite so ~256MB players stay lively.
 * Technical: heapBudget = min(maxMemory×0.18, 48MB); SEGMENTED at 300+ tracks or when estimate/free-heap is tight.
 * Planning floor: 256MB total / often ~64MB avail — do not demand 64MB free heap (that forced SEGMENTED forever).
 * Reversal: always FULL_RESIDENT (ignore SEGMENTED path); restore TARGET_FREE_HEAP 64MB gate if needed.
 */
public final class LibraryMemoryBudget {

    /** Rough SongItem + path/string overhead in bytes (conservative). */
    public static final int BYTES_PER_SONG_ITEM = 320;

    /**
     * 2026-07-20 — Real residency includes customLibrary + FlowLibraryRows + policy tracks.
     * Layman: one song in the library costs about three copies in RAM today.
     * Technical: estimateFullBytes multiplies by this before comparing to heap budget.
     * Reversal: set to 1 (count SongItem list only).
     */
    public static final int RESIDENT_COPY_FACTOR = 3;

    /** Cap resident catalog RAM so MT6572-class devices keep headroom. */
    public static final long MAX_BUDGET_BYTES = 48L * 1024L * 1024L;

    /** Fraction of Runtime.maxMemory() allowed for full SongItem residency. */
    public static final double MAX_MEMORY_FRACTION = 0.18;

    /**
     * 2026-07-20 — Soft free-heap floor for early SEGMENTED (not a 64MB near-hard gate).
     * Layman: only page early when the app heap is truly squeezed, not when ~64MB system free is normal.
     * Technical: was TARGET_FREE_HEAP_BYTES=64MB (wrong on 256MB/~64MB-avail). Reversal: 64MB constant.
     */
    public static final long SOFT_FREE_HEAP_BYTES = 12L * 1024L * 1024L;

    /**
     * @deprecated 2026-07-20 — Alias of {@link #SOFT_FREE_HEAP_BYTES}. Was 64MB hard gate; do not restore.
     */
    public static final long TARGET_FREE_HEAP_BYTES = SOFT_FREE_HEAP_BYTES;

    /** Alias of {@link #SOFT_FREE_HEAP_BYTES} for callers that checked LOW_FREE_*. */
    public static final long LOW_FREE_HEAP_BYTES = SOFT_FREE_HEAP_BYTES;

    /**
     * 2026-07-20 — Prefetch ±2 neighbor blocks when free heap is at least this roomy.
     * Layman: only warm extra shelves when RAM has spare room.
     * Technical: MainActivity scheduleSongBrowsePrefetchAround; was TARGET 64MB. Reversal: 64MB.
     */
    public static final long PREFETCH_EXTRA_FREE_BYTES = 24L * 1024L * 1024L;

    public static final int LOW_FREE_MIN_TRACKS = 120;

    public enum Mode {
        /** Entire library as SongItem list in RAM (today’s customLibrary). */
        FULL_RESIDENT,
        /** Compact indexes + LRU segment pages only. */
        SEGMENTED
    }

    private LibraryMemoryBudget() {}

    /**
     * Heap bytes we may spend on a full SongItem catalog.
     * Layman: how much room is left for “all songs in memory.”
     */
    public static long heapBudgetBytes() {
        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory();
        long byFrac = (long) (max * MAX_MEMORY_FRACTION);
        return Math.min(byFrac, MAX_BUDGET_BYTES);
    }

    /** Estimated bytes if every track is a resident SongItem (+ duplicate catalogs). */
    public static long estimateFullBytes(int trackCount) {
        if (trackCount <= 0) return 0L;
        return (long) trackCount * (long) BYTES_PER_SONG_ITEM * (long) RESIDENT_COPY_FACTOR;
    }

    /**
     * Pick mode for this track count + free-heap headroom (+ system avail when LowMemoryGate is warm).
     * Layman: stay full-resident when it fits; otherwise segment to disk-backed pages.
     */
    public static Mode chooseMode(int trackCount) {
        long avail = 0L;
        // 2026-07-20 — Soft system pressure when Application already wired LowMemoryGate.
        try {
            com.solar.launcher.LowMemoryGate.Snapshot s =
                    com.solar.launcher.LowMemoryGate.evaluate(null);
            if (s != null) avail = s.availMem;
        } catch (Throwable ignored) {}
        return chooseMode(trackCount, heapBudgetBytes(), freeMemoryBytes(), avail);
    }

    /**
     * 2026-07-20 — Hard track-count gate for real Y1/Y2 libraries (Approach 3 lift).
     * Layman: once you have a few hundred songs, keep the big list on disk and page chunks.
     * Technical: SEGMENTED when trackCount &gt;= this OR any heap/estimate gate below.
     * Reversal: set to Integer.MAX_VALUE (heap gates only); was effectively ~53k via estimate.
     */
    public static final int SEGMENTED_MIN_TRACKS = 300;

    /**
     * Injectable for unit tests (budget + free heap bytes; system avail unknown).
     * Layman: pick full-in-RAM vs page-from-DB for this library size and free memory.
     */
    public static Mode chooseMode(int trackCount, long budgetBytes, long freeHeapBytes) {
        return chooseMode(trackCount, budgetBytes, freeHeapBytes, 0L);
    }

    /**
     * 2026-07-20 — Also honor system availMem when known (ActivityManager).
     * Layman: if the whole phone is nearly out of free RAM, page the library even when the app heap looks OK.
     * Technical: systemAvailMem&gt;0 and under soft floor + ≥LOW_FREE_MIN_TRACKS → SEGMENTED.
     * Reversal: ignore systemAvailMem (pass 0).
     */
    public static Mode chooseMode(int trackCount, long budgetBytes, long freeHeapBytes,
            long systemAvailMem) {
        if (trackCount <= 0) return Mode.FULL_RESIDENT;
        // 2026-07-20 — Real-device libraries: segment at 300+ even when heap still looks roomy.
        if (trackCount >= SEGMENTED_MIN_TRACKS) return Mode.SEGMENTED;
        long estimate = estimateFullBytes(trackCount);
        if (estimate > budgetBytes) return Mode.SEGMENTED;
        // Need ~estimate free so hydrate does not thrash GC mid-scan.
        if (freeHeapBytes > 0L && freeHeapBytes < estimate + (2L * 1024L * 1024L)) {
            return Mode.SEGMENTED;
        }
        // Soft free-heap squeeze (12MB class) — not the old 64MB near-hard gate.
        if (freeHeapBytes > 0L && freeHeapBytes < SOFT_FREE_HEAP_BYTES
                && trackCount >= LOW_FREE_MIN_TRACKS) {
            return Mode.SEGMENTED;
        }
        // System availMem soft floor (~24MB) for 256MB/~64MB-avail planning.
        if (systemAvailMem > 0L && systemAvailMem < SOFT_SYSTEM_AVAIL_BYTES
                && trackCount >= LOW_FREE_MIN_TRACKS) {
            return Mode.SEGMENTED;
        }
        return Mode.FULL_RESIDENT;
    }

    /**
     * 2026-07-20 — Soft system availMem floor (~24MB) for SEGMENTED early-out.
     * Layman: when the phone only has a little free RAM left, don’t keep the whole song list in memory.
     * Reversal: Integer.MAX_VALUE (ignore system avail in chooseMode).
     */
    public static final long SOFT_SYSTEM_AVAIL_BYTES = 24L * 1024L * 1024L;

    static long freeMemoryBytes() {
        Runtime rt = Runtime.getRuntime();
        return rt.maxMemory() - (rt.totalMemory() - rt.freeMemory());
    }

    /** JVM self-check used by unit test. Layman: prove small stays full; 300+ and tight-RAM page. */
    static void selfCheck() {
        if (chooseMode(100, 48L * 1024 * 1024, 64L * 1024 * 1024) != Mode.FULL_RESIDENT) {
            throw new AssertionError("small lib full");
        }
        // 2026-07-20 — Tiny stays FULL; 300+ segments even with roomy heap (was ~53k via estimate only).
        if (chooseMode(299, 48L * 1024 * 1024, 64L * 1024 * 1024) != Mode.FULL_RESIDENT) {
            throw new AssertionError("299 stays full when heap ok");
        }
        if (chooseMode(300, 48L * 1024 * 1024, 64L * 1024 * 1024) != Mode.SEGMENTED) {
            throw new AssertionError("300+ segmented");
        }
        if (chooseMode(200_000, 10L * 1024 * 1024, 64L * 1024 * 1024) != Mode.SEGMENTED) {
            throw new AssertionError("huge lib segmented");
        }
        if (estimateFullBytes(10) != 10L * BYTES_PER_SONG_ITEM * RESIDENT_COPY_FACTOR) {
            throw new AssertionError("estimate");
        }
        // 2026-07-20 — ~53k tracks × 3 copies exceeds 48MB budget → SEGMENTED.
        if (chooseMode(53_000, MAX_BUDGET_BYTES, 64L * 1024 * 1024) != Mode.SEGMENTED) {
            throw new AssertionError("53k should segment with copy factor");
        }
        // Soft free (~12MB) + ≥120 tracks → page; 63MB free must NOT force SEGMENTED (256MB floor).
        if (chooseMode(150, 48L * 1024 * 1024, 4L * 1024 * 1024) != Mode.SEGMENTED) {
            throw new AssertionError("low free segmented");
        }
        if (chooseMode(150, 48L * 1024 * 1024, 63L * 1024 * 1024) != Mode.FULL_RESIDENT) {
            throw new AssertionError("63MB free heap stays full under 300 tracks");
        }
        if (chooseMode(50, 48L * 1024 * 1024, 4L * 1024 * 1024) != Mode.FULL_RESIDENT) {
            throw new AssertionError("tiny stays full under low free");
        }
        if (SOFT_FREE_HEAP_BYTES != 12L * 1024L * 1024L) {
            throw new AssertionError("soft free is 12MB");
        }
        // System avail soft floor: 20MB avail + 150 tracks → SEGMENTED.
        if (chooseMode(150, 48L * 1024 * 1024, 64L * 1024 * 1024, 20L * 1024 * 1024)
                != Mode.SEGMENTED) {
            throw new AssertionError("low system avail segmented");
        }
        if (chooseMode(150, 48L * 1024 * 1024, 64L * 1024 * 1024, 64L * 1024 * 1024)
                != Mode.FULL_RESIDENT) {
            throw new AssertionError("roomy system avail stays full");
        }
    }
}
