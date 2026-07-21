package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Home/settings wheel paint contract: one detent stays on screen while spinning.
 * Was: spinning skipped ensureVisible → four quick ticks looked like a frozen then-skip jump.
 */
public class MenuWheelChromePolicyTest {

    @Test
    public void spinningPaintDoesNotChaseViewport() {
        // 2026-07-20 — Mid-spin paint must NOT chase the viewport (focus hop is KEY’s job).
        MenuWheelChromePolicy.PaintPlan spin = MenuWheelChromePolicy.plan(true);
        assertFalse("paint path must not scroll mid-spin", spin.ensureVisible);
        assertFalse("requestFocus/status wait for idle", spin.focusAndStatus);
        assertFalse("preview art waits for idle", spin.preview);
    }

    @Test
    public void idleRunsFullChrome() {
        MenuWheelChromePolicy.PaintPlan idle = MenuWheelChromePolicy.plan(false);
        assertTrue(idle.ensureVisible);
        assertTrue(idle.focusAndStatus);
        assertTrue(idle.preview);
    }

    @Test
    public void fourRapidDetentsStayOneStepEach() {
        // Layman: four clicks → four single focus steps (not viewport slides).
        int index = 0;
        for (int i = 0; i < 4; i++) {
            index += 1;
            MenuWheelChromePolicy.PaintPlan p = MenuWheelChromePolicy.plan(true);
            assertFalse(p.ensureVisible);
        }
        assertTrue(index == 4);
    }

    @Test
    public void shortMenusDoNotPacePaintAfterKey() {
        // 2026-07-20 — Delayed coalescer flush after KEY felt like catch-up lag on Y1.
        assertFalse(MenuWheelChromePolicy.pacePaintAfterKey());
    }
}
