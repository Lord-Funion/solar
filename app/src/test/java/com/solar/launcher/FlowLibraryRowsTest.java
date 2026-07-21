package com.solar.launcher;

import com.solar.launcher.flow.FlowCatalog;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

public class FlowLibraryRowsTest {

    @Test
    public void cacheReusesRowsUntilGenChanges() {
        FlowLibraryRows cache = new FlowLibraryRows();
        List<MainActivity.SongItem> lib = new ArrayList<MainActivity.SongItem>();
        lib.add(new MainActivity.SongItem(new File("/a.mp3"), "A", "B", "C", "Rock", "", 1, 1999));
        List<FlowCatalog.SongRow> first = cache.rows(lib, 1);
        List<FlowCatalog.SongRow> second = cache.rows(lib, 1);
        assertSame(first, second);
        cache.invalidate();
        List<FlowCatalog.SongRow> third = cache.rows(lib, 2);
        assertEquals(1, third.size());
        assertEquals(1999, third.get(0).year);
    }

    /**
     * 2026-07-20 — SEGMENTED Flow uses replace/peekWarm so catalog build isn’t stuck on emptyList.
     * Layman: after a background DB load, Flow must see the same song list until the gen changes.
     */
    @Test
    public void replaceAndPeekWarmSurviveUntilInvalidate() {
        FlowLibraryRows cache = new FlowLibraryRows();
        List<FlowCatalog.SongRow> seeded = new ArrayList<FlowCatalog.SongRow>();
        seeded.add(new FlowCatalog.SongRow(new File("/b.mp3"), "T", "Ar", "Al", "", 1L, 1, "Rock", 2001));
        List<FlowCatalog.SongRow> installed = cache.replace(seeded, 7);
        assertEquals(1, installed.size());
        assertSame(installed, cache.peekWarm(7));
        assertEquals(null, cache.peekWarm(8));
        cache.invalidate();
        assertEquals(null, cache.peekWarm(7));
    }
}
