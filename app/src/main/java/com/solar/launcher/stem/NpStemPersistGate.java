package com.solar.launcher.stem;

import com.solar.launcher.StreamQueueHelper;

import java.io.File;

/**
 * NP Stems cook persist gates — auto library, prompt non-library, delete on fail.
 * Layman: library songs save stems quietly; temps ask first; failed downloads wipe scratch.
 * Technical: StreamQueueHelper library check + StemDeferredPublish persist + clearDirQuiet.
 * Was: ensureSolo sibling publish without Stems master. Reversal: solo path only.
 * 2026-07-21
 */
public final class NpStemPersistGate {

    private NpStemPersistGate() {}

    /**
     * Library track = under music root and not a stream temp.
     * Layman: a song in your Music folder, not a one-off download cache.
     * 2026-07-21
     */
    public static boolean isLibraryTrack(File track, File musicRoot, File appCache) {
        if (track == null || !track.isFile()) return false;
        if (StreamQueueHelper.isStreamTempFile(appCache, track)) return false;
        if (musicRoot != null && StreamQueueHelper.isLibraryMusicFile(musicRoot, appCache, track)) {
            return true;
        }
        // Dual-volume / Internal Music — still auto-persist when not a stream temp. 2026-07-21
        return StemDeferredPublish.shouldPersistStems(track, appCache);
    }

    /**
     * Prompt the user before starting a cloud multistem job.
     * Library → false (auto cook). Non-library / temps → true.
     * 2026-07-21
     */
    public static boolean mustPromptBeforeCook(File track, File musicRoot, File appCache) {
        return !isLibraryTrack(track, musicRoot, appCache);
    }

    /**
     * After a successful cook, may we copy work → Song.stems / vault?
     * Yes when library, or when non-library user accepted the pre-cook prompt.
     * 2026-07-21
     */
    public static boolean allowPersistAfterCook(boolean isLibrary, boolean userAcceptedPrompt) {
        if (isLibrary) return true;
        return userAcceptedPrompt;
    }

    /**
     * Download / separate failed — wipe partial work so origin stays the only feed.
     * Layman: throw away the half-baked stems folder.
     * Technical: {@link LalalClient#clearDirQuiet}; returns true when cleanup ran.
     * 2026-07-21
     */
    public static boolean cleanupFailedWork(File workDir) {
        if (workDir == null) return false;
        boolean existed = workDir.exists();
        LalalClient.clearDirQuiet(workDir);
        return existed;
    }

    /**
     * NP Stems path always uses full multistem + Melody premix (catch-all).
     * Was: stem_separator 2-file solo. Reversal: return false → ensureSolo path.
     * 2026-07-21
     */
    public static boolean useMultistemWithMelodyPremix() {
        return true;
    }
}
