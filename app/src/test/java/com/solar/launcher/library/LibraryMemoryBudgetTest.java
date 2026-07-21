package com.solar.launcher.library;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 2026-07-18/20 — Library memory / segment / RAM index self-checks (256MB planning floor).
 */
public class LibraryMemoryBudgetTest {

    @Test
    public void budgetSelfCheck() {
        LibraryMemoryBudget.selfCheck();
    }

    /** 2026-07-20 — 300-track gate (Approach 3): tiny FULL, 300+ SEGMENTED when heap roomy. */
    @Test
    public void segmentedAtThreeHundredTracks() {
        long roomyBudget = 48L * 1024 * 1024;
        long roomyFree = 64L * 1024 * 1024;
        assertEquals(LibraryMemoryBudget.Mode.FULL_RESIDENT,
                LibraryMemoryBudget.chooseMode(299, roomyBudget, roomyFree));
        assertEquals(LibraryMemoryBudget.Mode.SEGMENTED,
                LibraryMemoryBudget.chooseMode(300, roomyBudget, roomyFree));
        assertEquals(300, LibraryMemoryBudget.SEGMENTED_MIN_TRACKS);
    }

    @Test
    public void segmentSelfCheck() {
        LibrarySegmentCache.selfCheck();
    }

    @Test
    public void ramCacheSelfCheck() {
        LibraryRamCache.selfCheck();
    }

    /**
     * 2026-07-20 — Dropped 64MB free-heap near-hard gate (256MB/~64MB-avail devices).
     * Layman: having ~63MB free heap must not force paging under 300 tracks.
     */
    @Test
    public void softFreeHeapIsTwelveMbNotSixtyFour() {
        assertEquals(12L * 1024 * 1024, LibraryMemoryBudget.SOFT_FREE_HEAP_BYTES);
        assertEquals(LibraryMemoryBudget.SOFT_FREE_HEAP_BYTES,
                LibraryMemoryBudget.TARGET_FREE_HEAP_BYTES);
        assertEquals(LibraryMemoryBudget.SOFT_FREE_HEAP_BYTES,
                LibraryMemoryBudget.LOW_FREE_HEAP_BYTES);
        // Under soft free + ≥120 tracks → SEGMENTED.
        assertEquals(LibraryMemoryBudget.Mode.SEGMENTED,
                LibraryMemoryBudget.chooseMode(150, 48L * 1024 * 1024, 4L * 1024 * 1024));
        // 63MB free heap stays FULL under 300 (was wrongly SEGMENTED under 64MB gate).
        assertEquals(LibraryMemoryBudget.Mode.FULL_RESIDENT,
                LibraryMemoryBudget.chooseMode(150, 48L * 1024 * 1024, 63L * 1024 * 1024));
    }

    /** 2026-07-20 — System availMem soft floor (~24MB). */
    @Test
    public void systemAvailSoftFloorSegments() {
        assertEquals(LibraryMemoryBudget.Mode.SEGMENTED,
                LibraryMemoryBudget.chooseMode(150, 48L * 1024 * 1024, 64L * 1024 * 1024,
                        20L * 1024 * 1024));
        assertEquals(LibraryMemoryBudget.Mode.FULL_RESIDENT,
                LibraryMemoryBudget.chooseMode(150, 48L * 1024 * 1024, 64L * 1024 * 1024,
                        64L * 1024 * 1024));
    }
}
