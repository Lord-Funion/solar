package com.solar.launcher.stem;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Has Stems inverted index — marker + ready pads map to library paths.
 * 2026-07-19/20
 */
public class LalalHasStemsIndexTest {

    @Test
    public void indexReadyOriginatingPaths_matchesMarkerAndSidecar() throws Exception {
        File dir = File.createTempFile("hasstems", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        File track = new File(dir, "Song.mp3");
        writeBytes(track, new byte[1200]);

        File sidecar = new File(dir, "Song.stems");
        if (!sidecar.mkdir()) throw new AssertionError("sidecar");
        // Minimal ready pads for cacheReadyFlexible. 2026-07-19
        writeBytes(new File(sidecar, "vocals.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "drums.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "bass.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "melody.mp3"), new byte[200]);

        ArrayList<File> lib = new ArrayList<File>();
        lib.add(track);
        HashSet<String> ready = LalalClient.indexReadyOriginatingPaths(null, lib, null);
        if (!ready.contains(track.getAbsolutePath())) {
            throw new AssertionError("sidecar not indexed");
        }
    }

    /**
     * 2026-07-20 — SEGMENTED path→size map (no File list from customLibrary).
     * Layman: DB sizes alone still find songs that already have stem folders.
     */
    @Test
    public void indexReadyOriginatingPaths_fromPathSizeMap() throws Exception {
        File dir = File.createTempFile("hasstemsMap", "");
        if (!dir.delete() || !dir.mkdir()) throw new AssertionError("tmpdir");
        File track = new File(dir, "MapSong.mp3");
        writeBytes(track, new byte[900]);

        File sidecar = new File(dir, "MapSong.stems");
        if (!sidecar.mkdir()) throw new AssertionError("sidecar");
        writeBytes(new File(sidecar, "vocals.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "drums.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "bass.mp3"), new byte[200]);
        writeBytes(new File(sidecar, "melody.mp3"), new byte[200]);

        HashMap<String, Long> pathToSize = new HashMap<String, Long>();
        pathToSize.put(track.getAbsolutePath(), Long.valueOf(track.length()));
        HashSet<String> ready = LalalClient.indexReadyOriginatingPaths(null, pathToSize, null);
        if (!ready.contains(track.getAbsolutePath())) {
            throw new AssertionError("path→size map missed sidecar");
        }
    }

    private static void writeBytes(File f, byte[] data) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.close();
    }
}
