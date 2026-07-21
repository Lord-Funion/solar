package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-19 — Hierarchy Search… header index math for ListView adapters.
 */
public class HierarchySearchChromeTest {

    @Test
    public void countWithHeader_addsOne() {
        assertEquals(1, HierarchySearchChrome.countWithHeader(0));
        assertEquals(11, HierarchySearchChrome.countWithHeader(10));
    }

    @Test
    public void dataIndex_mapsPastHeader() {
        assertEquals(-1, HierarchySearchChrome.dataIndex(0));
        assertEquals(0, HierarchySearchChrome.dataIndex(1));
        assertEquals(4, HierarchySearchChrome.dataIndex(5));
    }

    @Test
    public void isSearchPosition_onlyZero() {
        assertTrue(HierarchySearchChrome.isSearchPosition(0));
        assertFalse(HierarchySearchChrome.isSearchPosition(1));
    }

    /**
     * 2026-07-20 — Context Play Instrumental used raw list pos → track below selection.
     * Layman: Search at top means list row 5 is song #4 in the file array.
     */
    @Test
    public void dataIndex_focusedSongNotNeighbor() {
        // List positions with Search… header: 0=Search, 1=song0, 2=song1, 3=song2
        assertEquals(0, HierarchySearchChrome.dataIndex(1));
        assertEquals(1, HierarchySearchChrome.dataIndex(2));
        assertEquals(2, HierarchySearchChrome.dataIndex(3));
        // Bug was: treating list pos 2 as virtualSongList[2] (song below).
        assertFalse(HierarchySearchChrome.dataIndex(2) == 2);
    }

    /**
     * 2026-07-20 — Restore category focus after drill-down must land on the same artist/album.
     * Was: setSelection(dataIndex) highlighted the row above (Search shifts everything +1).
     */
    @Test
    public void adapterPosition_roundTripsWithDataIndex() {
        assertEquals(1, HierarchySearchChrome.adapterPosition(0));
        assertEquals(5, HierarchySearchChrome.adapterPosition(4));
        assertEquals(4, HierarchySearchChrome.dataIndex(
                HierarchySearchChrome.adapterPosition(4)));
    }

    /** 2026-07-20 — Detect Search chrome from adapter vs data sizes (category or song lists). */
    @Test
    public void hasHeader_whenAdapterIsOneLonger() {
        assertTrue(HierarchySearchChrome.hasHeader(11, 10));
        assertTrue(HierarchySearchChrome.hasHeader(1, 0));
        assertFalse(HierarchySearchChrome.hasHeader(10, 10));
        assertFalse(HierarchySearchChrome.hasHeader(9, 10));
    }

    /**
     * 2026-07-20 — Map focus for context menus: Search → no item; song row → data index.
     */
    @Test
    public void dataIndexOrNeg_searchYieldsNegOne() {
        assertEquals(-1, HierarchySearchChrome.dataIndexOrNeg(0, true));
        assertEquals(0, HierarchySearchChrome.dataIndexOrNeg(1, true));
        assertEquals(2, HierarchySearchChrome.dataIndexOrNeg(2, false));
    }

    /**
     * 2026-07-20 — List focus → song-data index when Search… may sit at row 0.
     */
    @Test
    public void songDataIndex_skipsSearchHeader() {
        assertEquals(-1, HierarchySearchChrome.songDataIndex(0, 10, 11));
        assertEquals(0, HierarchySearchChrome.songDataIndex(1, 10, 11));
        assertEquals(4, HierarchySearchChrome.songDataIndex(5, 10, 11));
        // Playlist / no header: 1:1
        assertEquals(2, HierarchySearchChrome.songDataIndex(2, 10, 10));
    }
}
