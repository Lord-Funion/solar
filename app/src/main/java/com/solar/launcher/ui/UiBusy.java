package com.solar.launcher.ui;

import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

/**
 * 2026-07-18 — Refcount status-bar busy reasons (search, track change, Flow, seek buffer, …).
 * Layman: keeps the little status spinner on while Solar is still working so waits feel intentional.
 * Technical: named tokens with nest-safe begin/end; main-thread listener for ProgressBar visibility.
 * Reversal: delete class; restore isStatusBarSearchLoading-only throbber in MainActivity.
 */
public final class UiBusy {

    /** Search results still streaming (Get Music / YouTube browse / podcasts / …). */
    public static final String REASON_SEARCH = "search";
    /** Now Playing track switch / prepareMusicTrack. */
    public static final String REASON_TRACK_CHANGE = "track_change";
    /** Opening Flow from menu or Now Playing. */
    public static final String REASON_FLOW_OPEN = "flow_open";
    /**
     * 2026-07-18 — Context-menu hold (Back / Power / long OK) until key-up.
     * Layman: spinner while waiting for the Options menu to pop.
     * Technical: arm on hold start if idle; never steal an existing busy reason.
     */
    public static final String REASON_CONTEXT_HOLD = "context_hold";
    /** Menu screen destination building a heavy list. */
    public static final String REASON_TRANSITION = "transition";
    /** Library folder / catalog first page bind. */
    public static final String REASON_LIBRARY_LOAD = "library_load";
    /** Stream seek past buffered edge waiting for catch-up. */
    public static final String REASON_SEEK_BUFFER = "seek_buffer";
    /** YouTube URL resolve / first buffer. */
    public static final String REASON_YOUTUBE_RESOLVE = "youtube_resolve";
    /**
     * 2026-07-18 — Reach / Deezer file download in flight.
     * Layman: spinner while a song is downloading from the network.
     * Technical: arm for SOULSEEK_UI_DOWNLOAD (and similar); clear on cancel/complete.
     */
    public static final String REASON_DOWNLOAD = "download";
    /**
     * 2026-07-18 — Decoder buffering / podcast episode stream / mid-play stall.
     * Layman: spinner while audio or video is still filling its buffer.
     * Technical: MediaPlayer/IJK BUFFERING_START/END + podcastEpisodeLoading + video fill.
     */
    public static final String REASON_MEDIA_BUFFER = "media_buffer";
    /**
     * 2026-07-20 — Cold start until home is navigable.
     * Layman: status spinner while Solar is still waking up so menus do not look half-dead.
     * Technical: MainActivity arms until first-ready dismiss / onCreate home ready; blocks wheel/OK.
     * Reversal: remove reason; drop StartupNavGate swallow.
     */
    public static final String REASON_STARTUP = "startup";
    /**
     * 2026-07-20 — Stem / Lalal prepare or mashup resolve in flight.
     * Layman: spinner while Stem Player is splitting or loading pads.
     * Technical: StemPlayerHost.startJob → clear when mixers ready / fail / detach.
     * Reversal: drop reason; statusView text-only again.
     */
    public static final String REASON_STEM_PREPARE = "stem_prepare";

    public interface Listener {
        void onBusyChanged(boolean busy);
    }

    private static final Map<String, Integer> COUNTS = new HashMap<String, Integer>();
    private static Listener listener;
    /** Generation so delayed auto-end cannot clear a newer busy epoch. */
    private static int generation;
    private static long lastBusyStartMs;
    /** Lazy — unit tests have no main looper prepare. */
    private static Handler mainHandler;

    private UiBusy() {}

    private static Handler main() {
        Handler h = mainHandler;
        if (h == null) {
            try {
                h = new Handler(Looper.getMainLooper());
            } catch (Throwable t) {
                // Unit tests / no looper: synchronous notify only.
                return null;
            }
            mainHandler = h;
        }
        return h;
    }

    /** Wire once from MainActivity (status ProgressBar sync). */
    public static void setListener(Listener l) {
        listener = l;
        notifyListener();
    }

    public static boolean isBusy() {
        synchronized (COUNTS) {
            for (Integer c : COUNTS.values()) {
                if (c != null && c.intValue() > 0) return true;
            }
        }
        return false;
    }

    public static boolean isBusy(String reason) {
        if (reason == null) return false;
        synchronized (COUNTS) {
            Integer c = COUNTS.get(reason);
            return c != null && c.intValue() > 0;
        }
    }

    /** 2026-07-19 — Debug: active reason→count map for throbber hunting (cb4747). */
    public static String snapshotReasons() {
        synchronized (COUNTS) {
            return COUNTS.toString();
        }
    }

    /**
     * Mark work in flight.
     * Layman: turn the spinner on for this job.
     * Technical: nest-safe refcount per reason; notifies listener on 0→busy.
     */
    public static void begin(String reason) {
        if (reason == null || reason.length() == 0) return;
        boolean becameBusy = false;
        synchronized (COUNTS) {
            Integer c = COUNTS.get(reason);
            int next = (c == null ? 0 : c.intValue()) + 1;
            COUNTS.put(reason, Integer.valueOf(next));
            if (next == 1 && totalLocked() == 1) {
                becameBusy = true;
                // wall clock — avoid SystemClock (not mocked in unit tests)
                lastBusyStartMs = System.currentTimeMillis();
                generation++;
            }
        }
        // #region agent log
        // 2026-07-19 — Lean: skip per-begin spam (was ~1 Hz idle + every hop).
        // #endregion
        if (becameBusy) notifyListener();
        else if (isBusy()) notifyListener();
    }

    /**
     * Clear one begin for this reason.
     * Layman: this job finished — hide spinner if nothing else is running.
     */
    public static void end(String reason) {
        if (reason == null || reason.length() == 0) return;
        boolean becameIdle = false;
        synchronized (COUNTS) {
            Integer c = COUNTS.get(reason);
            if (c == null || c.intValue() <= 0) return;
            int next = c.intValue() - 1;
            if (next <= 0) COUNTS.remove(reason);
            else COUNTS.put(reason, Integer.valueOf(next));
            becameIdle = totalLocked() == 0;
        }
        if (becameIdle) notifyListener();
    }

    /** Drop all counts for one reason (cancel / screen leave). */
    public static void clear(String reason) {
        if (reason == null) return;
        boolean becameIdle = false;
        synchronized (COUNTS) {
            if (!COUNTS.containsKey(reason)) return;
            COUNTS.remove(reason);
            becameIdle = totalLocked() == 0;
        }
        if (becameIdle) notifyListener();
        else notifyListener();
    }

    /**
     * 2026-07-19 — Clear after one UI frame so sync OK/Back/drill still paints the throbber once.
     * Layman: spinner stays on for a blink even when the next menu is ready instantly.
     * Was: clear() same-stack → ProgressBar never drawn. Reversal: call clear(reason) directly.
     */
    public static void clearNextFrame(final String reason) {
        Handler h = main();
        if (h == null) {
            clear(reason);
            return;
        }
        h.post(new Runnable() {
            @Override
            public void run() {
                clear(reason);
            }
        });
    }

    /** Clear every reason (activity destroy / emergency). */
    public static void clearAll() {
        synchronized (COUNTS) {
            COUNTS.clear();
            generation++;
        }
        notifyListener();
    }

    /**
     * begin + auto end after timeoutMs (safety net if a path forgets end).
     * Layman: spinner cannot stick forever if a prepare never finishes.
     * 2026-07-20 — Callers that need an immediate status-bar paint should sync after begin
     * (see MainActivity.armNavFeedbackThrobber).
     */
    public static void beginAutoEnd(final String reason, long timeoutMs) {
        begin(reason);
        final int gen;
        synchronized (COUNTS) {
            gen = generation;
        }
        final long delay = Math.max(250L, timeoutMs);
        Handler h = main();
        if (h == null) return; // unit tests: rely on explicit end/clear
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (COUNTS) {
                    if (gen != generation) return;
                    if (!isBusy(reason)) return;
                }
                clear(reason);
            }
        }, delay);
    }

    /** Test hook — wipe state between unit tests. */
    static void resetForTest() {
        synchronized (COUNTS) {
            COUNTS.clear();
            generation = 0;
            lastBusyStartMs = 0L;
        }
        listener = null;
        mainHandler = null;
    }

    static int countForTest(String reason) {
        synchronized (COUNTS) {
            Integer c = COUNTS.get(reason);
            return c == null ? 0 : c.intValue();
        }
    }

    private static int totalLocked() {
        int t = 0;
        for (Integer c : COUNTS.values()) {
            if (c != null) t += c.intValue();
        }
        return t;
    }

    private static void notifyListener() {
        final Listener l = listener;
        if (l == null) return;
        final boolean busy = isBusy();
        Handler h = main();
        if (h == null || Looper.myLooper() == Looper.getMainLooper()) {
            try {
                l.onBusyChanged(busy);
            } catch (Throwable ignored) {}
            return;
        }
        h.post(new Runnable() {
            @Override
            public void run() {
                try {
                    l.onBusyChanged(busy);
                } catch (Throwable ignored) {}
            }
        });
    }
}
