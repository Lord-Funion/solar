package com.solar.launcher;

import android.os.Handler;
import android.os.Looper;

/**
 * 2026-07-18 — Rockbox talk-delay analogue: while the dial is spinning, skip heavy paint
 * (preview, art, full row rebind); when idle for {@link #IDLE_MS}, run one catch-up pass.
 * Layman: spinning only moves the highlight; when you pause, covers and subtitles catch up.
 * Technical: markActivity on every coalesced apply; scheduleIdle runs listener once after quiet.
 * Reversal: delete class; always refresh preview/bind on each selection change.
 */
public final class ScrollIdleGate {

    /**
     * Quiet gap before idle catch-up (Rockbox voice ~HZ/5 ≈ 200 ms).
     * 2026-07-18 — 180 ms: snappy enough on Y1, still merges last spin burst.
     */
    public static final long IDLE_MS = 180L;

    /**
     * 2026-07-19 — Y1/A5 idle window: slightly longer so MT6572 finishes selection paints
     * before preview/art catch-up. Reversal: use {@link #IDLE_MS} on all families.
     */
    public static final long IDLE_MS_Y1 = 220L;

    /** Pending coalescer depth that means “busy paint — frame-drop” (Rockbox FRAMEDROP 6). */
    public static final int FRAME_DROP_PENDING = 3;

    /**
     * 2026-07-19 — Y1/A5: drop dual-pane / haptic sooner (pending ≥2). Reversal: FRAME_DROP_PENDING.
     */
    public static final int FRAME_DROP_PENDING_Y1 = 2;

    public interface IdleListener {
        /** Called once after idleMs with no {@link #markActivity()}. */
        void onScrollIdle();
    }

    private final Handler handler;
    private IdleListener listener;
    private long lastActivityUptimeMs = -1L;
    private boolean idlePosted;
    /** 2026-07-19 — Instance idle window (Y1 may raise via {@link #applyDeviceDefaults()}). */
    private long idleMs = IDLE_MS;
    /** 2026-07-19 — Instance frame-drop pending depth. */
    private int frameDropPending = FRAME_DROP_PENDING;
    private final Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            idlePosted = false;
            long now = System.currentTimeMillis();
            if (lastActivityUptimeMs >= 0L && (now - lastActivityUptimeMs) < idleMs) {
                // Another notch arrived; reschedule.
                scheduleIdle();
                return;
            }
            IdleListener l = listener;
            if (l != null) {
                try {
                    l.onScrollIdle();
                } catch (Throwable ignored) {
                }
            }
        }
    };

    public ScrollIdleGate() {
        Handler h;
        try {
            h = new Handler(Looper.getMainLooper());
        } catch (Throwable t) {
            h = null;
        }
        handler = h;
    }

    /**
     * 2026-07-19 — Tighten paint budget on MT6572-class (Y1/A5); leave Y2 at stock.
     * Layman: weaker chips skip heavy art sooner while you spin the dial.
     * Technical: idleMs + frameDropPending. Reversal: leave defaults; never call this.
     */
    public void applyDeviceDefaults() {
        if (DeviceFeatures.isY1() || DeviceFeatures.isA5()) {
            idleMs = IDLE_MS_Y1;
            frameDropPending = FRAME_DROP_PENDING_Y1;
        } else {
            idleMs = IDLE_MS;
            frameDropPending = FRAME_DROP_PENDING;
        }
    }

    /** Wire catch-up (preview / visible rebind). Null clears. */
    public void setListener(IdleListener listener) {
        this.listener = listener;
    }

    /**
     * Call on every wheel apply / notch that advances selection.
     * Layman: “still spinning — don’t paint expensive stuff yet.”
     */
    public void markActivity() {
        // wall clock — SystemClock is not mocked in JVM unit tests
        lastActivityUptimeMs = System.currentTimeMillis();
        scheduleIdle();
    }

    /** True while within idleMs of last activity (spinning or just stopped). */
    public boolean isSpinning() {
        if (lastActivityUptimeMs < 0L) return false;
        return (System.currentTimeMillis() - lastActivityUptimeMs) < idleMs;
    }

    /**
     * Frame-drop paint: skip preview/rebind when jumping hard or coalescer still has backlog.
     * Selection must still advance; only paint is deferred.
     */
    public boolean shouldFrameDropPaint(int absStepsThisFlush, int pendingAfterOffer) {
        if (absStepsThisFlush > 1) return true;
        if (pendingAfterOffer >= frameDropPending) return true;
        return isSpinning() && absStepsThisFlush >= 1 && pendingAfterOffer > 0;
    }

    /** Cancel pending idle callback (leave list / hard stop). */
    public void reset() {
        lastActivityUptimeMs = -1L;
        idlePosted = false;
        if (handler != null) {
            handler.removeCallbacks(idleRunnable);
        }
    }

    private void scheduleIdle() {
        if (handler == null || listener == null) return;
        if (idlePosted) {
            handler.removeCallbacks(idleRunnable);
        }
        idlePosted = true;
        handler.postDelayed(idleRunnable, idleMs);
    }

    /** Test hook. */
    void markActivityAtForTest(long uptimeMs) {
        lastActivityUptimeMs = uptimeMs;
    }

    /** 2026-07-19 — Test / debug: current frame-drop pending threshold. */
    int frameDropPendingForTest() {
        return frameDropPending;
    }

    /** 2026-07-19 — Test / debug: current idle quiet window. */
    long idleMsForTest() {
        return idleMs;
    }

    /**
     * 2026-07-19 — Injectable paint budget for unit tests (no DeviceFeatures).
     * Reversal: only use applyDeviceDefaults / field defaults.
     */
    void setPaintBudgetForTest(long idleMsValue, int frameDropPendingValue) {
        idleMs = idleMsValue;
        frameDropPending = frameDropPendingValue;
    }
}
