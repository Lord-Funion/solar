package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-20 — Settings-root wheel feel shared by all short menus. */
public class WheelMenuNavPolicyTest {

    @Test
    public void hapticWheneverMoved() {
        // 2026-07-20 — Soft-spin skip removed; wrap flag ignored.
        assertTrue(WheelMenuNavPolicy.shouldHaptic(true, false));
        assertTrue(WheelMenuNavPolicy.shouldHaptic(true, true));
        assertFalse(WheelMenuNavPolicy.shouldHaptic(false, false));
        assertFalse(WheelMenuNavPolicy.shouldHaptic(false, true));
    }

    @Test
    public void hapticDurationIsShortPulse() {
        assertTrue(WheelMenuNavPolicy.HAPTIC_MS >= 1);
        assertTrue(WheelMenuNavPolicy.HAPTIC_MS <= 5);
        assertTrue(WheelMenuNavPolicy.HAPTIC_MIN_GAP_MS >= 20L);
    }

    @Test
    public void shortMenusUseEdgeOnlyFocus() {
        assertTrue(WheelMenuNavPolicy.useEdgeOnlyFocus());
    }
}
