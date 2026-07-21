package com.solar.launcher.stem;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * NP Stems unify — gains, persist gates, fail cleanup, swap position match.
 * 2026-07-21
 */
public class NpStemUnifyPolicyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Stems off hides layer rows; on shows them.
     * 2026-07-21
     */
    @Test
    public void masterShowsLayersOnlyWhenOn() {
        assertFalse(NpStemMasterPolicy.showLayerToggles(false));
        assertTrue(NpStemMasterPolicy.showLayerToggles(true));
        assertTrue(NpStemMasterPolicy.playOriginFile(false));
        assertFalse(NpStemMasterPolicy.playOriginFile(true));
        assertTrue(NpStemMasterPolicy.needsCook(true, false));
        assertFalse(NpStemMasterPolicy.needsCook(true, true));
        assertTrue(NpStemMasterPolicy.playPadMix(true, true));
    }

    /**
     * Never both Instrumentals and Vocals off while Stems on.
     * 2026-07-21
     */
    @Test
    public void neverBothLayersOff() {
        assertFalse(NpStemMasterPolicy.allowLayerToggle(true, false, false));
        assertTrue(NpStemMasterPolicy.allowLayerToggle(true, true, false));
        assertTrue(NpStemMasterPolicy.allowLayerToggle(true, false, true));
        boolean[] clamped = NpStemMasterPolicy.clampLayers(true, false, false, true, false);
        assertTrue(clamped[0]);
        assertFalse(clamped[1]);
    }

    /**
     * Both layers on → all four pads full (whole song with Melody catch-all).
     * 2026-07-21
     */
    @Test
    public void padGainsFullSongWhenBothOn() {
        float[] t = NpStemPadGains.targets(true, true);
        assertArrayEquals(new float[] {1f, 1f, 1f, 1f}, t, 0.0001f);
        assertTrue(NpStemPadGains.isFullSongMix(true, true));
        float[] instrOnly = NpStemPadGains.targets(false, true);
        assertEquals(0f, instrOnly[0], 0.0001f);
        assertEquals(1f, instrOnly[1], 0.0001f);
        assertEquals(1f, instrOnly[3], 0.0001f);
        float[] vocalsOnly = NpStemPadGains.targets(true, false);
        assertEquals(1f, vocalsOnly[0], 0.0001f);
        assertEquals(0f, vocalsOnly[2], 0.0001f);
    }

    /**
     * Magical swap clamps playhead and matches within tolerance.
     * 2026-07-21
     */
    @Test
    public void swapPositionMatch() {
        assertEquals(1000, StemMixerSwapPolicy.matchedPositionMs(1000, 5000));
        assertEquals(0, StemMixerSwapPolicy.matchedPositionMs(-5, 5000));
        assertEquals(4950, StemMixerSwapPolicy.matchedPositionMs(9999, 5000));
        assertTrue(StemMixerSwapPolicy.positionsMatch(100, 150,
                StemMixerSwapPolicy.defaultToleranceMs()));
        assertFalse(StemMixerSwapPolicy.positionsMatch(100, 300, 80));
        float[] padIn = StemMixerSwapPolicy.padGainsAtSwapStart(true, true, true);
        assertArrayEquals(new float[] {0f, 0f, 0f, 0f}, padIn, 0.0001f);
        float[] padEnd = StemMixerSwapPolicy.padGainsAtSwapEnd(true, true, false);
        assertEquals(1f, padEnd[0], 0.0001f);
        assertEquals(0f, padEnd[1], 0.0001f);
    }

    /**
     * Library auto-persist; stream temps must prompt; fail cleanup deletes work.
     * 2026-07-21
     */
    @Test
    public void persistGatesAndFailCleanup() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "song.mp3");
        writeBytes(track, 200);
        File cache = tmp.newFolder("cache");
        assertTrue(NpStemPersistGate.isLibraryTrack(track, music, cache));
        assertFalse(NpStemPersistGate.mustPromptBeforeCook(track, music, cache));
        assertTrue(NpStemPersistGate.allowPersistAfterCook(true, false));
        assertTrue(NpStemPersistGate.allowPersistAfterCook(false, true));
        assertFalse(NpStemPersistGate.allowPersistAfterCook(false, false));
        assertTrue(NpStemPersistGate.useMultistemWithMelodyPremix());

        File reach = new File(new File(cache, "reach"), "temp.mp3");
        // mkdirs may return false if parent already exists — only care that path is writable. 2026-07-21
        if (!reach.getParentFile().isDirectory()) {
            assertTrue(reach.getParentFile().mkdirs());
        }
        writeBytes(reach, 200);
        assertTrue(NpStemPersistGate.mustPromptBeforeCook(reach, music, cache));

        File work = tmp.newFolder("lalal_work_fail");
        writeBytes(new File(work, "vocals.mp3"), 50);
        assertTrue(NpStemPersistGate.cleanupFailedWork(work));
        assertFalse(work.isDirectory());
    }

    /**
     * Melody catch-all forces premix for NP; single part skips blend.
     * 2026-07-21
     */
    @Test
    public void melodyCatchAllPrefersPremix() throws Exception {
        assertTrue(NpStemMelodyCatchAll.forcePremixForNp());
        File dir = tmp.newFolder("stems");
        File v = new File(dir, "vocals.mp3");
        File d = new File(dir, "drum.mp3");
        File b = new File(dir, "bass.mp3");
        File mel = new File(dir, StemOtherPremix.MELODY_WAV);
        writeBytes(v, 200);
        writeBytes(d, 200);
        writeBytes(b, 200);
        writeBytes(mel, 200);
        java.util.List<LalalClient.StemFile> raw = new java.util.ArrayList<LalalClient.StemFile>();
        raw.add(new LalalClient.StemFile("vocals", "Vocals", v, 0));
        raw.add(new LalalClient.StemFile("drum", "Drums", d, 1));
        raw.add(new LalalClient.StemFile("bass", "Bass", b, 2));
        raw.add(new LalalClient.StemFile("melody", "Melody", mel, 3));
        assertTrue(NpStemMelodyCatchAll.hasPremixedMelody(raw));
        assertEquals(1, NpStemMelodyCatchAll.melodyPartCount(raw));
        java.util.List<LalalClient.StemFile> out =
                NpStemMelodyCatchAll.padsForPlayback(raw, dir);
        assertEquals(4, out.size());
    }

    private static void writeBytes(File f, int n) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(new byte[n]);
        } finally {
            out.close();
        }
    }
}
