package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;

/** Lossless warn gate — warn never block. 2026-07-21 */
public class StemMixLosslessTest {

    @Test
    public void detectsLosslessExtensions() {
        assertTrue(StemMixLossless.isLosslessName("song.flac"));
        assertTrue(StemMixLossless.isLosslessName("Song.WAV"));
        assertTrue(StemMixLossless.isLosslessName("x.aiff"));
        assertFalse(StemMixLossless.isLosslessName("song.mp3"));
        assertFalse(StemMixLossless.isLosslessName("song.m4a"));
    }

    @Test
    public void warnSkippedWhenStemsReady() {
        File flac = new File("/tmp/x.flac");
        assertFalse(StemMixLossless.shouldWarnLossless(flac, true));
        assertTrue(StemMixLossless.shouldWarnLossless(flac, false));
    }

    @Test
    public void batchWarnAnyLosslessNeedingCook() {
        File[] files = new File[] { new File("a.mp3"), new File("b.flac") };
        boolean[] ready = new boolean[] { false, false };
        assertTrue(StemMixLossless.shouldWarnLosslessBatch(files, ready));
        ready[1] = true;
        assertFalse(StemMixLossless.shouldWarnLosslessBatch(files, ready));
    }

    @Test
    public void copyLabelsPresent() {
        assertTrue(StemMixLossless.warnBody().length() > 10);
        assertEquals("Continue anyway", StemMixLossless.continueLabel());
        assertEquals("Cancel", StemMixLossless.cancelLabel());
    }
}
