package com.solar.launcher.flow;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** LRU of pre-scaled Flow cover bitmaps with distance-priority eviction (Rockbox free_slide_prio). */
public final class FlowCoverCache {

    public interface Listener {
        void onCoverReady(String coverKey, Bitmap bitmap);
    }

    /** 2026-07-05 — Tighter LOD window for 30k catalogs; rollback: 48 / 6. */
    private static final int MAX_ENTRIES = 32;
    private static final int EVICT_DISTANCE = 4;

    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            recycleUnlessPlaceholder(eldest.getValue());
            return true;
        }
    };

    private Bitmap emptyPlaceholder;
    private int emptyPlaceholderPx;

    public int thumbSizePx() {
        return AlbumArtCache.THUMB_PX;
    }

    /** Viewport-aware thumb size (Cover Flow square cover). */
    public int thumbSizePx(float viewW, float viewH) {
        return CoverFlowLayout.metricsForViewport(viewW, viewH).thumbSizePx();
    }

    /** Shared empty slide bitmap — never evicted or recycled by LRU. */
    public synchronized Bitmap emptyPlaceholder(int sizePx) {
        if (sizePx <= 0) sizePx = 96;
        if (emptyPlaceholder != null && emptyPlaceholderPx == sizePx
                && !emptyPlaceholder.isRecycled()) {
            return emptyPlaceholder;
        }
        if (emptyPlaceholder != null && !emptyPlaceholder.isRecycled()) {
            emptyPlaceholder.recycle();
        }
        emptyPlaceholderPx = sizePx;
        emptyPlaceholder = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565);
        emptyPlaceholder.eraseColor(0xFF2A2A30);
        return emptyPlaceholder;
    }

    public synchronized Bitmap get(String coverKey) {
        if (coverKey == null) return null;
        Bitmap b = cache.get(coverKey);
        if (b != null && b.isRecycled()) {
            cache.remove(coverKey);
            return null;
        }
        return b;
    }

    public synchronized void put(String coverKey, Bitmap bitmap) {
        if (coverKey == null || bitmap == null) return;
        Bitmap old = cache.put(coverKey, bitmap);
        recycleUnlessPlaceholder(old);
    }

    /**
     * Evict covers far from carousel focus — keeps RAM for nearby slides.
     * 2026-07-05 — optional coverKey→index map skips O(n) item scan at 30k.
     */
    public synchronized void evictFarFrom(int focusIndex, List<FlowItem> items,
            java.util.Map<String, Integer> coverKeyIndex) {
        if (items == null || items.isEmpty() || cache.size() <= MAX_ENTRIES / 2) return;
        java.util.Set<String> cachedKeys = new java.util.HashSet<String>(cache.keySet());
        Map<String, Integer> keyToIndex;
        if (coverKeyIndex != null && !coverKeyIndex.isEmpty()) {
            keyToIndex = new java.util.HashMap<String, Integer>(cachedKeys.size());
            for (String key : cachedKeys) {
                Integer idx = coverKeyIndex.get(key);
                if (idx != null) keyToIndex.put(key, idx);
            }
        } else {
            keyToIndex = new java.util.HashMap<String, Integer>(cachedKeys.size());
            for (int i = 0; i < items.size(); i++) {
                FlowItem item = items.get(i);
                if (item != null && item.coverKey != null && cachedKeys.contains(item.coverKey)) {
                    keyToIndex.put(item.coverKey, i);
                }
            }
        }
        Iterator<Map.Entry<String, Bitmap>> it = cache.entrySet().iterator();
        while (it.hasNext() && cache.size() > MAX_ENTRIES / 2) {
            Map.Entry<String, Bitmap> e = it.next();
            Integer idx = keyToIndex.get(e.getKey());
            if (idx == null || Math.abs(idx - focusIndex) > EVICT_DISTANCE) {
                recycleUnlessPlaceholder(e.getValue());
                it.remove();
            }
        }
    }

    /** Back-compat wrapper — linear scan when no index map supplied. */
    public synchronized void evictFarFrom(int focusIndex, List<FlowItem> items) {
        evictFarFrom(focusIndex, items, null);
    }

    private void recycleUnlessPlaceholder(Bitmap b) {
        if (b != null && b != emptyPlaceholder && !b.isRecycled()) b.recycle();
    }

    public synchronized void clear() {
        for (Bitmap b : cache.values()) {
            recycleUnlessPlaceholder(b);
        }
        cache.clear();
    }

    /**
     * 2026-07-20 — MemoryRelease step 2: clear covers then re-pin Now Playing / handoff bitmap.
     * Layman: empty the carousel picture shelf but keep the song that’s playing so NP never goes blank first.
     * Technical: clear + optional put(keepKey, keep). Reversal: clear() only / invalidateCoverCaches.
     */
    public synchronized void releaseKeeping(String keepKey, Bitmap keep) {
        clear();
        if (keepKey != null && keep != null && !keep.isRecycled()) {
            put(keepKey, keep);
        }
    }

    /**
     * 2026-07-20 — Drop one raw cover after bake lands (avoid bake+raw double residency).
     * Layman: throw away the plain album picture once the shiny tile exists.
     * Technical: remove+recycle unless placeholder. Reversal: no-op; keep both in RAM.
     */
    public synchronized void drop(String coverKey) {
        if (coverKey == null || coverKey.isEmpty()) return;
        Bitmap old = cache.remove(coverKey);
        recycleUnlessPlaceholder(old);
    }

    /** 2026-07-20 — Design cap for docs / tests (LRU eldest eviction). */
    public static int maxEntries() {
        return MAX_ENTRIES;
    }
}
