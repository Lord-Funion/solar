package com.solar.launcher;

import android.os.SystemClock;
import android.view.View;
import android.widget.ListView;

/**
 * Coalesce scrollwheel ticks for long ListViews (Y1/Y2 track lists, 10k–50k tracks).
 *
 * <p>Layman: spinning the dial fast used to queue every notch as full layout work; now ticks
 * merge into <b>one jump per paced flush</b> so the highlight keeps up (iPod Classic feel).
 *
 * <p>2026-07-17 — Hard-stop when the finger pauses or mic says lift: drop leftover queue so
 * the list does not walk after release. Optional {@link LiveGate} (mic volume/HF) kills
 * ghost flushes mid-frame on huge libraries.
 *
 * <p>2026-07-18 — Reverse (CW↔CCW) clears opposite backlog immediately so scrubbing back
 * does not wait for the old direction to finish draining.
 *
 * <p>Technical: signed step accumulator + paced flush ({@link #MIN_FLUSH_MS}); pending capped;
 * opposite-sign offer zeros then replaces. Reversal: apply every KEY.
 *
 * <p>2026-07-20 — Agent NDJSON/SD/HTTP metering removed from flush path (poisoned Y1 scroll).
 *
 * <p>2026-07-20 — Dial integrity: do <b>not</b> idle-clear pending on {@link #offerSteps}.
 * Was: gap ≥ {@link #IDLE_CLEAR_MS} wiped unflushed notches while minFlush (64) still pending
 * → “four turns for four rows”. Now: clear only via {@link #dropPending} (KEY_UP/hardStop)
 * or reverse. Reversal: restore idle-clear block in offerSteps.
 */
final class ListWheelCoalescer {

    /**
     * Cap jump per flush — one selection change; letter jump path uses section index instead.
     * Keep low so 50k lists never apply multi-frame backlog after stop.
     * 2026-07-17 — 5 (was 6): long spin leave less residual for the last frame.
     */
    static final int MAX_STEPS_PER_FLUSH = 5;

    /**
     * 2026-07-20 — Advisory pause gap only (docs / tests). Not applied inside {@link #offerSteps}.
     * Was: offerSteps zeroed pending when gap ≥ this while minFlush still waiting → lost notches.
     * Clear pending via {@link #dropPending} on KEY_UP/hardStop instead. Reversal: idle-clear on offer.
     */
    static final long IDLE_CLEAR_MS = 50L;

    /**
     * 2026-07-18 — Floor between list selection paints (was every vsync via postOnAnimation).
     * Layman: don’t redraw the highlight more than ~12×/sec so the UI queue doesn’t pile up.
     * Technical: scheduleFlush uses postDelayed when under this floor; reverse forces immediate.
     * Reversal: set 0 for postOnAnimation-only.
     */
    static final long MIN_FLUSH_MS = 80L;

    /**
     * 2026-07-20 — Y1/A5 song-list paint floor — match short menus (was 100 ms → felt sluggish).
     * Layman: scrolling songs should keep up with home/settings like the keyboard strip.
     * Reversal: raise above {@link #MENU_MIN_FLUSH_MS_Y1} again.
     */
    static final long MIN_FLUSH_MS_Y1 = 64L;

    /** 2026-07-19 — Home/settings menu paint floor (Y2 / default). */
    static final long MENU_MIN_FLUSH_MS = 48L;

    /**
     * 2026-07-19 — Y1/A5 menu paint floor — still snappier than song lists, calmer than 48 ms.
     * Reversal: {@link #MENU_MIN_FLUSH_MS}.
     */
    static final long MENU_MIN_FLUSH_MS_Y1 = 64L;

    /**
     * 2026-07-19 — Per-instance floor (menus use a lower value for snappier short lists).
     * Layman: home/settings can paint a bit faster than huge song lists.
     * Technical: defaults to {@link #MIN_FLUSH_MS}; menu coalescer sets menu floor. Reversal: always MIN_FLUSH_MS.
     */
    private long minFlushMs = MIN_FLUSH_MS;

    /**
     * 2026-07-19 — Song-list flush floor for this device family.
     * Layman: Y1 waits a hair longer between highlight jumps so the UI stays ahead of the dial.
     */
    static long listMinFlushMsForDevice() {
        return (DeviceFeatures.isY1() || DeviceFeatures.isA5()) ? MIN_FLUSH_MS_Y1 : MIN_FLUSH_MS;
    }

    /**
     * 2026-07-19 — Short-menu flush floor for this device family.
     */
    static long menuMinFlushMsForDevice() {
        return (DeviceFeatures.isY1() || DeviceFeatures.isA5())
                ? MENU_MIN_FLUSH_MS_Y1 : MENU_MIN_FLUSH_MS;
    }

    private int pendingSteps;
    private boolean flushPosted;
    private long lastOfferUptimeMs = -1L;
    private long lastFlushUptimeMs = -1L;
    /** 2026-07-18 — When true, next scheduleFlush skips MIN_FLUSH_MS (direction reverse). */
    private boolean forceImmediateFlush;
    private final Runnable flushRunnable;
    private View host;
    private Apply apply;
    private LiveGate liveGate;

    interface Apply {
        /** Apply net signed row steps (negative = up). Return true if selection moved. */
        boolean applySteps(int signedSteps);
    }

    /**
     * Optional mic / activity gate. When {@link #allowFlush()} is false, pending steps are
     * dropped instead of applied (finger lifted — no ghost walk).
     */
    interface LiveGate {
        boolean allowFlush();
    }

    ListWheelCoalescer() {
        flushRunnable = new Runnable() {
            @Override
            public void run() {
                flushPosted = false;
                long now = SystemClock.uptimeMillis();
                LiveGate gate = liveGate;
                if (gate != null && !gate.allowFlush()) {
                    // Mic/quiet: finger gone — discard backlog, do not walk the list.
                    pendingSteps = 0;
                    return;
                }
                int steps = pendingSteps;
                pendingSteps = 0;
                Apply a = apply;
                if (a == null || steps == 0) return;
                int signed = clampSteps(steps);
                lastFlushUptimeMs = now;
                a.applySteps(signed);
                // Only continue if new notches arrived during apply (live spin).
                if (pendingSteps != 0) {
                    if (gate != null && !gate.allowFlush()) {
                        pendingSteps = 0;
                        return;
                    }
                    scheduleFlush();
                }
            }
        };
    }

    /**
     * 2026-07-19 — Override paint floor for this coalescer (menus use ~48 ms).
     * Layman: short menus can update the highlight a bit more often than huge song lists.
     * Technical: clamp ≥0. Reversal: leave default {@link #MIN_FLUSH_MS}.
     */
    void setMinFlushMs(long ms) {
        minFlushMs = ms < 0L ? 0L : ms;
    }

    void setLiveGate(LiveGate gate) {
        this.liveGate = gate;
    }

    /**
     * Bind ListView + callback (song lists).
     * 2026-07-19 — Delegates to {@link #bind(View, Apply)} so home/settings can reuse the same coalescer.
     */
    void bind(ListView list, Apply apply) {
        bind((View) list, apply);
    }

    /**
     * 2026-07-19 — Bind any host View (ScrollView menu, decor) for paced wheel flushes.
     * Layman: same “merge dial clicks” helper works on short menus, not only long song lists.
     * Technical: host posts flushRunnable; Apply moves selection. Reversal: ListView-only bind.
     */
    void bind(View host, Apply apply) {
        this.host = host;
        this.apply = apply;
    }

    void clear() {
        dropPending();
        host = null;
        apply = null;
        lastOfferUptimeMs = -1L;
        lastFlushUptimeMs = -1L;
    }

    /**
     * Drop any unflushed steps and cancel a scheduled flush.
     * Call when the dial pauses, mic lift, stale keyevents, or the list is torn down.
     */
    void dropPending() {
        pendingSteps = 0;
        flushPosted = false;
        forceImmediateFlush = false;
        if (host != null) {
            host.removeCallbacks(flushRunnable);
        }
    }

    /**
     * 2026-07-18 — Next scheduleFlush skips the 80ms floor (used after CW↔CCW reverse).
     * Layman: turning back must move the highlight now, not after a paced wait.
     */
    void armImmediateFlush() {
        forceImmediateFlush = true;
        if (flushPosted && host != null) {
            host.removeCallbacks(flushRunnable);
            flushPosted = false;
            scheduleFlush();
        }
    }

    boolean offer(int direction) {
        return offerSteps(direction > 0 ? 1 : (direction < 0 ? -1 : 0),
                SystemClock.uptimeMillis());
    }

    boolean offerSteps(int signedSteps) {
        return offerSteps(signedSteps, SystemClock.uptimeMillis());
    }

    /**
     * @param nowMs {@link SystemClock#uptimeMillis()} (injectable for tests)
     */
    boolean offerSteps(int signedSteps, long nowMs) {
        if (signedSteps == 0 || apply == null || host == null) return false;
        LiveGate gate = liveGate;
        if (gate != null && !gate.allowFlush()) {
            // Drop ghost backlog only — still accept this live KEY offer.
            pendingSteps = 0;
            if (host != null) {
                host.removeCallbacks(flushRunnable);
            }
            flushPosted = false;
        }
        // 2026-07-20 — No idle-clear here (was wipe pending when gap ≥ IDLE_CLEAR_MS).
        // Layman: slow turns must still count every click; pause-clear is KEY_UP/hardStop only.
        // Technical: keep lastOffer for diagnostics; reverse + dropPending clear. Reversal: idle wipe.
        // 2026-07-18 — Opposite dial direction kills old backlog (CW↔CCW interrupt).
        boolean reversed = pendingSteps != 0
                && ((pendingSteps > 0) != (signedSteps > 0));
        if (reversed) {
            if (host != null) {
                host.removeCallbacks(flushRunnable);
            }
            flushPosted = false;
            forceImmediateFlush = true;
        }
        lastOfferUptimeMs = nowMs;
        pendingSteps = mergeOfferPending(pendingSteps, signedSteps);
        scheduleFlush();
        return true;
    }

    /**
     * 2026-07-20 — Pure pending merge for dial integrity (tests + {@link #offerSteps}).
     * Layman: each click adds to the pile; spinning the other way dumps the old pile first.
     * Technical: reverse zeros then add; clamp to {@link #MAX_STEPS_PER_FLUSH}; no idle wipe.
     * Reversal: idle-clear before merge in offerSteps.
     */
    static int mergeOfferPending(int pending, int signedSteps) {
        if (signedSteps == 0) return pending;
        boolean reversed = pending != 0 && ((pending > 0) != (signedSteps > 0));
        if (reversed) pending = 0;
        pending += signedSteps;
        return clampSteps(pending);
    }

    int pendingStepsForTest() {
        return pendingSteps;
    }

    /**
     * 2026-07-18 — Absolute pending steps waiting for the next vsync flush (frame-drop signal).
     * Layman: how far the dial is ahead of the painted highlight.
     * Technical: |pendingSteps| before clamp on flush; 0 when idle.
     */
    int pendingDepth() {
        return pendingSteps >= 0 ? pendingSteps : -pendingSteps;
    }

    /**
     * 2026-07-18 — Schedule at most one flush; pace to {@link #MIN_FLUSH_MS} unless reverse.
     * Was: always postOnAnimation (one flush per display frame).
     */
    private void scheduleFlush() {
        if (flushPosted || host == null) return;
        flushPosted = true;
        long now = SystemClock.uptimeMillis();
        long wait = 0L;
        boolean immediate = forceImmediateFlush;
        forceImmediateFlush = false;
        if (!immediate && minFlushMs > 0L && lastFlushUptimeMs >= 0L) {
            long since = now - lastFlushUptimeMs;
            if (since < minFlushMs) {
                wait = minFlushMs - since;
            }
        }
        if (wait > 0L) {
            host.postDelayed(flushRunnable, wait);
        } else {
            host.postOnAnimation(flushRunnable);
        }
    }

    static int clampSteps(int steps) {
        if (steps > MAX_STEPS_PER_FLUSH) return MAX_STEPS_PER_FLUSH;
        if (steps < -MAX_STEPS_PER_FLUSH) return -MAX_STEPS_PER_FLUSH;
        return steps;
    }
}
