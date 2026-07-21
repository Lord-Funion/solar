package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 2026-07-16/17/20 — Wheel coalesce clamp + dial-integrity notch survival. */
public class ListWheelCoalescerTest {

    @Test
    public void clampDoesNotExceedMax() {
        assertEquals(ListWheelCoalescer.MAX_STEPS_PER_FLUSH,
                ListWheelCoalescer.clampSteps(100));
        assertEquals(-ListWheelCoalescer.MAX_STEPS_PER_FLUSH,
                ListWheelCoalescer.clampSteps(-100));
    }

    @Test
    public void clampPreservesSmallSteps() {
        assertEquals(3, ListWheelCoalescer.clampSteps(3));
        assertEquals(-2, ListWheelCoalescer.clampSteps(-2));
        assertEquals(0, ListWheelCoalescer.clampSteps(0));
    }

    @Test
    public void pendingCapsAtOneFrameEvenWhenFlooded() {
        // Pure clamp path: flood of multi-step offers never exceeds one frame.
        int pending = 0;
        for (int i = 0; i < 20; i++) {
            pending += 4;
            pending = ListWheelCoalescer.clampSteps(pending);
        }
        assertEquals(ListWheelCoalescer.MAX_STEPS_PER_FLUSH, pending);
    }

    @Test
    public void idleClearMsStaysAdvisoryNotOfferWipe() {
        // 2026-07-20 — IDLE_CLEAR_MS is advisory only; offer path must not wipe on this gap.
        // Race that bit Y1 menus: IDLE_CLEAR (50) < MENU_MIN_FLUSH_Y1 (64).
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS >= 40L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS <= 80L);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS
                < ListWheelCoalescer.MENU_MIN_FLUSH_MS_Y1);
        assertTrue(ListWheelCoalescer.IDLE_CLEAR_MS
                < WheelPhysics.RESET_NANOS / 1_000_000L + 50L);
    }

    @Test
    public void maxStepsStaysModestForHugeLibraries() {
        // One selection per frame — never multi-frame backlog on 50k tracks.
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH <= 6);
        assertTrue(ListWheelCoalescer.MAX_STEPS_PER_FLUSH >= 3);
    }

    @Test
    public void dropPendingZerosBacklog() {
        // Pure API: dropPending is what KEY_UP / long-spin hard-stop call.
        ListWheelCoalescer c = new ListWheelCoalescer();
        c.dropPending();
        assertEquals(0, c.pendingStepsForTest());
    }

    @Test
    public void minFlushMsIsEighty() {
        // 2026-07-18 — Pace floor so list paints do not vsync-storm on MT6572.
        assertEquals(80L, ListWheelCoalescer.MIN_FLUSH_MS);
    }

    @Test
    public void y1ListFlushFloorMatchesMenu() {
        // 2026-07-20 — Song lists use the same paced floor as short menus on Y1/A5.
        assertEquals(ListWheelCoalescer.MENU_MIN_FLUSH_MS_Y1,
                ListWheelCoalescer.MIN_FLUSH_MS_Y1);
        assertTrue(ListWheelCoalescer.MENU_MIN_FLUSH_MS_Y1 < ListWheelCoalescer.MIN_FLUSH_MS);
    }

    @Test
    public void oppositeSignReplacesPendingNotNets() {
        // 2026-07-18 — CW backlog must die on first CCW (scrub-back interrupt).
        int pending = ListWheelCoalescer.mergeOfferPending(0, 5);
        assertEquals(ListWheelCoalescer.MAX_STEPS_PER_FLUSH, pending);
        pending = ListWheelCoalescer.mergeOfferPending(pending, -1);
        assertEquals(-1, pending);
    }

    @Test
    public void notchesSurviveGapBetweenIdleClearAndMinFlush() {
        // 2026-07-20 — Phase A1 dial integrity.
        // Layman: turning once every ~60 ms must still move two rows, not drop the first click.
        // Technical: gap 50–70 ms used to idle-clear while minFlushMs=64 left pending unflushed.
        // Simulate two ±1 offers with that gap — pending must be 2 until flush.
        assertTrue("race window exists",
                ListWheelCoalescer.IDLE_CLEAR_MS < ListWheelCoalescer.MENU_MIN_FLUSH_MS_Y1);
        long gapMs = (ListWheelCoalescer.IDLE_CLEAR_MS + ListWheelCoalescer.MENU_MIN_FLUSH_MS_Y1) / 2L;
        assertTrue("gap in 50–70 race band", gapMs >= 50L && gapMs <= 70L);

        int pending = 0;
        pending = ListWheelCoalescer.mergeOfferPending(pending, 1); // t=0
        // t ≈ gapMs later — must NOT wipe (merge has no idle-clear)
        pending = ListWheelCoalescer.mergeOfferPending(pending, 1);
        assertEquals("both notches survive until flush", 2, pending);

        // Same for CCW
        pending = 0;
        pending = ListWheelCoalescer.mergeOfferPending(pending, -1);
        pending = ListWheelCoalescer.mergeOfferPending(pending, -1);
        assertEquals(-2, pending);
    }

    @Test
    public void mergeOfferPendingNeverIdleClearsAcrossManyGaps() {
        // 2026-07-20 — Four slow detents under minFlush floor keep count (clamp still caps at 5).
        int pending = 0;
        for (int i = 0; i < 4; i++) {
            pending = ListWheelCoalescer.mergeOfferPending(pending, 1);
        }
        assertEquals(4, pending);
    }
}
