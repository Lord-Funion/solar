package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Back short vs long must use physical key times, not wall-clock backlog.
 * Layman: a quick Back tap must leave Now Playing even if the UI was busy.
 */
public class BackHoldPolicyTest {

    private static final long LONG_MS = 350L;

    @Test
    public void physicalHoldMs_prefersEventTimesOverWallClock() {
        // Finger up at 120ms; wall clock drifted to 800ms while main thread was busy.
        assertEquals(120L, BackHoldPolicy.physicalHoldMs(1000L, 1120L, 5000L, 5800L));
    }

    @Test
    public void physicalHoldMs_fallsBackToWallWhenEventMissing() {
        assertEquals(0L, BackHoldPolicy.physicalHoldMs(0L, 0L, 0L, 100L));
        assertEquals(200L, BackHoldPolicy.physicalHoldMs(0L, 0L, 1000L, 1200L));
    }

    @Test
    public void shortPhysicalHold_undoesSpuriousTimerOpen() {
        // Timer opened Options because KEY_UP was delayed; physical tap was short.
        assertTrue(BackHoldPolicy.shouldUndoSpuriousContextOpen(
                true, /*longHandled*/ true, 120L, LONG_MS));
        assertFalse(BackHoldPolicy.shouldUndoSpuriousContextOpen(
                true, true, 400L, LONG_MS));
        assertFalse(BackHoldPolicy.shouldUndoSpuriousContextOpen(
                true, /*longHandled*/ false, 120L, LONG_MS));
        assertFalse(BackHoldPolicy.shouldUndoSpuriousContextOpen(
                false, true, 120L, LONG_MS));
    }

    @Test
    public void enterPlayer_shouldForceDismissLeftoverContext() {
        // Play Instrumental leaves animated dismiss mid-flight → Back must not hit menu first.
        assertTrue(BackHoldPolicy.shouldForceDismissContextOnEnterPlayer(true));
        assertFalse(BackHoldPolicy.shouldForceDismissContextOnEnterPlayer(false));
    }
}
