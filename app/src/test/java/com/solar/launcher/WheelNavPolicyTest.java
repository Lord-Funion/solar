package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Wheel must keep ~7–11 detents/rev; stale/repeat guards must not eat live notches.
 * Layman: spinning the dial should move many rows; stopping must not keep walking.
 */
public class WheelNavPolicyTest {

    @Test
    public void freshEventNeverStale() {
        assertFalse(WheelNavPolicy.isStaleEvent(20L, 200L));
        assertFalse(WheelNavPolicy.isStaleEvent(0L, -1L));
    }

    @Test
    public void agedEventDuringLiveSpinIsNotStale() {
        // Main thread lagged mid-spin: event is old but last offer was recent.
        assertFalse(WheelNavPolicy.isStaleEvent(150L, 30L));
        assertFalse(WheelNavPolicy.isStaleEvent(200L, 100L));
    }

    @Test
    public void agedEventAfterFingerIdleIsStale() {
        // Finger parked — late queue ticks must die (no coast).
        assertTrue(WheelNavPolicy.isStaleEvent(150L, 200L));
        assertTrue(WheelNavPolicy.isStaleEvent(100L, -1L));
    }

    @Test
    public void liveDownAlwaysAccepted() {
        assertTrue(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_DOWN, 0, true));
        assertTrue(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_DOWN, 0, false));
    }

    @Test
    public void hardwareRepeatAcceptedOnlyWhileKeyHeld() {
        // Continuous turn often arrives as DOWN then value=2 repeats.
        assertTrue(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_DOWN, 3, true));
        assertFalse(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_DOWN, 3, false));
    }

    @Test
    public void keyUpNeverMoves() {
        assertFalse(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_UP, 0, true));
        assertFalse(WheelNavPolicy.acceptNotch(KeyEvent.ACTION_UP, 2, true));
    }

    @Test
    public void keyUpClearsHeld() {
        assertTrue(WheelNavPolicy.heldAfter(KeyEvent.ACTION_DOWN, 0, false));
        assertFalse(WheelNavPolicy.heldAfter(KeyEvent.ACTION_UP, 0, true));
        // Repeats do not clear hold.
        assertTrue(WheelNavPolicy.heldAfter(KeyEvent.ACTION_DOWN, 2, true));
    }
}
