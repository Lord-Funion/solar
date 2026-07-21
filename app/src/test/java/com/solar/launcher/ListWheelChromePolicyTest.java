package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-21 — Dual-line ListView wheel paint: viewport always; focus/preview only when idle.
 * Was: copying menu mid-spin ensureVisible=false would leave long-list selection off-screen.
 */
public class ListWheelChromePolicyTest {

    @Test
    public void spinningKeepsViewportSkipsFocusAndPreview() {
        ListWheelChromePolicy.PaintPlan spin = ListWheelChromePolicy.plan(true);
        assertTrue("long lists must pin selection on screen mid-spin", spin.ensureVisible);
        assertFalse("requestFocus waits for idle", spin.requestFocus);
        assertFalse("preview art waits for idle", spin.preview);
    }

    @Test
    public void idleRunsFullChrome() {
        ListWheelChromePolicy.PaintPlan idle = ListWheelChromePolicy.plan(false);
        assertTrue(idle.ensureVisible);
        assertTrue(idle.requestFocus);
        assertTrue(idle.preview);
    }

    @Test
    public void selectedOrFocusedLightsRow() {
        // Mid-spin: selection alone paints; idle: focus alone still paints.
        assertTrue(ListWheelChromePolicy.rowHighlighted(true, false));
        assertTrue(ListWheelChromePolicy.rowHighlighted(false, true));
        assertTrue(ListWheelChromePolicy.rowHighlighted(true, true));
        assertFalse(ListWheelChromePolicy.rowHighlighted(false, false));
    }
}
