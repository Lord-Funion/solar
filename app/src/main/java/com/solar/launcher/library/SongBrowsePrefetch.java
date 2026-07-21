package com.solar.launcher.library;

/**
 * 2026-07-20 — Which SEGMENTED song-browse blocks to warm around the selection.
 * Layman: fetch this shelf and the ones next door so the dial never waits on the DB.
 * Technical: pure block-id math for tests + MainActivity prefetch / play window.
 * Reversal: inline bi±1; play used full virtualSongList.
 */
public final class SongBrowsePrefetch {

    /**
     * 2026-07-20 — Soft free-heap floor for optional ±2 prefetch neighbor.
     * Layman: only warm an extra shelf when RAM still has a little spare room.
     * Technical: ~4MB — not LibraryMemoryBudget.TARGET_FREE_HEAP (wrong on ~64MB-avail devices).
     * Reversal: gate on TARGET_FREE_HEAP_BYTES again.
     */
    public static final long EXTRA_NEIGHBOR_FREE_BYTES = 4L * 1024L * 1024L;

    private SongBrowsePrefetch() {}

    /**
     * 2026-07-20 — Block ids to keep warm for {@code dataIndex} (self + neighbors).
     * @param dataIndex global row in the segmented list
     * @param blockSize tracks per block
     * @param totalCount total tracks (exclusive upper for next block)
     * @param extraNeighbors 0 → ±1 only; 1 → ±2 when headroom allows
     * @return sorted unique block indices (may be empty if totalCount≤0)
     */
    public static int[] blocksAround(int dataIndex, int blockSize, int totalCount,
            int extraNeighbors) {
        if (totalCount <= 0 || blockSize <= 0) return new int[0];
        int idx = dataIndex < 0 ? 0 : dataIndex;
        if (idx >= totalCount) idx = totalCount - 1;
        int center = idx / blockSize;
        int maxBlock = (totalCount - 1) / blockSize;
        int radius = 1 + Math.max(0, extraNeighbors);
        int lo = Math.max(0, center - radius);
        int hi = Math.min(maxBlock, center + radius);
        int n = hi - lo + 1;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = lo + i;
        }
        return out;
    }

    /**
     * 2026-07-20 — First index of the ~2-block play queue around the focused row.
     * Layman: start the queue a bit before the song you picked, not at the library start.
     * Technical: max(0, dataIndex − blockSize/2). Reversal: play from index 0 of full list.
     */
    public static int playWindowStart(int dataIndex, int blockSize, int totalCount) {
        if (totalCount <= 0 || blockSize <= 0) return 0;
        int idx = dataIndex < 0 ? 0 : dataIndex;
        if (idx >= totalCount) idx = totalCount - 1;
        int start = idx - (blockSize / 2);
        return start < 0 ? 0 : start;
    }

    /**
     * 2026-07-20 — Exclusive end of the play window (about two blocks, clamped).
     * Layman: stop the short queue after the next shelf so we don’t enqueue the whole library.
     * Technical: start + 2×blockSize, min totalCount. Reversal: end = totalCount.
     */
    public static int playWindowEndExclusive(int dataIndex, int blockSize, int totalCount) {
        if (totalCount <= 0 || blockSize <= 0) return 0;
        int start = playWindowStart(dataIndex, blockSize, totalCount);
        int end = start + (blockSize * 2);
        return end > totalCount ? totalCount : end;
    }

    /**
     * 2026-07-20 — Whether free heap allows the optional extra prefetch neighbor.
     * Layman: only grab one more shelf when memory still looks comfortable.
     */
    public static boolean allowExtraNeighbor(long freeHeapBytes) {
        return freeHeapBytes >= EXTRA_NEIGHBOR_FREE_BYTES;
    }
}
