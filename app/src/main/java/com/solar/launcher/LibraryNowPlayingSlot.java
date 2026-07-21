package com.solar.launcher;

import java.io.File;

/**
 * 2026-07-20 — Path-based now-playing mark for library song rows.
 * Layman: show play/pause on the song that is actually playing, in any list.
 * Technical: match absolute paths; ignore playlist-name gate.
 * Was: isPlaylistViewNowPlayingSlot required active playlist name == virtualQueryValue.
 * Reversal: restore playlist-name-only gate in MainActivity.
 */
public final class LibraryNowPlayingSlot {
    private LibraryNowPlayingSlot() {}

    /**
     * True when this row file is the current music queue item (or its solo origin match).
     * 2026-07-20
     */
    public static boolean isNowPlayingRow(boolean musicActive, File queueCurrent, File rowFile) {
        if (!musicActive || queueCurrent == null || rowFile == null) return false;
        return samePath(queueCurrent, rowFile);
    }

    /**
     * Match row to queue current or shared solo origin (instrumental/acapella siblings).
     * 2026-07-20
     */
    public static boolean isNowPlayingRowOrSoloOrigin(boolean musicActive, File queueCurrent,
            File rowFile, File soloOrigin) {
        if (!musicActive || rowFile == null) return false;
        if (queueCurrent != null && samePath(queueCurrent, rowFile)) return true;
        if (soloOrigin != null && samePath(soloOrigin, rowFile)) {
            // Row is the origin while queue plays a sibling stem (or dual mix keeps origin). 2026-07-20
            return queueCurrent != null;
        }
        return false;
    }

    public static boolean samePath(File a, File b) {
        if (a == null || b == null) return a == b;
        return a.getAbsolutePath().equals(b.getAbsolutePath());
    }
}
