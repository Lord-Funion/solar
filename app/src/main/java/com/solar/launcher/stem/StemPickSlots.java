package com.solar.launcher.stem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stem mashup pick queue — unlimited ordered marks for jam seed.
 * Layman: tap Center on songs to stack them; Play starts the mash with that list.
 * Technical: ArrayList append/toggle by path; live pads still use {@link StemSession#MAX_SONGS}.
 * Was: File[2] Prev=Track1 / Next=Track2 bind. Reversal: restore SLOT_COUNT=2 + bind(slots,i,f).
 * 2026-07-21
 */
public final class StemPickSlots {
    /**
     * Live pad count hint (not a pick cap). Was: hard mark limit = MAX_SONGS.
     * Reversal: treat as max marks again.
     * 2026-07-21
     */
    public static final int LIVE_WINDOW = StemSession.MAX_SONGS;

    private StemPickSlots() {}

    /**
     * Append track if missing, or remove if already queued (toggle).
     * Layman: mark on / mark off with Center.
     * @return 1-based queue position after append, or 0 when removed / invalid
     * 2026-07-21
     */
    public static int toggle(List<File> queue, File track) {
        if (queue == null || track == null || !track.isFile()) return 0;
        int at = indexOfPath(queue, track);
        if (at >= 0) {
            queue.remove(at);
            return 0;
        }
        queue.add(track);
        return queue.size();
    }

    /**
     * Append if not already queued (no remove).
     * Layman: Prev/Next also park a song at the end of the line.
     * @return 1-based position, or existing position if already marked
     * 2026-07-21
     */
    public static int append(List<File> queue, File track) {
        if (queue == null || track == null || !track.isFile()) return 0;
        int at = indexOfPath(queue, track);
        if (at >= 0) return at + 1;
        queue.add(track);
        return queue.size();
    }

    /** How many valid files are marked. 2026-07-21 */
    public static int filled(List<File> queue) {
        if (queue == null) return 0;
        int n = 0;
        for (int i = 0; i < queue.size(); i++) {
            File f = queue.get(i);
            if (f != null && f.isFile()) n++;
        }
        return n;
    }

    /** True when Play may start (≥1 mark). 2026-07-20 / 2026-07-21 */
    public static boolean canStart(List<File> queue) {
        return filled(queue) >= 1;
    }

    /**
     * 1-based badge N for this file, or 0 if unmarked.
     * Layman: which queue number to show beside the song row.
     * Was: slotOf returning 1..2 only. Reversal: that File[] slot scan.
     * 2026-07-21
     */
    public static int positionOf(List<File> queue, File track) {
        int i = indexOfPath(queue, track);
        return i < 0 ? 0 : i + 1;
    }

    /** Ordered non-null files for openStemPlayer seed. 2026-07-20 / 2026-07-21 */
    public static ArrayList<File> orderedTracks(List<File> queue) {
        ArrayList<File> out = new ArrayList<File>();
        if (queue == null) return out;
        for (int i = 0; i < queue.size(); i++) {
            File f = queue.get(i);
            if (f != null && f.isFile()) out.add(f);
        }
        return out;
    }

    /** Clear all marks. 2026-07-20 / 2026-07-21 */
    public static void clear(List<File> queue) {
        if (queue == null) return;
        queue.clear();
    }

    /**
     * Mark every candidate in list order (skip dupes / bad files).
     * Layman: Select all — stamp every song on this page into the jam line.
     * Was: no bulk mark. Reversal: no-op / clear only.
     * 2026-07-21 Stems/Mix sanity
     */
    public static int selectAll(List<File> queue, List<File> candidates) {
        if (queue == null || candidates == null) return 0;
        int added = 0;
        for (int i = 0; i < candidates.size(); i++) {
            File f = candidates.get(i);
            if (f == null || !f.isFile()) continue;
            if (indexOfPath(queue, f) >= 0) continue;
            queue.add(f);
            added++;
        }
        return added;
    }

    /** Path-equality index (−1 if absent). 2026-07-21 */
    public static int indexOfPath(List<File> queue, File track) {
        if (queue == null || track == null) return -1;
        String path = track.getAbsolutePath();
        if (path == null) return -1;
        for (int i = 0; i < queue.size(); i++) {
            File f = queue.get(i);
            if (f != null && path.equals(f.getAbsolutePath())) return i;
        }
        return -1;
    }

    /**
     * @deprecated Prefer {@link #positionOf}; kept name for older call sites.
     * 2026-07-21
     */
    public static int slotOf(List<File> queue, File track) {
        return positionOf(queue, track);
    }
}
