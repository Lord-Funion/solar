package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Solo leaf keying, sibling paths, artifact skip, prefer-cache resolve.
 * 2026-07-19
 */
public class LalalSoloCacheTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Stable leaf from basename|size. 2026-07-19 */
    @Test
    public void soloCacheLeafStable() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "jam.mp3");
        writeBytes(track, new byte[200]);
        String leaf = LalalClient.soloCacheLeaf(track);
        assertTrue(leaf.startsWith("v1_"));
        assertEquals(leaf, LalalClient.soloCacheLeaf(track));
    }

    /** stem_solo trees are library artifacts. 2026-07-19 */
    @Test
    public void soloTreeSkippedFromLibrary() throws Exception {
        File cache = tmp.newFolder("cache");
        File soloRoot = new File(new File(cache, "stem_solo"), "lalal");
        File leaf = new File(soloRoot, "v1_abc");
        assertTrue(leaf.mkdirs());
        File vocals = new File(leaf, "vocals.mp3");
        writeBytes(vocals, new byte[120]);
        assertTrue(LalalClient.isStemLibraryArtifact(soloRoot));
        assertTrue(LalalClient.isStemLibraryArtifact(vocals));
    }

    /** Sibling .instrumentals / .acapellas never enter the library. 2026-07-19 */
    @Test
    public void siblingFoldersSkippedFromLibrary() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "song.mp3");
        writeBytes(track, new byte[200]);
        File instrDir = new File(music, SoloStemPaths.DIR_INSTRUMENTALS);
        assertTrue(instrDir.mkdirs());
        File instr = new File(instrDir, "song.mp3");
        writeBytes(instr, new byte[150]);
        File acapDir = new File(music, SoloStemPaths.DIR_ACAPELLAS);
        assertTrue(acapDir.mkdirs());
        File acap = new File(acapDir, "song.mp3");
        writeBytes(acap, new byte[150]);
        assertTrue(LalalClient.isStemLibraryArtifact(instrDir));
        assertTrue(LalalClient.isStemLibraryArtifact(instr));
        assertTrue(LalalClient.isStemLibraryArtifact(acapDir));
        assertTrue(LalalClient.isStemLibraryArtifact(acap));
        assertFalse(LalalClient.isStemLibraryArtifact(track));
    }

    /** Sibling path helpers use same basename next to the track. 2026-07-19 */
    @Test
    public void siblingPathsMatchBasename() throws Exception {
        File album = tmp.newFolder("Album");
        File track = new File(album, "Cool Song.flac");
        writeBytes(track, new byte[300]);
        File instr = SoloStemPaths.siblingSoloFile(track, SoloMode.INSTRUMENTAL);
        File acap = SoloStemPaths.siblingSoloFile(track, SoloMode.ACAPELLA);
        assertNotNull(instr);
        assertNotNull(acap);
        assertEquals(new File(album, SoloStemPaths.DIR_INSTRUMENTALS), instr.getParentFile());
        assertEquals(new File(album, SoloStemPaths.DIR_ACAPELLAS), acap.getParentFile());
        assertEquals("Cool Song.mp3", instr.getName());
        assertEquals("Cool Song.mp3", acap.getName());
        assertEquals(track.getAbsolutePath(),
                SoloStemPaths.originatingTrackFromSolo(
                        writeSibling(track, SoloMode.INSTRUMENTAL)).getAbsolutePath());
    }

    /** findReadySoloFile prefers sibling over legacy stem_solo. 2026-07-19 */
    @Test
    public void findReadyPrefersSiblingThenSoloLeaf() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "song.mp3");
        writeBytes(track, new byte[300]);
        File cache = tmp.newFolder("cache");
        File solo = LalalClient.soloDir(cache, track);
        assertTrue(solo.mkdirs());
        File vocals = new File(solo, "vocals.mp3");
        File instr = new File(solo, "instrumental.mp3");
        writeBytes(vocals, new byte[150]);
        writeBytes(instr, new byte[150]);
        LalalClient.writeTrackMarker(solo, track);

        File hitAcap = LalalClient.findReadySoloFile(null, track, SoloMode.ACAPELLA, cache);
        File hitInstr = LalalClient.findReadySoloFile(null, track, SoloMode.INSTRUMENTAL, cache);
        assertNotNull(hitAcap);
        assertNotNull(hitInstr);
        assertEquals(vocals.getAbsolutePath(), hitAcap.getAbsolutePath());
        assertEquals(instr.getAbsolutePath(), hitInstr.getAbsolutePath());

        File siblingInstr = writeSibling(track, SoloMode.INSTRUMENTAL);
        File prefer = LalalClient.findReadySoloFile(null, track, SoloMode.INSTRUMENTAL, cache);
        assertEquals(siblingInstr.getAbsolutePath(), prefer.getAbsolutePath());

        assertNull(LalalClient.findReadySoloFile(null, track, SoloMode.ACAPELLA, tmp.newFolder("empty")));
    }

    /**
     * Full pads on disk → offline instrumental bake source even without baked instrumental.
     * 2026-07-19
     */
    @Test
    public void hasOfflineSoloSourceWhenPadsReady() throws Exception {
        File track = tmp.newFile("bakeable.mp3");
        writeBytes(track, new byte[220]);
        File cache = tmp.newFolder("cache_bake");
        assertFalse(LalalClient.hasOfflineSoloSource(null, track, SoloMode.INSTRUMENTAL, cache));
        File dir = LalalClient.stemCacheDir(cache, track, false);
        assertTrue(dir.mkdirs());
        writeBytes(new File(dir, "vocals.mp3"), new byte[120]);
        writeBytes(new File(dir, "drum.mp3"), new byte[120]);
        writeBytes(new File(dir, "bass.mp3"), new byte[120]);
        writeBytes(new File(dir, "melody.mp3"), new byte[120]);
        assertTrue(LalalClient.hasOfflineSoloSource(null, track, SoloMode.INSTRUMENTAL, cache));
        assertTrue(LalalClient.hasOfflineSoloSource(null, track, SoloMode.ACAPELLA, cache));
        // No baked sibling yet — findReadySoloFile instrumental may still be null. 2026-07-19
        assertNull(LalalClient.findReadySoloFile(null, track, SoloMode.INSTRUMENTAL, cache));
        assertNotNull(LalalClient.findReadySoloFile(null, track, SoloMode.ACAPELLA, cache));
    }

    private static File writeSibling(File track, SoloMode mode) throws Exception {
        SoloStemPaths.ensureSiblingDir(track, mode);
        File dest = SoloStemPaths.siblingSoloFile(track, mode);
        writeBytes(dest, new byte[160]);
        return dest;
    }

    private static void writeBytes(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
