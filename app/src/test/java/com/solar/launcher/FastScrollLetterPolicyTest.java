package com.solar.launcher;

import com.solar.launcher.media.MediaSuiteHost;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 2026-07-15 — Letter HUD / section-jump gate: alpha name-sorted catalogs only.
 */
public class FastScrollLetterPolicyTest {

    private static boolean eligible(
            int screen, int browser, String query,
            int artistSort, int songSort, int albumRack, int albumSong,
            boolean themes, boolean server, boolean media) {
        return FastScrollLetterPolicy.isEligible(
                screen, browser, query, artistSort, songSort, albumRack, albumSong,
                themes, server, media);
    }

    @Test
    public void deniesHomeSettingsMoreAndLibraryRoot() {
        assertFalse(eligible(FastScrollLetterPolicy.STATE_MENU, 0, "", 0, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_MORE, 0, "", 0, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_SETTINGS, 0, "", 0, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ROOT, "", 0, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_PLAYLISTS, "", 0, 0, 0, 0, false, false, false));
    }

    @Test
    public void allowsThemeGalleryUnderSettings() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_SETTINGS, 0, "", 0, 0, 0, 0, true, false, false));
    }

    @Test
    public void artistsOnlyWhenNameSorted() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ARTISTS, "",
                LibraryBrowsePrefs.ARTIST_SORT_NAME, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ARTISTS, "",
                LibraryBrowsePrefs.ARTIST_SORT_TRACK_COUNT, 0, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ARTISTS, "",
                LibraryBrowsePrefs.ARTIST_SORT_RECENT, 0, 0, 0, false, false, false));
    }

    @Test
    public void albumRackOnlyWhenTitleSorted() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ALBUMS, "",
                0, 0, LibraryBrowsePrefs.ALBUM_RACK_SORT_TITLE, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ALBUMS, "",
                0, 0, LibraryBrowsePrefs.ALBUM_RACK_SORT_ARTIST_THEN_TITLE, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ALBUMS, "",
                0, 0, LibraryBrowsePrefs.ALBUM_RACK_SORT_RECENT, 0, false, false, false));
    }

    @Test
    public void fixedAlphaCatalogsAllowed() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_FOLDER, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_GENRES, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_YEARS, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_ARTIST_ALBUMS, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_FAVORITES, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_LIBRARY_SEARCH, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_APPS, 0, "", 0, 0, 0, 0, false, false, false));
    }

    @Test
    public void allSongsOnlyWhenTitleSorted() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALL",
                0, LibraryBrowsePrefs.SONG_SORT_TITLE, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALL",
                0, LibraryBrowsePrefs.SONG_SORT_ARTIST, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALL",
                0, LibraryBrowsePrefs.SONG_SORT_DATE, 0, 0, false, false, false));
        // 2026-07-20 — Length sort is not A–Z letter-indexable.
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALL",
                0, LibraryBrowsePrefs.SONG_SORT_LENGTH, 0, 0, false, false, false));
    }

    @Test
    public void playlistAndRecentDenied() {
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "PLAYLIST",
                0, LibraryBrowsePrefs.SONG_SORT_TITLE, 0, 0, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "RECENT",
                0, LibraryBrowsePrefs.SONG_SORT_TITLE, 0, 0, false, false, false));
    }

    @Test
    public void favoritesVirtualAlwaysAllowed() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "FAVORITES",
                0, LibraryBrowsePrefs.SONG_SORT_DATE, 0, 0, false, false, false));
    }

    @Test
    public void albumTracksOnlyWhenTitleSorted() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALBUM",
                0, 0, 0, LibraryBrowsePrefs.SONG_SORT_TITLE, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ALBUM",
                0, 0, 0, LibraryBrowsePrefs.SONG_SORT_ALBUM, false, false, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_BROWSER,
                FastScrollLetterPolicy.BROWSER_VIRTUAL_SONGS, "ARTIST_ALBUM",
                0, 0, 0, LibraryBrowsePrefs.SONG_SORT_ALBUM, false, false, false));
    }

    @Test
    public void serverAndMediaFlags() {
        assertTrue(eligible(FastScrollLetterPolicy.STATE_NAVIDROME, 0, "", 0, 0, 0, 0, false, true, false));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_NAVIDROME, 0, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(FastScrollLetterPolicy.STATE_PODCASTS, 0, "", 0, 0, 0, 0, false, false, true));
        assertFalse(eligible(FastScrollLetterPolicy.STATE_PODCASTS, 0, "", 0, 0, 0, 0, false, false, false));
        assertTrue(eligible(MediaSuiteHost.STATE_VIDEOS, 0, "", 0, 0, 0, 0, false, false, true));
    }

    @Test
    public void virtualSongsHelperMatchesMatrix() {
        assertTrue(FastScrollLetterPolicy.isVirtualSongsEligible(
                "ALL", LibraryBrowsePrefs.SONG_SORT_TITLE, LibraryBrowsePrefs.SONG_SORT_ALBUM));
        assertFalse(FastScrollLetterPolicy.isVirtualSongsEligible(
                "PLAYLIST", LibraryBrowsePrefs.SONG_SORT_TITLE, LibraryBrowsePrefs.SONG_SORT_TITLE));
        assertTrue(FastScrollLetterPolicy.isVirtualSongsEligible(
                "FAVORITES", LibraryBrowsePrefs.SONG_SORT_DATE, LibraryBrowsePrefs.SONG_SORT_ALBUM));
    }
}
