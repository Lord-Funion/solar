package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * 2026-07-20 — Song-row duration subtitle glue (Length sort helpers live on MusicLibraryStore).
 */
public class LibrarySongDurationTest {

    /** Appends mm:ss after artist/album; empty duration leaves base alone. */
    @Test
    public void appendDurationPreservesCredits() {
        MainActivity.SongItem song = new MainActivity.SongItem(
                new File("/music/a.mp3"), "Title", "Artist", "Album", "Rock");
        song.durationMs = "125000";
        assertEquals("Artist · Album · 2:05",
                MainActivity.appendSongDurationSubtitle("Artist · Album", song));
        assertEquals("2:05", MainActivity.appendSongDurationSubtitle("", song));
        song.durationMs = "";
        assertEquals("Artist · Album",
                MainActivity.appendSongDurationSubtitle("Artist · Album", song));
    }

    /** Length mode orders short before long; unknown duration after known. */
    @Test
    public void lengthComparatorShortestFirst() {
        assertEquals(-1, MusicLibraryStore.compareDurationAscending("30000", "90000"));
        assertEquals(1, MusicLibraryStore.compareDurationAscending("", "30000"));
        assertEquals(0, MusicLibraryStore.compareDurationAscending("45000", "45000"));
    }
}
