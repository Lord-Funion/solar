package com.solar.launcher.stem;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Fast solo presence for context-menu gating (no stem-root scan).
 */
public class LalalSoloFastReadyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void findReadySoloFileFast_hitsSibling() throws Exception {
        File track = tmp.newFile("Song.mp3");
        writeBytes(track, 200);
        File acapDir = new File(track.getParentFile(), SoloStemPaths.DIR_ACAPELLAS);
        assertTrue(acapDir.mkdirs());
        File acap = new File(acapDir, "Song.mp3");
        writeBytes(acap, 200);
        File hit = LalalClient.findReadySoloFileFast(track, SoloMode.ACAPELLA, tmp.getRoot());
        assertNotNull(hit);
        assertTrue(hit.getAbsolutePath().contains(SoloStemPaths.DIR_ACAPELLAS));
    }

    @Test
    public void findReadySoloFileFast_missWithoutWalk() throws Exception {
        File track = tmp.newFile("Bare.mp3");
        writeBytes(track, 200);
        assertNull(LalalClient.findReadySoloFileFast(track, SoloMode.INSTRUMENTAL, tmp.getRoot()));
        assertFalse(LalalClient.hasOfflineSoloSourceFast(track, SoloMode.INSTRUMENTAL, tmp.getRoot()));
    }

    private static void writeBytes(File f, int n) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(new byte[n]);
        out.close();
    }
}
