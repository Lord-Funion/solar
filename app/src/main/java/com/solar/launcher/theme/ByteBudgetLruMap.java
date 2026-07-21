package com.solar.launcher.theme;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Byte-budgeted LRU keyed by an injectable size function. Hand-rolled (not
 * android.util.LruCache) so it stays plain-JUnit testable — the size function is the only
 * android-specific bit (e.g. Bitmap::getByteCount), and tests can swap in a plain one.
 * 2026-07-20 — trimToBytes + OnEvict for MemoryRelease soft shrink / bitmap recycle.
 * Reversal: drop trimToBytes/OnEvict; Theme HashMaps.
 */
public final class ByteBudgetLruMap<K, V> extends LinkedHashMap<K, V> {

    public interface SizeOf<V> {
        int sizeOf(V value);
    }

    /**
     * 2026-07-20 — Optional recycle hook when an entry leaves the map.
     * Layman: when we forget a picture, free its pixels if the caller says so.
     * Reversal: null listener (Theme HashMaps never recycled on eviction).
     */
    public interface OnEvict<V> {
        void onEvict(V value);
    }

    private final int maxBytes;
    private final SizeOf<V> sizeOf;
    private OnEvict<V> onEvict;
    private int currentBytes;

    /** Layman: make a map that forgets old pictures when it gets too heavy. */
    public ByteBudgetLruMap(int maxBytes, SizeOf<V> sizeOf) {
        super(16, 0.75f, true);
        this.maxBytes = maxBytes;
        this.sizeOf = sizeOf;
    }

    /** 2026-07-20 — Wire bitmap recycle (or test spy) on LRU drop. */
    public void setOnEvict(OnEvict<V> listener) {
        this.onEvict = listener;
    }

    @Override
    public V put(K key, V value) {
        V old = super.put(key, value);
        if (old != null) {
            currentBytes -= sizeOf.sizeOf(old);
            // Replaced in place — recycle old unless same instance.
            if (old != value) notifyEvict(old);
        }
        if (value != null) currentBytes += sizeOf.sizeOf(value);
        trim();
        return old;
    }

    @Override
    public V remove(Object key) {
        V removed = super.remove(key);
        if (removed != null) {
            currentBytes -= sizeOf.sizeOf(removed);
            notifyEvict(removed);
        }
        return removed;
    }

    @Override
    public void clear() {
        // 2026-07-20 — Do not OnEvict here: theme switches drop refs; ImageViews may still hold bitmaps.
        // MemoryRelease uses trimToBytes with a temporary recycle listener instead.
        super.clear();
        currentBytes = 0;
    }

    public void evictAll() {
        clear();
    }

    public int currentBytes() {
        return currentBytes;
    }

    public int maxBytes() {
        return maxBytes;
    }

    /**
     * 2026-07-20 — Shrink to a tighter byte ceiling (MemoryRelease soft trim).
     * Layman: throw away the least-used tiles until we fit under a smaller budget.
     * Technical: LRU eldest eviction until currentBytes ≤ max. Reversal: remove; only put()-trim.
     */
    public void trimToBytes(int max) {
        if (max < 0) max = 0;
        Iterator<Map.Entry<K, V>> it = entrySet().iterator();
        while (currentBytes > max && it.hasNext()) {
            V v = it.next().getValue();
            it.remove();
            if (v != null) {
                currentBytes -= sizeOf.sizeOf(v);
                notifyEvict(v);
            }
        }
    }

    private void notifyEvict(V v) {
        OnEvict<V> cb = onEvict;
        if (cb != null && v != null) {
            try {
                cb.onEvict(v);
            } catch (Throwable ignored) {}
        }
    }

    private void trim() {
        trimToBytes(maxBytes);
    }
}
