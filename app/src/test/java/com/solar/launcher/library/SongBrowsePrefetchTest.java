package com.solar.launcher.library;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** 2026-07-20 — Prefetch block-id math for SEGMENTED song browse. */
public class SongBrowsePrefetchTest {

    @Test
    public void centerPlusMinusOne() {
        // blockSize 256: index 300 → block 1 → neighbors 0,1,2
        assertArrayEquals(new int[] {0, 1, 2},
                SongBrowsePrefetch.blocksAround(300, 256, 1000, 0));
    }

    @Test
    public void atStartNoNegative() {
        assertArrayEquals(new int[] {0, 1},
                SongBrowsePrefetch.blocksAround(0, 256, 500, 0));
    }

    @Test
    public void atEndNoPastMax() {
        // total 520 → max block 2 (512..519)
        assertArrayEquals(new int[] {1, 2},
                SongBrowsePrefetch.blocksAround(519, 256, 520, 0));
    }

    @Test
    public void extraNeighborWidens() {
        assertArrayEquals(new int[] {0, 1, 2, 3},
                SongBrowsePrefetch.blocksAround(300, 256, 2000, 1));
    }

    @Test
    public void emptyWhenNoTracks() {
        assertEquals(0, SongBrowsePrefetch.blocksAround(0, 256, 0, 0).length);
    }

    @Test
    public void playWindowCenteredTwoBlocks() {
        // index 300, block 256 → start 300-128=172; end 172+512=684
        assertEquals(172, SongBrowsePrefetch.playWindowStart(300, 256, 1000));
        assertEquals(684, SongBrowsePrefetch.playWindowEndExclusive(300, 256, 1000));
    }

    @Test
    public void playWindowClampsAtEnds() {
        assertEquals(0, SongBrowsePrefetch.playWindowStart(10, 256, 100));
        assertEquals(100, SongBrowsePrefetch.playWindowEndExclusive(10, 256, 100));
        assertEquals(0, SongBrowsePrefetch.playWindowStart(0, 256, 0));
        assertEquals(0, SongBrowsePrefetch.playWindowEndExclusive(0, 256, 0));
    }

    @Test
    public void extraNeighborNeedsModestFree() {
        org.junit.Assert.assertFalse(SongBrowsePrefetch.allowExtraNeighbor(1024L));
        org.junit.Assert.assertTrue(SongBrowsePrefetch.allowExtraNeighbor(
                SongBrowsePrefetch.EXTRA_NEIGHBOR_FREE_BYTES));
    }
}
