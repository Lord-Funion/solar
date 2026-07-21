package com.solar.launcher;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlaylistManagerTest {
    @Test
    public void parseM3u_resolvesRelativePaths() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "solar_pl_test");
        dir.mkdirs();
        File track = new File(dir, "a.mp3");
        if (!track.exists()) track.createNewFile();
        File m3u = new File(dir, "test.m3u");
        java.io.FileWriter fw = new java.io.FileWriter(m3u);
        fw.write("#EXTM3U\n#EXTINF:-1,Track A\na.mp3\n");
        fw.close();
        PlaylistManager.Entry e = PlaylistManager.parse(m3u, dir);
        if (e.tracks.size() != 1) throw new AssertionError("track count");
        track.delete();
        m3u.delete();
        dir.delete();
    }

    @Test
    public void safeFileName_stripsInvalidChars() {
        if (!"My Mix".equals(PlaylistManager.safeFileName("My Mix"))) throw new AssertionError("trim");
        if (!"Playlist".equals(PlaylistManager.safeFileName("  "))) throw new AssertionError("empty");
        if (!"a_b".equals(PlaylistManager.safeFileName("a/b"))) throw new AssertionError("slash");
    }

    @Test
    public void appendTracks_dedupesAndPreservesOrder() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "solar_pl_append");
        File plDir = new File(root, "Playlists");
        plDir.mkdirs();
        File t1 = new File(root, "one.mp3");
        File t2 = new File(root, "two.mp3");
        t1.createNewFile();
        t2.createNewFile();
        List<File> initial = new ArrayList<File>();
        initial.add(t1);
        PlaylistManager.Entry created = PlaylistManager.createPlaylist(root, "Test", initial);
        PlaylistManager.appendTracks(created.sourceFile, root, initial);
        PlaylistManager.Entry after = PlaylistManager.parse(created.sourceFile, root);
        if (after.tracks.size() != 1) throw new AssertionError("dedupe");
        List<File> add = new ArrayList<File>();
        add.add(t1);
        add.add(t2);
        PlaylistManager.appendTracks(created.sourceFile, root, add);
        after = PlaylistManager.parse(created.sourceFile, root);
        if (after.tracks.size() != 2) throw new AssertionError("append count");
        t1.delete();
        t2.delete();
        created.sourceFile.delete();
        plDir.delete();
        root.delete();
    }

    @Test
    public void scan_includesEmptyPlaylist() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "solar_pl_empty");
        root.mkdirs();
        PlaylistManager.createPlaylist(root, "Empty Shell", new ArrayList<File>());
        List<PlaylistManager.Entry> found = PlaylistManager.scan(root);
        boolean hasEmpty = false;
        for (PlaylistManager.Entry e : found) {
            if ("Empty Shell".equals(e.name) && e.tracks.isEmpty()) hasEmpty = true;
        }
        File plDir = PlaylistManager.playlistsDir(root);
        File[] leftovers = plDir.listFiles();
        if (leftovers != null) {
            for (File f : leftovers) f.delete();
        }
        plDir.delete();
        root.delete();
        if (!hasEmpty) throw new AssertionError("empty playlist not scanned");
    }

    /**
     * 2026-07-20 — Streamed path chunks write + soft-cap without a giant File ArrayList.
     * Layman: prove playlist save can take songs in small batches.
     */
    @Test
    public void pathChunks_createAndAppend_windowed() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "solar_pl_chunks");
        File plDir = new File(root, "Playlists");
        plDir.mkdirs();
        final File t1 = new File(root, "c1.mp3");
        final File t2 = new File(root, "c2.mp3");
        final File t3 = new File(root, "c3.mp3");
        t1.createNewFile();
        t2.createNewFile();
        t3.createNewFile();
        PlaylistManager.PathChunkSource src = new PlaylistManager.PathChunkSource() {
            int step;

            @Override
            public List<String> nextChunk() {
                if (step == 0) {
                    step = 1;
                    List<String> a = new ArrayList<String>();
                    a.add(t1.getAbsolutePath());
                    a.add(t2.getAbsolutePath());
                    return a;
                }
                if (step == 1) {
                    step = 2;
                    List<String> a = new ArrayList<String>();
                    a.add(t3.getAbsolutePath());
                    return a;
                }
                return null;
            }
        };
        PlaylistManager.ChunkWriteResult created = PlaylistManager.createPlaylistFromPathChunks(
                root, "Chunked", src, PlaylistManager.PATH_CHUNK_SOFT_CAP);
        if (created.added != 3) throw new AssertionError("create added=" + created.added);
        PlaylistManager.Entry parsed = PlaylistManager.parse(created.entry.sourceFile, root);
        if (parsed.tracks.size() != 3) throw new AssertionError("parse count");

        // Soft cap stops after 1 new path when appending.
        PlaylistManager.PathChunkSource oneMore = new PlaylistManager.PathChunkSource() {
            boolean once;

            @Override
            public List<String> nextChunk() {
                if (once) return null;
                once = true;
                List<String> a = new ArrayList<String>();
                a.add(t1.getAbsolutePath()); // dup
                a.add(t2.getAbsolutePath()); // dup — should not grow
                return a;
            }
        };
        PlaylistManager.ChunkWriteResult ap = PlaylistManager.appendPathChunks(
                created.entry.sourceFile, root, oneMore, PlaylistManager.PATH_CHUNK_SOFT_CAP);
        if (ap.added != 0) throw new AssertionError("dedupe added=" + ap.added);
        parsed = PlaylistManager.parse(created.entry.sourceFile, root);
        if (parsed.tracks.size() != 3) throw new AssertionError("still 3 after dup append");

        t1.delete();
        t2.delete();
        t3.delete();
        created.entry.sourceFile.delete();
        plDir.delete();
        root.delete();
    }
}
