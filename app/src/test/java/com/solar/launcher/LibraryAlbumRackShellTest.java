package com.solar.launcher;

import com.solar.launcher.flow.FlowItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — SEGMENTED Flow shells from Tier-0 DISTINCT titles (no SongRows).
 * Layman: empty libraryRows must still produce album covers for the carousel.
 */
public class LibraryAlbumRackShellTest {

    @Test
    public void buildShellsFromTitlesProducesSortedAlbums() {
        List<FlowItem> items = LibraryAlbumRack.buildShellsFromTitles(
                Arrays.asList("Zebra", "Alpha", "", "Unknown Album", null, "  Beta  "));
        assertEquals(3, items.size());
        assertEquals("Alpha", items.get(0).title);
        assertEquals("Beta", items.get(1).title);
        assertEquals("Zebra", items.get(2).title);
        assertTrue(items.get(0).tracks == null || items.get(0).tracks.isEmpty());
        assertTrue(items.get(0).matchKey != null && !items.get(0).matchKey.isEmpty());
    }

    @Test
    public void buildShellsEmptyInputYieldsEmptyCatalog() {
        assertTrue(LibraryAlbumRack.buildShellsFromTitles(null).isEmpty());
        assertTrue(LibraryAlbumRack.buildShellsFromTitles(Collections.<String>emptyList()).isEmpty());
    }

    /**
     * 2026-07-20 — Multi-track shell titles come from SQL HAVING, not rack filtering.
     * Layman: when “multi-track only” is on, callers pass the filtered name list in.
     */
    @Test
    public void buildShellsUsesCallerFilteredTitles() {
        List<FlowItem> all = LibraryAlbumRack.buildShellsFromTitles(
                Arrays.asList("Single", "Double"));
        assertEquals(2, all.size());
        List<FlowItem> multi = LibraryAlbumRack.buildShellsFromTitles(
                Arrays.asList("Double"));
        assertEquals(1, multi.size());
        assertEquals("Double", multi.get(0).title);
    }
}
