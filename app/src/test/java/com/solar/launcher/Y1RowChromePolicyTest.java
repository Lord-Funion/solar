package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Selection decoration follows setSelected (lockstep with item arrow), not focus alone.
 */
public class Y1RowChromePolicyTest {

    @Test
    public void selectedOrPressedShowsChrome() {
        assertTrue(Y1RowChromePolicy.showsSelectedChrome(true, false));
        assertTrue(Y1RowChromePolicy.showsSelectedChrome(false, true));
        assertFalse(Y1RowChromePolicy.showsSelectedChrome(false, false));
    }

    @Test
    public void focusDoesNotOwnDecoration() {
        // Home mid-spin: arrow via visibility + setSelected; requestFocus waits for idle.
        assertFalse(Y1RowChromePolicy.focusOwnsDecoration());
    }

    @Test
    public void selectedChromeStatesAreSelectedThenPressed() {
        int[][] states = Y1RowChromePolicy.selectedChromeStates();
        assertEquals(2, states.length);
        assertEquals(android.R.attr.state_selected, states[0][0]);
        assertEquals(android.R.attr.state_pressed, states[1][0]);
        for (int[] s : states) {
            for (int attr : s) {
                assertFalse("must not key decoration on focus alone",
                        attr == android.R.attr.state_focused);
            }
        }
    }
}
