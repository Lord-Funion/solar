package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/**
 * Dual-layer gain / fade math for NP Instrumentals/Vocals toggles.
 * 2026-07-20 / 2026-07-21
 */
public class SoloLayerGainsTest {

    @Test
    public void targetGainOnOff() {
        assertEquals(1f, SoloLayerGains.targetGain(true), 0.0001f);
        assertEquals(0f, SoloLayerGains.targetGain(false), 0.0001f);
    }

    @Test
    public void modeForLayers() {
        assertNull(SoloLayerGains.modeForLayers(true, true, SoloMode.INSTRUMENTAL));
        assertEquals(SoloMode.INSTRUMENTAL,
                SoloLayerGains.modeForLayers(false, true, null));
        assertEquals(SoloMode.ACAPELLA,
                SoloLayerGains.modeForLayers(true, false, null));
        // Never both off — keep previous. 2026-07-20
        assertEquals(SoloMode.INSTRUMENTAL,
                SoloLayerGains.modeForLayers(false, false, SoloMode.INSTRUMENTAL));
        assertEquals(SoloMode.ACAPELLA,
                SoloLayerGains.modeForLayers(false, false, SoloMode.ACAPELLA));
        assertNull(SoloLayerGains.modeForLayers(false, false, null));
    }

    @Test
    public void isActiveStemSiblingMatchesEitherStemPath() {
        File vocals = new File("/cache/song.vocals.mp3");
        File instr = new File("/cache/song.instr.mp3");
        File original = new File("/Music/song.mp3");
        assertTrue(SoloLayerGains.isActiveStemSibling(vocals, vocals, instr));
        assertTrue(SoloLayerGains.isActiveStemSibling(instr, vocals, instr));
        assertFalse(SoloLayerGains.isActiveStemSibling(original, vocals, instr));
        assertFalse(SoloLayerGains.isActiveStemSibling(null, vocals, instr));
        assertFalse(SoloLayerGains.isActiveStemSibling(vocals, null, null));
    }

    @Test
    public void stepTowardReachesTarget() {
        float g = 1f;
        int guard = 0;
        while (!SoloLayerGains.fadeDone(g, 0f) && guard++ < 100) {
            g = SoloLayerGains.stepToward(g, 0f);
        }
        assertTrue(SoloLayerGains.fadeDone(g, 0f));
        assertTrue(g <= StemControls.SILENT_GAIN);
    }

    @Test
    public void fadeDoneAtSame() {
        assertTrue(SoloLayerGains.fadeDone(1f, 1f));
        assertFalse(SoloLayerGains.fadeDone(1f, 0f));
    }
}
