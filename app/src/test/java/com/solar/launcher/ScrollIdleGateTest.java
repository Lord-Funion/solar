package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-18 — ScrollIdleGate frame-drop / idle timing contract.
 * 2026-07-19 — Y1 paint-budget knobs (injectable; no DeviceFeatures in JVM).
 */
public class ScrollIdleGateTest {

    @Test
    public void idleMsMatchesRockboxTalkWindow() {
        assertTrue(ScrollIdleGate.IDLE_MS >= 150L);
        assertTrue(ScrollIdleGate.IDLE_MS <= 220L);
        assertTrue(ScrollIdleGate.IDLE_MS_Y1 >= ScrollIdleGate.IDLE_MS);
        assertTrue(ScrollIdleGate.IDLE_MS_Y1 <= 260L);
    }

    @Test
    public void frameDropOnMultiStep() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertTrue(g.shouldFrameDropPaint(3, 0));
        assertFalse(g.shouldFrameDropPaint(1, 0));
    }

    @Test
    public void frameDropOnPendingBacklog() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertTrue(g.shouldFrameDropPaint(1, ScrollIdleGate.FRAME_DROP_PENDING));
        assertFalse(g.shouldFrameDropPaint(1, 0));
    }

    @Test
    public void y1FrameDropThresholdIsEarlier() {
        // 2026-07-19 — MT6572 drops art/haptic at pending≥2 (was 3).
        ScrollIdleGate g = new ScrollIdleGate();
        g.setPaintBudgetForTest(ScrollIdleGate.IDLE_MS_Y1, ScrollIdleGate.FRAME_DROP_PENDING_Y1);
        assertEquals(ScrollIdleGate.FRAME_DROP_PENDING_Y1, g.frameDropPendingForTest());
        assertTrue(g.shouldFrameDropPaint(1, ScrollIdleGate.FRAME_DROP_PENDING_Y1));
        assertFalse(g.shouldFrameDropPaint(1, ScrollIdleGate.FRAME_DROP_PENDING_Y1 - 1));
    }

    @Test
    public void spinningAfterMark() {
        ScrollIdleGate g = new ScrollIdleGate();
        assertFalse(g.isSpinning());
        g.markActivity();
        assertTrue(g.isSpinning());
        g.reset();
        assertFalse(g.isSpinning());
    }
}
