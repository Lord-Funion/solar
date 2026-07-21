package com.solar.launcher.library;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** 2026-07-20 — LRU segment pages + trimTo for SEGMENTED / MemoryRelease. */
public class LibrarySegmentCacheTest {

    @Test
    public void trimToEvictsEldest() {
        LibrarySegmentCache<String> c = new LibrarySegmentCache<String>(4, 4);
        c.putBlock(0, Collections.singletonList("a"));
        c.putBlock(1, Collections.singletonList("b"));
        c.putBlock(2, Collections.singletonList("c"));
        c.trimTo(2);
        assertEquals(2, c.cachedBlockCount());
        // Access-order eldest was 0 after puts 0,1,2 — trim removes 0 then stops at keep=2.
        assertNull(c.getBlock(0));
        c.trimTo(0);
        assertEquals(0, c.cachedBlockCount());
    }

    @Test
    public void selfCheckPasses() {
        LibrarySegmentCache.selfCheck();
    }
}
