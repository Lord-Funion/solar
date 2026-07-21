package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Arch geometry for Stem mashup track labels around the pad cluster.
 * 2026-07-21
 */
public class StemPadArchLabelTest {

    /** Radius sits outside pad reach but inside the face. 2026-07-21 */
    @Test
    public void archRadiusClearsPads() {
        float r = StemPadArchLabel.archRadius(360f, 100f);
        assertTrue(r > 100f);
        assertTrue(r < 360f * 0.5f);
    }

    /** Sweep and arc length stay positive and calm. 2026-07-21 */
    @Test
    public void sweepAndArcLength() {
        assertEquals(118f, StemPadArchLabel.archSweepDeg(), 0.01f);
        float len = StemPadArchLabel.arcLengthPx(120f, 118f);
        assertTrue(len > 100f);
        assertTrue(StemPadArchLabel.titleTextSize(360f) >= 10f);
        assertTrue(StemPadArchLabel.artistTextSize(12f) < 12f);
    }
}
