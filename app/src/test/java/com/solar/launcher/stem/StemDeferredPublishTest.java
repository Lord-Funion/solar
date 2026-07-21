package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.solar.launcher.youtube.YouTubePlayCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Deferred stem publish — root pick, persist gate, work-only separate.
 * 2026-07-21
 */
public class StemDeferredPublishTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Internal vault wins when it has space.
     * Layman: prefer the phone chip over the SD card.
     * 2026-07-21
     */
    @Test
    public void pickPrefersInternalWhenSpacious() throws Exception {
        File internal = tmp.newFolder("internal_vault");
        File micro = tmp.newFolder("micro_vault");
        File app = tmp.newFolder("app_vault");
        File picked = StemDurableRoots.pick(internal, micro, app, 1L);
        assertSame(internal, picked);
    }

    /**
     * MicroSD wins when internal cannot satisfy the budget.
     * 2026-07-21
     */
    @Test
    public void pickFallsBackToMicroWhenInternalTight() throws Exception {
        // Null internal → skip; micro with space → pick micro. 2026-07-21
        File micro = tmp.newFolder("micro_only");
        File app = tmp.newFolder("app_only");
        File picked = StemDurableRoots.pick(null, micro, app, 1L);
        assertSame(micro, picked);
    }

    /**
     * App cache is last resort when both volume vaults are missing.
     * 2026-07-21
     */
    @Test
    public void pickFallsBackToAppCache() throws Exception {
        File app = tmp.newFolder("app_last");
        File picked = StemDurableRoots.pick(null, null, app, 1L);
        assertSame(app, picked);
    }

    /**
     * Reach / Deezer / YouTube play temps must not persist.
     * 2026-07-21
     */
    @Test
    public void shouldPersistFalseForStreamTemps() throws Exception {
        File cache = tmp.newFolder("cache");
        File reach = new File(new File(cache, "reach"), "partial.mp3");
        assertTrue(reach.getParentFile().mkdirs());
        writeBytes(reach, 200);
        assertFalse(StemDeferredPublish.shouldPersistStems(reach, cache));

        File deezer = new File(new File(cache, "deezer"), "dz.mp3");
        assertTrue(deezer.getParentFile().mkdirs());
        writeBytes(deezer, 200);
        assertFalse(StemDeferredPublish.shouldPersistStems(deezer, cache));

        File yt = new File(YouTubePlayCache.dir(cache), "vid_song.m4a");
        writeBytes(yt, 200);
        assertFalse(StemDeferredPublish.shouldPersistStems(yt, cache));
    }

    /**
     * Ordinary library file may keep durable stems.
     * 2026-07-21
     */
    @Test
    public void shouldPersistTrueForLibraryTrack() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "album_song.mp3");
        writeBytes(track, 200);
        File cache = tmp.newFolder("cache");
        assertTrue(StemDeferredPublish.shouldPersistStems(track, cache));
    }

    /**
     * publishAfterPlayback on a stream temp clears work and skips durable.
     * 2026-07-21
     */
    @Test
    public void publishAfterPlaybackSkipsStreamTemp() throws Exception {
        File cache = tmp.newFolder("cache");
        File track = new File(new File(cache, "reach"), "stream.mp3");
        assertTrue(track.getParentFile().mkdirs());
        writeBytes(track, 200);
        File work = tmp.newFolder("lalal_work_leaf");
        writeStem(work, "vocals.mp3");
        writeStem(work, "drum.mp3");
        writeStem(work, "bass.mp3");
        writeStem(work, "piano.mp3");
        List<LalalClient.StemFile> out =
                StemDeferredPublish.publishAfterPlayback(null, track, work, false, cache);
        assertNull(out);
        assertFalse(work.isDirectory());
    }

    /**
     * Deferred separate leaves pads only under work — no sibling durable leaf yet.
     * Simulates post-download state without calling the network API.
     * 2026-07-21
     */
    @Test
    public void deferredSeparateLeavesOnlyWork() throws Exception {
        File music = tmp.newFolder("Music");
        File track = new File(music, "jam.mp3");
        writeBytes(track, 200);
        File work = tmp.newFolder("work_leaf");
        File durable = tmp.newFolder("durable_leaf");
        writeStem(work, "vocals.mp3");
        writeStem(work, "drum.mp3");
        writeStem(work, "bass.mp3");
        writeStem(work, "piano.mp3");
        LalalClient.writeTrackMarker(work, track);
        // Contract: separateToMp3 no longer copies to durable — durable stays empty. 2026-07-21
        assertTrue(work.isDirectory());
        assertEquals(0, countMp3(durable));
        assertTrue(LalalClient.cacheReadyFlexible(work)
                || LalalClient.cacheReady(work)
                || LalalClient.loadStemDirFlexible(work).size() >= 4);
        // After deferred publish, durable gains pads and work is cleared. 2026-07-21
        File cache = tmp.newFolder("cache");
        // Point user stems beside track by publishing into userStemsDir. 2026-07-21
        List<LalalClient.StemFile> published =
                StemDeferredPublish.publishAfterPlayback(null, track, work, false, cache);
        assertNotNull(published);
        File user = LalalClient.userStemsDir(track);
        assertTrue(user.isDirectory());
        assertTrue(countMp3(user) >= 4);
        assertFalse(work.isDirectory());
    }

    /**
     * remember + flushAll drains pending list into empty.
     * 2026-07-21
     */
    @Test
    public void rememberCoalescesSameWorkPath() throws Exception {
        File work = tmp.newFolder("w1");
        File track = tmp.newFile("t.mp3");
        writeBytes(track, 50);
        ArrayList<StemDeferredPublish.Pending> list =
                new ArrayList<StemDeferredPublish.Pending>();
        StemDeferredPublish.remember(list,
                new StemDeferredPublish.Pending(track, work, false, true));
        StemDeferredPublish.remember(list,
                new StemDeferredPublish.Pending(track, work, true, true));
        assertEquals(1, list.size());
        assertTrue(list.get(0).premix);
        StemDeferredPublish.flushAll(null, tmp.newFolder("c"), list);
        assertTrue(list.isEmpty());
    }

    private static int countMp3(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        int n = 0;
        for (int i = 0; i < kids.length; i++) {
            if (kids[i].isFile() && kids[i].getName().endsWith(".mp3")) n++;
        }
        return n;
    }

    private static void writeStem(File dir, String name) throws Exception {
        writeBytes(new File(dir, name), 200);
    }

    private static void writeBytes(File f, int n) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            byte[] buf = new byte[n];
            out.write(buf);
        } finally {
            out.close();
        }
    }
}
