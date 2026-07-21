package com.solar.launcher;

import org.junit.After;
import org.junit.Test;

/**
 * 2026-07-14 — A5 edge swipe classifier + hold-still timing constants.
 */
public class A5EdgeGesturesTest {

    @After
    public void tearDown() {
        DeviceFeatures.resetCacheForTest();
    }

    @Test
    public void leftEdgeSwipeIsBack() {
        if (A5EdgeGestures.classify(A5EdgeGestures.Edge.LEFT, 50f, 5f)
                != A5EdgeGestures.Gesture.BACK) {
            throw new AssertionError("left swipe → back");
        }
    }

    @Test
    public void rightEdgeSwipeIsBack() {
        if (A5EdgeGestures.classify(A5EdgeGestures.Edge.RIGHT, -60f, 10f)
                != A5EdgeGestures.Gesture.BACK) {
            throw new AssertionError("right swipe → back");
        }
    }

    @Test
    public void bottomEdgeSwipeUpIsHome() {
        if (A5EdgeGestures.classify(A5EdgeGestures.Edge.BOTTOM, 5f, -55f)
                != A5EdgeGestures.Gesture.HOME) {
            throw new AssertionError("bottom up → home");
        }
    }

    @Test
    public void bottomEdgeSwipeDownIsNone() {
        if (A5EdgeGestures.classify(A5EdgeGestures.Edge.BOTTOM, 5f, 55f)
                != A5EdgeGestures.Gesture.NONE) {
            throw new AssertionError("bottom down not home");
        }
    }

    @Test
    public void interiorSwipeIsNone() {
        if (A5EdgeGestures.classify(A5EdgeGestures.Edge.NONE, 80f, 0f)
                != A5EdgeGestures.Gesture.NONE) {
            throw new AssertionError("interior horizontal not edge-back");
        }
    }

    @Test
    public void edgeAtDetectsBands() {
        if (A5EdgeGestures.edgeAt(5, 160, 240, 320) != A5EdgeGestures.Edge.LEFT) {
            throw new AssertionError("left band");
        }
        if (A5EdgeGestures.edgeAt(230, 160, 240, 320) != A5EdgeGestures.Edge.RIGHT) {
            throw new AssertionError("right band");
        }
        if (A5EdgeGestures.edgeAt(120, 300, 240, 320) != A5EdgeGestures.Edge.BOTTOM) {
            throw new AssertionError("bottom band");
        }
        if (A5EdgeGestures.edgeAt(120, 160, 240, 320) != A5EdgeGestures.Edge.NONE) {
            throw new AssertionError("center none");
        }
    }

    @Test
    public void edgeBandWideEnoughForFinger() {
        // 2026-07-14 — 28px so fat-finger hits count; was too narrow so NP ate edge strokes.
        if (A5EdgeGestures.EDGE_BAND_PX < 24) {
            throw new AssertionError("edge band too narrow for Back");
        }
    }

    @Test
    public void holdContextAtLeastSystemLongPress() {
        // 2026-07-20 — Must be ≥ policy (~280); system long-press is ~500
        // so short taps do not open context. Floor 350 keeps deliberate hold-still.
        long policy = com.solar.input.policy.GlobalInputPolicy.SOLAR_BACK_CONTEXT_HOLD_MS;
        if (A5EdgeGestures.HOLD_CONTEXT_MS < policy) {
            throw new AssertionError("hold shorter than policy " + A5EdgeGestures.HOLD_CONTEXT_MS);
        }
        if (A5EdgeGestures.HOLD_CONTEXT_MS < 350L) {
            throw new AssertionError("hold too short for deliberate hold-still");
        }
    }

    @Test
    public void shouldArmHoldWhenChildDoesNotOwnLongPress() {
        // 2026-07-14 — Empty space / non-row → edge hold still opens context.
        if (!A5EdgeGestures.shouldArmHoldContext(false)) {
            throw new AssertionError("arm hold when child does not own long-press");
        }
    }

    @Test
    public void shouldNotArmHoldWhenChildOwnsLongPress() {
        // 2026-07-14 — Menu row OnLongClick owns focus+context; edge must not steal.
        if (A5EdgeGestures.shouldArmHoldContext(true)) {
            throw new AssertionError("defer hold when child owns long-press");
        }
    }

    @Test
    public void contextHoldThrobberArmActionsDefined() {
        // 2026-07-20 — Power/edge hold use these broadcasts for spinner parity with Back.
        if (OverlayTriggers.ACTION_CONTEXT_HOLD_ARM == null
                || OverlayTriggers.ACTION_CONTEXT_HOLD_ARM.length() == 0) {
            throw new AssertionError("CONTEXT_HOLD_ARM action missing");
        }
        if (OverlayTriggers.ACTION_CONTEXT_HOLD_CANCEL == null
                || OverlayTriggers.ACTION_CONTEXT_HOLD_CANCEL.length() == 0) {
            throw new AssertionError("CONTEXT_HOLD_CANCEL action missing");
        }
        if (OverlayTriggers.ACTION_CONTEXT_HOLD_ARM.equals(
                OverlayTriggers.ACTION_CONTEXT_HOLD_CANCEL)) {
            throw new AssertionError("arm and cancel actions must differ");
        }
    }

    @Test
    public void hitOwnsLongPressNullRootIsFalse() {
        if (A5EdgeGestures.hitOwnsLongPress(null, 10f, 10f)) {
            throw new AssertionError("null root must not own long-press");
        }
    }

    @Test
    public void showBottomStripHiddenWithoutPortraitChrome() {
        // null ctx → usePortraitChrome false → strip off regardless of screen flags.
        if (A5PortraitChrome.showBottomStrip(null, true, false)) {
            throw new AssertionError("null context must not force strip");
        }
        if (A5PortraitChrome.showBottomStrip(null, false, false)) {
            throw new AssertionError("browser must not show strip");
        }
        if (A5PortraitChrome.showBottomStrip(null, true, true)) {
            throw new AssertionError("reach browse hides strip");
        }
    }

    /** 2026-07-14 — Browser/Songs never get the home bottom strip. */
    @Test
    public void showBottomStripRequiresHomeOrSettings() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        // Without a real Context, orientation defaults → usePortraitChrome false → strip off.
        if (A5PortraitChrome.showBottomStrip(null, false, false)) {
            throw new AssertionError("Songs/browser must hide strip");
        }
    }
}
