package com.solar.launcher.media;

/**
 * 2026-07-20 — Pure math so NP/volume tips stay fully visible above the scrub.
 * Layman: room for the hold-Back / hold-Play line; tip stays up long enough to read.
 * Tech: FrameLayout AT_MOST for hint = transportH − progressH; hide deferral for live hold.
 * Reversal: drop this class; restore fixed 48dp transport + instant live-hold hide.
 */
public final class TransportHintChromePolicy {

    /** Fade in/out for live keep-holding tip (matches volume tip overlay). */
    public static final long LIVE_HOLD_FADE_MS = 200L;
    /** Short Play/Pause taps still get a readable Flow tip. */
    public static final long LIVE_HOLD_MIN_VISIBLE_MS = 900L;

    private TransportHintChromePolicy() {}

    /**
     * 2026-07-20 — True when tip row is not force-measured shorter than its min height.
     * FrameLayout bottom-gravity + marginBottom=progressH caps hint at transportH−progressH.
     */
    public static boolean hintFitsInTransport(int transportHeightDp, int progressHeightDp,
            int hintMinHeightDp) {
        if (transportHeightDp < 0 || progressHeightDp < 0 || hintMinHeightDp < 0) return false;
        return transportHeightDp - progressHeightDp >= hintMinHeightDp;
    }

    /**
     * 2026-07-20 — Smallest transport band that keeps scrub + full tip without clipping.
     */
    public static int requiredTransportHeight(int progressHeightDp, int hintMinHeightDp) {
        int p = progressHeightDp < 0 ? 0 : progressHeightDp;
        int h = hintMinHeightDp < 0 ? 0 : hintMinHeightDp;
        return p + h;
    }

    /**
     * 2026-07-20 — How long to wait before starting fade-out so tip meets min on-screen time.
     * Layman: even a quick tap leaves the tip up long enough to read.
     */
    public static long hideDelayMs(long shownAtElapsedMs, long nowElapsedMs, long minVisibleMs) {
        long min = minVisibleMs < 0L ? 0L : minVisibleMs;
        long shownFor = nowElapsedMs - shownAtElapsedMs;
        if (shownFor < 0L) return min;
        if (shownFor >= min) return 0L;
        return min - shownFor;
    }
}
