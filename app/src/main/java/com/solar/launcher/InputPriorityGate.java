package com.solar.launcher;

/**
 * 2026-07-15 — Prefer user input over background UI / disk / network thrash.
 * Layman: while you're typing or scrolling, Solar holds heavy background work until
 * you've been hands-off for a few seconds — then catches up.
 * Technical: pure clock math on last-interaction timestamp; callers re-post with
 * {@link #msUntilAllowed}. Does not cancel network readers or PM delivery —
 * those stay live; only optional UI rebuilds / scans / flushes defer.
 * Reversal: always return false / 0 from shouldDefer / msUntilAllowed.
 */
public final class InputPriorityGate {
    /**
     * 2026-07-20 — Quiet period after last key/touch before background work may run.
     * Layman: wait a beat after you stop scrolling, then catch up on art/precook.
     * Was 3000L (felt sticky after wheel). Keep above ScrollIdleGate (~180–220ms) so dial stays index-first.
     * Reversal: 3000L if post-scroll chores fight the wheel again.
     */
    public static final long IDLE_MS = 1500L;

    private InputPriorityGate() {}

    /**
     * @param nowMs wall clock (usually {@link System#currentTimeMillis()})
     * @param lastInteractionMs last key/touch timestamp (same clock domain)
     * @return true when background work should wait so input stays snappy
     */
    public static boolean shouldDefer(long nowMs, long lastInteractionMs) {
        if (lastInteractionMs <= 0L) return false;
        long idle = nowMs - lastInteractionMs;
        return idle >= 0L && idle < IDLE_MS;
    }

    /**
     * @return ms until background is allowed (0 = run now). Never negative.
     */
    public static long msUntilAllowed(long nowMs, long lastInteractionMs) {
        if (lastInteractionMs <= 0L) return 0L;
        long idle = nowMs - lastInteractionMs;
        if (idle >= IDLE_MS) return 0L;
        if (idle < 0L) return IDLE_MS;
        return IDLE_MS - idle;
    }

    /**
     * Next delay for a periodic background tick.
     * When user is busy: wait until idle window ends (at least {@code busyMinMs}).
     * When quiet: use {@code quietPeriodMs}.
     */
    public static long nextPeriodMs(
            long nowMs, long lastInteractionMs, long quietPeriodMs, long busyMinMs) {
        long until = msUntilAllowed(nowMs, lastInteractionMs);
        if (until <= 0L) {
            return quietPeriodMs > 0L ? quietPeriodMs : 0L;
        }
        long floor = busyMinMs > 0L ? busyMinMs : until;
        return until > floor ? until : floor;
    }
}
