package com.solar.launcher.stem;

import android.content.Context;

import com.solar.launcher.StreamQueueHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Deferred durable stem publish — play from {@code lalal_work}, copy after session ends.
 * Layman: finish listening first, then save the stem folder for next time.
 * Technical: skips stream temps; prefers {@link LalalClient#userStemsDir} then internal-first vault.
 * Was: {@code separateToMp3} called {@code publishStems} before mixers opened.
 * Reversal: restore immediate publish in separate; drop this queue.
 * 2026-07-21
 */
public final class StemDeferredPublish {

    /** Single background writer — coalesce flush jobs off the UI thread. 2026-07-21 */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    /** In-flight keys so re-enter does not double-copy the same work leaf. 2026-07-21 */
    private static final java.util.HashSet<String> IN_FLIGHT = new java.util.HashSet<String>();

    private StemDeferredPublish() {}

    /**
     * True when this track may keep a long-term stem copy.
     * Layman: library songs yes; Reach/Deezer/YouTube play temps no.
     * Technical: {@link StreamQueueHelper#isStreamTempFile} (includes YouTube play cache).
     * 2026-07-21
     */
    public static boolean shouldPersistStems(File track, File appCache) {
        if (track == null || !track.isFile()) return false;
        if (appCache == null) return false;
        // Stream temps + youtube_play — never Song.stems / vault. 2026-07-21
        if (StreamQueueHelper.isStreamTempFile(appCache, track)) return false;
        return true;
    }

    /**
     * Parent of the track can host {@code Song.stems/} with enough free space.
     * Layman: song’s folder is writable and not nearly full.
     * 2026-07-21
     */
    public static boolean canWriteUserStems(File track, long needBytes) {
        if (track == null) return false;
        File parent = track.getParentFile();
        if (parent == null || !parent.isDirectory()) return false;
        try {
            if (!parent.canWrite()) return false;
        } catch (Exception e) {
            return false;
        }
        return com.solar.launcher.StreamCacheRoot.hasSpace(parent, needBytes);
    }

    /**
     * Copy work pads to user sidecar or durable vault, then clear work.
     * Layman: move the scratch stems into their permanent home.
     * Technical: no-persist → {@link LalalClient#clearDirQuiet}; else publish + clear.
     * 2026-07-21
     */
    public static List<LalalClient.StemFile> publishAfterPlayback(Context ctx, File track,
            File workDir, boolean premix, File appCache) throws IOException {
        if (workDir == null || !workDir.isDirectory()) return null;
        File cache = appCache != null ? appCache
                : (ctx != null ? ctx.getCacheDir() : null);
        if (!shouldPersistStems(track, cache)) {
            // Stream temp — drop scratch; do not vault. 2026-07-21
            LalalClient.clearDirQuiet(workDir);
            return null;
        }
        long need = StemDurableRoots.needBytes(premix);
        File dest;
        if (canWriteUserStems(track, need)) {
            // Beside the song when the volume allows. 2026-07-21
            dest = LalalClient.userStemsDir(track);
        } else if (ctx != null) {
            dest = LalalClient.durableStemDir(ctx, track, premix);
        } else {
            dest = LalalClient.stemCacheDir(cache, track, premix);
        }
        if (dest == null) {
            LalalClient.clearDirQuiet(workDir);
            return null;
        }
        // Already published (same path) — just stamp + leave. 2026-07-21
        if (samePath(workDir, dest)) {
            LalalClient.writeTrackMarker(dest, track);
            return LalalClient.loadStemDirFlexible(dest);
        }
        List<LalalClient.StemFile> pads = LalalClient.loadStemDirFlexible(workDir);
        if (pads == null || pads.isEmpty()) {
            LalalClient.clearDirQuiet(workDir);
            return null;
        }
        List<LalalClient.StemFile> published = LalalClient.publishStems(pads, dest, track);
        LalalClient.clearDirQuiet(workDir);
        return published;
    }

    /**
     * Copy solo work vocals/instrumental into sibling folders (or skip for temps).
     * Layman: after NP solo ends, save the peeled files next to the song.
     * 2026-07-21
     */
    public static void publishSoloAfterPlayback(Context ctx, File track, File vocalsWork,
            File instrWork, File workDir, File appCache) {
        if (!shouldPersistStems(track, appCache)) {
            if (workDir != null) LalalClient.clearDirQuiet(workDir);
            return;
        }
        LalalClient.publishSoloSiblings(track, vocalsWork, instrWork);
        // Tags land after siblings exist (ensureSolo used to tag too early). 2026-07-21
        SoloStemTagWriter.writeBothIfPresent(ctx, track);
        // Keep work as findReady fallback; only clear if siblings exist. 2026-07-21
        File acap = SoloStemPaths.findReadySibling(track, SoloMode.ACAPELLA);
        File instr = SoloStemPaths.findReadySibling(track, SoloMode.INSTRUMENTAL);
        if (acap != null && instr != null && workDir != null) {
            LalalClient.clearDirQuiet(workDir);
        }
    }

    /**
     * Enqueue full-stem publish on the shared background writer.
     * Layman: remember this scratch folder and copy it later without freezing the UI.
     * Technical: coalesce by work path; no-op if already in flight.
     * 2026-07-21
     */
    public static void enqueueAfterPlayback(final Context ctx, final File track,
            final File workDir, final boolean premix, final File appCache,
            final boolean persistAllowed) {
        if (workDir == null) return;
        final String key = workDir.getAbsolutePath();
        synchronized (IN_FLIGHT) {
            if (IN_FLIGHT.contains(key)) return;
            IN_FLIGHT.add(key);
        }
        final Context app = ctx != null ? ctx.getApplicationContext() : null;
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!persistAllowed) {
                        LalalClient.clearDirQuiet(workDir);
                    } else {
                        try {
                            publishAfterPlayback(app, track, workDir, premix, appCache);
                        } catch (Exception ignored) {}
                    }
                } finally {
                    synchronized (IN_FLIGHT) {
                        IN_FLIGHT.remove(key);
                    }
                }
            }
        });
    }

    /**
     * Enqueue solo sibling publish off the UI / playback thread.
     * 2026-07-21
     */
    public static void enqueueSoloAfterPlayback(final Context ctx, final File track,
            final File vocalsWork, final File instrWork, final File workDir,
            final File appCache) {
        if (track == null) return;
        final String key = "solo:" + track.getAbsolutePath();
        synchronized (IN_FLIGHT) {
            if (IN_FLIGHT.contains(key)) return;
            IN_FLIGHT.add(key);
        }
        final Context app = ctx != null ? ctx.getApplicationContext() : null;
        EXEC.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    publishSoloAfterPlayback(app, track, vocalsWork, instrWork, workDir, appCache);
                } finally {
                    synchronized (IN_FLIGHT) {
                        IN_FLIGHT.remove(key);
                    }
                }
            }
        });
    }

    /** True when two files share the same absolute path. 2026-07-21 */
    private static boolean samePath(File a, File b) {
        if (a == null || b == null) return a == b;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }

    /**
     * Pending job remembered by Stem Player until detach.
     * Layman: sticky note “copy these stems when we leave”.
     * 2026-07-21
     */
    public static final class Pending {
        public final File track;
        public final File workDir;
        public final boolean premix;
        public final boolean persistAllowed;

        /** Build one deferred publish record. 2026-07-21 */
        public Pending(File track, File workDir, boolean premix, boolean persistAllowed) {
            this.track = track;
            this.workDir = workDir;
            this.premix = premix;
            this.persistAllowed = persistAllowed;
        }
    }

    /**
     * Replace any pending row for the same work path (coalesce re-enters).
     * 2026-07-21
     */
    public static void remember(List<Pending> list, Pending next) {
        if (list == null || next == null || next.workDir == null) return;
        String path = next.workDir.getAbsolutePath();
        for (int i = list.size() - 1; i >= 0; i--) {
            Pending p = list.get(i);
            if (p != null && p.workDir != null
                    && path.equals(p.workDir.getAbsolutePath())) {
                list.remove(i);
            }
        }
        list.add(next);
    }

    /**
     * Drain pending list into background enqueue calls.
     * Layman: leaving Stem Player → start the quiet copies.
     * 2026-07-21
     */
    public static void flushAll(Context ctx, File appCache, List<Pending> list) {
        if (list == null || list.isEmpty()) return;
        ArrayList<Pending> copy = new ArrayList<Pending>(list);
        list.clear();
        for (int i = 0; i < copy.size(); i++) {
            Pending p = copy.get(i);
            if (p == null) continue;
            enqueueAfterPlayback(ctx, p.track, p.workDir, p.premix, appCache, p.persistAllowed);
        }
    }
}
