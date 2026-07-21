package com.solar.launcher;

import android.content.ComponentCallbacks2;

import com.solar.launcher.theme.ThemeManager;
import com.solar.launcher.youtube.YouTubeProgressiveCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 2026-07-20 — Ordered cache eviction on {@code onTrimMemory} / {@code onLowMemory}.
 * Layman: when the phone is low on RAM, free warm pictures and song shelves — keep Now Playing.
 * Technical order: Flow bake → Flow cover (NP pinned) → Theme ByteBudgetLruMap → LibrarySegmentCache
 * → severe Flow/session duplicates → pause YouTubeProgressiveCache growth.
 * Was: trim only bumped {@link LowMemoryGate} pressure gen. Reversal: delete; SolarApplication
 * trim callbacks only call LowMemoryGate again.
 */
public final class MemoryRelease {

    /** Soft trim keeps this many SEGMENTED song pages. */
    public static final int KEEP_SEGMENT_BLOCKS_SOFT = 2;
    /** Hard trim keeps this many. */
    public static final int KEEP_SEGMENT_BLOCKS_HARD = 1;

    /**
     * Ladder step ids for tests / logs.
     * Layman: which shelf we emptied and in what order.
     */
    public enum Step {
        FLOW_BAKE,
        FLOW_COVER,
        THEME,
        LIBRARY_SEGMENTS,
        FLOW_DUPLICATES,
        YT_PAUSE
    }

    /**
     * Optional host hooks (MainActivity + FlowScreenHost).
     * Layman: screens that hold big caches plug in so trim can reach them.
     */
    public interface Host {
        /** Clear Flow bake composites (pre-cover). */
        void releaseFlowBake();
        /** Clear Flow cover LRU but re-pin Now Playing / handoff art. */
        void releaseFlowCoverKeepNp();
        /** Shrink SEGMENTED library pages. */
        void shrinkLibrarySegments(boolean severe);
        /** Drop rebuildable Flow session / row duplicates (severe only). */
        void dropFlowDuplicates();
    }

    /**
     * Extra listeners (tests / future caches). Fired after Host steps, before YT pause.
     * Layman: optional add-ons that also free RAM when asked.
     */
    public interface Hook {
        void onMemoryRelease(int trimLevel, boolean severe);
    }

    private static volatile Host host;
    private static final CopyOnWriteArrayList<Hook> HOOKS = new CopyOnWriteArrayList<Hook>();
    private static final CopyOnWriteArrayList<Step> lastOrder = new CopyOnWriteArrayList<Step>();

    private MemoryRelease() {}

    /** Wire Activity/Flow caches. Null clears. 2026-07-20 */
    public static void setHost(Host h) {
        host = h;
    }

    /** Register an extra cache owner (idempotent). 2026-07-20 */
    public static void register(Hook hook) {
        if (hook == null) return;
        if (!HOOKS.contains(hook)) HOOKS.add(hook);
    }

    /** Drop an extra cache owner (activity destroy). 2026-07-20 */
    public static void unregister(Hook hook) {
        if (hook != null) HOOKS.remove(hook);
    }

    /** Clear host + hooks (unit tests). 2026-07-20 */
    public static void resetForTest() {
        host = null;
        HOOKS.clear();
        lastOrder.clear();
        try {
            YouTubeProgressiveCache.setGrowthPaused(false);
        } catch (Throwable ignored) {}
    }

    /** Last release step order (tests). */
    public static List<Step> lastOrderSnapshot() {
        return new ArrayList<Step>(lastOrder);
    }

    /**
     * 2026-07-20 — Run the release ladder; returns step names for tests / logs.
     * Layman: free rebuildable caches in a safe order when Android asks for memory.
     * Never evicts now-playing decode first (cover step re-pins NP).
     */
    public static List<String> release(int trimLevel) {
        lastOrder.clear();
        List<String> steps = new ArrayList<String>();
        boolean severe = isSevere(trimLevel);
        Host h = host;
        // 1) Flow bake first (composites rebuild from covers).
        try {
            if (h != null) h.releaseFlowBake();
            record(Step.FLOW_BAKE, steps);
        } catch (Throwable ignored) {}
        // 2) Flow cover LRU — host re-pins NP / handoff.
        try {
            if (h != null) h.releaseFlowCoverKeepNp();
            record(Step.FLOW_COVER, steps);
        } catch (Throwable ignored) {}
        // 3) Theme scaled/row bitmaps via ByteBudgetLruMap.
        try {
            ThemeManager.releaseMemoryCaches(severe);
            record(Step.THEME, steps);
        } catch (Throwable ignored) {}
        // 4) Shrink library segment cold pages.
        try {
            if (h != null) h.shrinkLibrarySegments(severe);
            record(Step.LIBRARY_SEGMENTS, steps);
        } catch (Throwable ignored) {}
        // 5) Severe: drop rebuildable Flow/session duplicates.
        if (severe) {
            try {
                if (h != null) h.dropFlowDuplicates();
                record(Step.FLOW_DUPLICATES, steps);
            } catch (Throwable ignored) {}
        }
        // Extra hooks (tests / future).
        for (int i = 0; i < HOOKS.size(); i++) {
            Hook hook = HOOKS.get(i);
            if (hook == null) continue;
            try {
                hook.onMemoryRelease(trimLevel, severe);
            } catch (Throwable ignored) {}
        }
        // 6) Pause opportunistic disk growth (do not delete user media).
        try {
            YouTubeProgressiveCache.setGrowthPaused(true);
            record(Step.YT_PAUSE, steps);
        } catch (Throwable ignored) {}
        return steps;
    }

    /**
     * 2026-07-20 — Severe trim: COMPLETE / MODERATE / RUNNING_CRITICAL.
     * Layman: really-low-RAM signals that justify dropping more caches.
     */
    public static boolean isSevere(int trimLevel) {
        return trimLevel == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                || trimLevel == ComponentCallbacks2.TRIM_MEMORY_MODERATE
                || trimLevel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
    }

    private static void record(Step step, List<String> steps) {
        lastOrder.add(step);
        steps.add(step.name().toLowerCase());
    }

    /** JVM self-check: bake → cover → theme → segments → yt; severe adds duplicates. 2026-07-20 */
    static void selfCheck() {
        resetForTest();
        final List<String> seen = new ArrayList<String>();
        setHost(new Host() {
            @Override
            public void releaseFlowBake() {
                seen.add("bake");
            }

            @Override
            public void releaseFlowCoverKeepNp() {
                seen.add("cover");
            }

            @Override
            public void shrinkLibrarySegments(boolean severe) {
                seen.add(severe ? "seg-hard" : "seg-soft");
            }

            @Override
            public void dropFlowDuplicates() {
                seen.add("dups");
            }
        });
        List<String> soft = release(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        if (!"flow_bake".equals(soft.get(0))) throw new AssertionError("bake first " + soft);
        if (!soft.contains("theme")) throw new AssertionError("theme");
        if (!"yt_pause".equals(soft.get(soft.size() - 1))) throw new AssertionError("yt last");
        if (seen.contains("dups")) throw new AssertionError("no dups on soft");
        if (!YouTubeProgressiveCache.isGrowthPaused()) throw new AssertionError("yt paused");
        seen.clear();
        List<String> hard = release(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        if (!hard.contains("flow_duplicates")) throw new AssertionError("dups on severe");
        if (!seen.contains("dups")) throw new AssertionError("dup hook");
        resetForTest();
    }
}
