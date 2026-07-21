package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Select all / Clear all header offsets for Stem+Mix pick lists.
 * 2026-07-21 Stems/Mix sanity
 */
public class StemMixPickChromeTest {

    @Test
    public void headerPositionsAndDataIndex() {
        assertTrue(StemMixPickChrome.isSelectAllPosition(0));
        assertTrue(StemMixPickChrome.isClearAllPosition(1));
        assertTrue(StemMixPickChrome.isHeaderPosition(0));
        assertFalse(StemMixPickChrome.isHeaderPosition(2));
        assertEquals(-1, StemMixPickChrome.dataIndex(0));
        assertEquals(-1, StemMixPickChrome.dataIndex(1));
        assertEquals(0, StemMixPickChrome.dataIndex(2));
        assertEquals(4, StemMixPickChrome.dataIndex(6));
        assertEquals(12, StemMixPickChrome.countWithHeader(10));
        assertTrue(StemMixPickChrome.hasHeader(12, 10));
        assertEquals(0, StemMixPickChrome.songDataIndex(2, 10, 12));
        assertEquals(-1, StemMixPickChrome.songDataIndex(0, 10, 12));
        assertEquals(5, StemMixPickChrome.adapterPosition(3));
    }
}
