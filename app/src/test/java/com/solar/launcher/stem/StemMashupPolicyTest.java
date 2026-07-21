package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tempo match + pad-local loop + bass body + SoundTouch hooks. 2026-07-19
 */
public class StemMashupPolicyTest {

    @Test
    public void tempoMasterIsOne() {
        assertEquals(1f, StemTempoSync.rateForSong(120f, 100f, 0), 0.001f);
    }

    @Test
    public void tempoNeedsSoundTouchWhenFar() {
        float r = StemTempoSync.rateForSong(120f, 100f, 1);
        assertTrue(r > 1f);
        assertTrue(StemTempoSync.needsSoundTouch(r));
    }

    @Test
    public void tempoNearMatchIsUnity() {
        float r = StemTempoSync.rateForSong(120f, 121f, 1);
        assertEquals(1f, r, 0.001f);
        assertFalse(StemTempoSync.needsSoundTouch(r));
    }

    @Test
    public void padLocalLoopSkipsFreeRunAndStutter() {
        assertTrue(StemMixer.shouldSeekZoneOnLoopWrap(true, true, false));
        assertFalse(StemMixer.shouldSeekZoneOnLoopWrap(true, false, false));
        assertFalse(StemMixer.shouldSeekZoneOnLoopWrap(true, true, true));
        assertFalse(StemMixer.shouldSeekZoneOnLoopWrap(false, true, false));
    }

    @Test
    public void bassBodyGainK() {
        assertEquals(0.7f, StemBassBody.BODY_GAIN_K, 0.001f);
    }

    /** Cold jam: tempoRate defaults 1; chop screw separate. 2026-07-20 */
    @Test
    public void songStateTempoVsScrew() {
        StemSession.SongState st = new StemSession.SongState();
        st.resetJam();
        assertEquals(1f, st.tempoRate, 0.001f);
        assertEquals(1f, st.screwRate, 0.001f);
        assertFalse(st.chopOn);
        st.tempoRate = 1.1f;
        st.screwRate = 0.7f;
        assertEquals(0.77f, StemTempoSync.composePadRate(st.tempoRate, st.screwRate), 0.01f);
    }

    @Test
    public void soundTouchFlag() {
        assertTrue(StemSoundTouch.isSoundTouchEnabled(1L));
        assertFalse(StemSoundTouch.isSoundTouchEnabled(0L));
    }
}
