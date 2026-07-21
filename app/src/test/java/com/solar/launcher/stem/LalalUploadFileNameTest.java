package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * 2026-07-20 — Content-Disposition upload names must not carry apostrophes/quotes.
 * Layman: Don't Stop.mp3 must upload without “unexpected char” from the cloud API.
 */
public class LalalUploadFileNameTest {

    @Test
    public void apostropheBecomesUnderscore() {
        assertEquals("Don_t_Stop.mp3",
                LalalClient.uploadFileNameForHeader("Don't Stop.mp3"));
        // 2026-07-20 — Curly ’ (U+2019) in Halley's-style titles.
        assertEquals("Halley_s_Comet.mp3",
                LalalClient.uploadFileNameForHeader("Halley’s Comet.mp3"));
    }

    @Test
    public void quotesAndPathStripped() {
        assertEquals("Track_Name.mp3",
                LalalClient.uploadFileNameForHeader("/Music/\"Track\" Name.mp3"));
        assertFalse(LalalClient.uploadFileNameForHeader("a'b\"c.mp3").contains("'"));
        assertFalse(LalalClient.uploadFileNameForHeader("a'b\"c.mp3").contains("\""));
    }

    @Test
    public void emptyFallsBack() {
        assertEquals("track.mp3", LalalClient.uploadFileNameForHeader(null));
        assertEquals("track.mp3", LalalClient.uploadFileNameForHeader(""));
        assertEquals("track.mp3", LalalClient.uploadFileNameForHeader("'''"));
    }

    @Test
    public void plainAsciiUnchanged() {
        assertEquals("Song_Title-01.mp3",
                LalalClient.uploadFileNameForHeader("Song_Title-01.mp3"));
    }
}
