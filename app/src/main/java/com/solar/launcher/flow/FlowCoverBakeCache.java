package com.solar.launcher.flow;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pre-composite cover + floor reflection to RGB_565 — one {@link Canvas#drawBitmap} per carousel slot.
 * ponytail: never bake on UI thread — {@link #peek} in draw, {@link #scheduleBake} on worker.
 */
public final class FlowCoverBakeCache {

    public interface BakeListener {
        void onBaked(String bakeKey);
    }

    private static final int MAX_ENTRIES = 56;
    private static final ExecutorService BAKE_EXECUTOR = Executors.newSingleThreadExecutor();

    /** 2026-07-20 — Design cap for docs / tests (fatter reflection composites). */
    public static int maxEntries() {
        return MAX_ENTRIES;
    }

    /**
     * 2026-07-20 — coverKey half of {@link #bakeKey} (before first '|').
     * Layman: which album this baked tile belongs to.
     */
    public static String coverKeyFromBakeKey(String bakeKey) {
        if (bakeKey == null || bakeKey.isEmpty()) return null;
        int pipe = bakeKey.indexOf('|');
        return pipe > 0 ? bakeKey.substring(0, pipe) : bakeKey;
    }

    private final Map<String, Bitmap> baked = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            Bitmap b = eldest.getValue();
            if (b != null && !b.isRecycled()) b.recycle();
            return true;
        }
    };

    private final Set<String> baking = new HashSet<String>();
    private final Paint coverPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint reflectionPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF square = new RectF();
    private BakeListener listener;

    public void setListener(BakeListener listener) {
        this.listener = listener;
    }

    static String bakeKey(String coverKey, CoverFlowLayout.Metrics metrics, boolean withReflection) {
        if (coverKey == null || metrics == null) return "";
        return coverKey + "|" + metrics.thumbSizePx() + "|" + (withReflection ? "R" : "N");
    }

    /** Non-blocking cache hit — safe from {@code onDraw}. */
    public synchronized Bitmap peek(String coverKey, CoverFlowLayout.Metrics metrics,
            boolean withReflection) {
        if (coverKey == null || metrics == null) return null;
        String key = bakeKey(coverKey, metrics, withReflection);
        Bitmap hit = baked.get(key);
        if (hit != null && !hit.isRecycled()) return hit;
        return null;
    }

    /**
     * Queue async bake — no-op if cached or already baking.
     * Cover bitmap must not be recycled until bake completes (carousel RAM cache owns it).
     */
    public void scheduleBake(final String coverKey, final Bitmap cover,
            final CoverFlowLayout.Metrics metrics, final boolean withReflection) {
        if (coverKey == null || cover == null || cover.isRecycled() || metrics == null) return;
        // 2026-07-16 — Skip bake under RAM pressure (Y1 thrash). Reversal: remove LowMemoryGate check.
        try {
            if (com.solar.launcher.LowMemoryGate.shouldDeferHeavyWork(null)) return;
        } catch (Throwable ignored) {}
        final String key = bakeKey(coverKey, metrics, withReflection);
        synchronized (this) {
            Bitmap hit = baked.get(key);
            if (hit != null && !hit.isRecycled()) return;
            if (baking.contains(key)) return;
            baking.add(key);
        }
        BAKE_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap out = bake(cover, metrics, withReflection);
                final BakeListener notify;
                synchronized (FlowCoverBakeCache.this) {
                    baking.remove(key);
                    if (out != null) baked.put(key, out);
                    notify = listener;
                }
                // 2026-07-20 — Prefer bake-only residency under pressure (drop raw in listener).
                if (notify != null && out != null) notify.onBaked(key);
            }
        });
    }

    /** @deprecated UI thread — use {@link #peek} + {@link #scheduleBake}. */
    public synchronized Bitmap get(String coverKey, Bitmap cover, CoverFlowLayout.Metrics metrics,
            boolean withReflection) {
        Bitmap hit = peek(coverKey, metrics, withReflection);
        if (hit != null) return hit;
        return null;
    }

    private Bitmap bake(Bitmap cover, CoverFlowLayout.Metrics metrics, boolean withReflection) {
        // 2026-07-16 — TOCTOU: cover may recycle between scheduleBake and worker run.
        if (cover == null || cover.isRecycled() || metrics == null) return null;
        int w = Math.max(1, Math.round(metrics.displaySize));
        float reflectH = withReflection ? metrics.reflectHeight : 0f;
        int h = w + Math.max(0, Math.round(reflectH));
        Bitmap out = null;
        try {
            out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            if (cover.isRecycled()) {
                if (out != null && !out.isRecycled()) out.recycle();
                return null;
            }
            Canvas canvas = new Canvas(out);
            square.set(0f, 0f, w, w);
            FlowAlbumArt3d.drawCoverWithReflection(canvas, cover, square, 0f, 1f, reflectH,
                    metrics.reflectTable, coverPaint, reflectionPaint);
            return out;
        } catch (RuntimeException e) {
            // Canvas: trying to use a recycled bitmap — drop partial bake.
            if (out != null && !out.isRecycled()) {
                try { out.recycle(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }

    public synchronized void clear() {
        for (Bitmap b : baked.values()) {
            if (b != null && !b.isRecycled()) b.recycle();
        }
        baked.clear();
        baking.clear();
    }

    /**
     * 2026-07-20 — MemoryRelease step 1: drop bake composites (covers stay until cover LRU step).
     * Layman: throw away pre-drawn album+reflection tiles; they rebuild when you scroll Flow.
     * Reversal: no-op; only clear on viewport/metric change.
     */
    public synchronized void releaseForMemoryPressure() {
        clear();
    }
}
