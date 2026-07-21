package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — YouTube Play cache must use IJK like Music/YouTube saves.
 * Layman: after “Saving 100%”, Now Playing must not hit stock MediaPlayer “Load Failed”.
 * Reversal: delete this test if prefersIjkLocalDecode drops youtube_play again.
 */
public class PrefersIjkLocalDecodeTest {

    @Test
    public void playCacheM4aPrefersIjk() {
        File f = new File("/data/data/com.solar.launcher/cache/youtube_play/id_Song.m4a");
        assertTrue(MainActivity.prefersIjkLocalDecode(f));
    }

    @Test
    public void libraryYoutubeM4aPrefersIjk() {
        File f = new File("/storage/sdcard0/Music/YouTube/Song.m4a");
        assertTrue(MainActivity.prefersIjkLocalDecode(f));
    }

    @Test
    public void plainMp3UsesStockPath() {
        File f = new File("/storage/sdcard0/Music/Artist/Song.mp3");
        assertFalse(MainActivity.prefersIjkLocalDecode(f));
    }

    @Test
    public void opusAlwaysPrefersIjk() {
        File f = new File("/storage/sdcard0/Music/Artist/Song.opus");
        assertTrue(MainActivity.prefersIjkLocalDecode(f));
    }
}
