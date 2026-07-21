package com.solar.launcher.stem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tempo rate clamp for Mix/Stem beat sync. 2026-07-19
 */
public class StemTempoSyncTest {

    @Test
    public void masterAlwaysOne() {
        assertEquals(1f, StemTempoSync.rateForSong(120f, 100f, 0), 0.001f);
    }

    @Test
    public void slaveMatchesWithinClamp() {
        float r = StemTempoSync.rateForSong(120f, 100f, 1);
        assertTrue(r > 1f);
        assertTrue(r <= StemBpm.MAX_RATE);
        assertTrue(StemTempoSync.needsSoundTouch(r));
    }

    @Test
    public void tinyDeltaIsUnity() {
        float r = StemTempoSync.rateForSong(120f, 120.5f, 1);
        assertEquals(1f, r, 0.001f);
        assertFalse(StemTempoSync.needsSoundTouch(r));
    }

    /** Tempo bus × pad screw — Houston feel without dropping match. 2026-07-20 */
    @Test
    public void composePadRateMultiplies() {
        assertEquals(0.77f, StemTempoSync.composePadRate(1.1f, 0.7f), 0.01f);
        assertEquals(0.5f, StemTempoSync.composePadRate(1f, 0.4f), 0.001f);
    }

    /** Slave media clock scales with tempoRate under IJK setSpeed. 2026-07-20 */
    @Test
    public void expectedSlavePosScales() {
        assertEquals(1100, StemTempoSync.expectedSlavePosMs(1000, 1.1f));
        assertEquals(1000, StemTempoSync.expectedSlavePosMs(1000, 1f));
    }
}
