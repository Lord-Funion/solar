package com.solar.launcher.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-07-18 — LRU pages of library rows for SEGMENTED mode (Rockbox-style visible window).
 * Layman: remember a few chunks of the big song list so spinning doesn’t re-hit storage each time.
 * Technical: blockSize tracks/block; maxBlocks LRU via LinkedHashMap access-order.
 * Reversal: delete; always scan full customLibrary on browse.
 *
 * @param <T> row type (SongItem or path String)
 */
public final class LibrarySegmentCache<T> {

    public static final int DEFAULT_BLOCK_SIZE = 256;
    public static final int DEFAULT_MAX_BLOCKS = 12;

    private final int blockSize;
    private final int maxBlocks;
    private final LinkedHashMap<Integer, List<T>> blocks;

    public LibrarySegmentCache() {
        this(DEFAULT_BLOCK_SIZE, DEFAULT_MAX_BLOCKS);
    }

    public LibrarySegmentCache(int blockSize, int maxBlocks) {
        this.blockSize = blockSize > 0 ? blockSize : DEFAULT_BLOCK_SIZE;
        this.maxBlocks = maxBlocks > 0 ? maxBlocks : DEFAULT_MAX_BLOCKS;
        this.blocks = new LinkedHashMap<Integer, List<T>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, List<T>> eldest) {
                return size() > LibrarySegmentCache.this.maxBlocks;
            }
        };
    }

    public int blockSize() {
        return blockSize;
    }

    public int maxBlocks() {
        return maxBlocks;
    }

    /** Block index containing global row {@code index}. */
    public int blockIndexFor(int index) {
        if (index < 0) return 0;
        return index / blockSize;
    }

    /** Put a page (copied defensively). */
    public synchronized void putBlock(int blockIndex, List<T> rows) {
        if (blockIndex < 0) return;
        List<T> copy;
        if (rows == null || rows.isEmpty()) {
            copy = Collections.emptyList();
        } else {
            copy = Collections.unmodifiableList(new ArrayList<T>(rows));
        }
        blocks.put(Integer.valueOf(blockIndex), copy);
    }

    /** Cached page or null on miss. */
    public synchronized List<T> getBlock(int blockIndex) {
        return blocks.get(Integer.valueOf(blockIndex));
    }

    public synchronized boolean hasBlock(int blockIndex) {
        return blocks.containsKey(Integer.valueOf(blockIndex));
    }

    public synchronized void clear() {
        blocks.clear();
    }

    /**
     * 2026-07-20 — Evict cold LRU pages down to {@code maxKeep} (MemoryRelease / trim).
     * Layman: under memory pressure, keep fewer song shelves in RAM.
     * Technical: remove access-order eldest until size ≤ maxKeep; does not change constructor cap.
     * Reversal: delete; rely only on removeEldestEntry at put time.
     */
    public synchronized void trimTo(int maxKeep) {
        int keep = maxKeep < 0 ? 0 : maxKeep;
        while (blocks.size() > keep) {
            java.util.Iterator<Integer> it = blocks.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }

    public synchronized int cachedBlockCount() {
        return blocks.size();
    }

    /**
     * Slice one row from cached blocks; null if that block is not loaded.
     */
    public synchronized T get(int globalIndex) {
        if (globalIndex < 0) return null;
        int bi = blockIndexFor(globalIndex);
        List<T> page = blocks.get(Integer.valueOf(bi));
        if (page == null || page.isEmpty()) return null;
        int local = globalIndex - bi * blockSize;
        if (local < 0 || local >= page.size()) return null;
        return page.get(local);
    }

    /** JVM self-check. */
    static void selfCheck() {
        LibrarySegmentCache<String> c = new LibrarySegmentCache<String>(4, 2);
        List<String> a = new ArrayList<String>();
        a.add("a0");
        a.add("a1");
        a.add("a2");
        a.add("a3");
        c.putBlock(0, a);
        List<String> b = new ArrayList<String>();
        b.add("b0");
        b.add("b1");
        c.putBlock(1, b);
        if (!"a2".equals(c.get(2))) throw new AssertionError("get");
        c.putBlock(2, Collections.singletonList("c0"));
        // LRU: accessing 1 then putting 2 should evict 0 if max=2... put 2 makes size 3 briefly then eldest.
        if (c.cachedBlockCount() > 2) throw new AssertionError("lru size");
        if (c.blockIndexFor(9) != 2) throw new AssertionError("block index");
        // 2026-07-20 — trimTo drops eldest until the keep count.
        LibrarySegmentCache<String> t = new LibrarySegmentCache<String>(4, 4);
        t.putBlock(0, Collections.singletonList("a"));
        t.putBlock(1, Collections.singletonList("b"));
        t.putBlock(2, Collections.singletonList("c"));
        t.trimTo(1);
        if (t.cachedBlockCount() != 1) throw new AssertionError("trimTo size");
        t.trimTo(0);
        if (t.cachedBlockCount() != 0) throw new AssertionError("trimTo empty");
    }
}
