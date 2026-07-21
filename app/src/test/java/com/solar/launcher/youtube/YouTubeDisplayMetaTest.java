package com.solar.launcher.youtube;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-19 — Catalog title must win over garbage MediaMetadataRetriever tags on YouTube Play.
 */
public class YouTubeDisplayMetaTest {

    @Test
    public void rejectsFailedAndEmptyTagTitles() {
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle(null));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle(""));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle("   "));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle("Failed"));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle("failed"));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle("Load Failed: foo.m4a"));
        assertFalse(YouTubeDisplayMeta.isUsableTagTitle("Unknown"));
        assertTrue(YouTubeDisplayMeta.isUsableTagTitle("Hello"));
    }

    @Test
    public void pickTitlePrefersCatalogOverFailedTag() {
        assertEquals("Adele - Hello",
                YouTubeDisplayMeta.pickTitle("Adele - Hello", "Failed", "id_x.m4a"));
        assertEquals("Adele - Hello",
                YouTubeDisplayMeta.pickTitle("Adele - Hello", "", "id_x.m4a"));
        assertEquals("Hello",
                YouTubeDisplayMeta.pickTitle("", "Hello", "id_x.m4a"));
    }

    @Test
    public void pickArtistPrefersCatalogAuthor() {
        assertEquals("Adele",
                YouTubeDisplayMeta.pickArtist("Adele", "Unknown Artist"));
        assertEquals("Adele",
                YouTubeDisplayMeta.pickArtist("Adele", "Failed"));
        assertEquals("Someone",
                YouTubeDisplayMeta.pickArtist("", "Someone"));
    }
}
