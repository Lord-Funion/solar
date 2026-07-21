package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Library now-playing path match.
 */
public class LibraryNowPlayingSlotTest {

    @Test
    public void matchesSamePath() {
        File a = new File("/storage/sdcard0/Music/a.mp3");
        assertTrue(LibraryNowPlayingSlot.isNowPlayingRow(true, a, new File("/storage/sdcard0/Music/a.mp3")));
        assertFalse(LibraryNowPlayingSlot.isNowPlayingRow(true, a, new File("/storage/sdcard0/Music/b.mp3")));
        assertFalse(LibraryNowPlayingSlot.isNowPlayingRow(false, a, a));
    }

    @Test
    public void soloOriginMarksLibraryRow() {
        File origin = new File("/Music/song.mp3");
        File stem = new File("/Music/.instrumentals/song.mp3");
        assertTrue(LibraryNowPlayingSlot.isNowPlayingRowOrSoloOrigin(true, stem, origin, origin));
        assertTrue(LibraryNowPlayingSlot.isNowPlayingRowOrSoloOrigin(true, stem, stem, origin));
        assertFalse(LibraryNowPlayingSlot.isNowPlayingRowOrSoloOrigin(true, stem,
                new File("/Music/other.mp3"), origin));
    }
}
