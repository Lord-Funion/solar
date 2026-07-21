package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Cold-start nav gate: block dial/OK until ready; volume stays live.
 */
public class StartupNavGateTest {

    @Test
    public void blocksWhenStartupOrFirstReady() {
        assertTrue(StartupNavGate.shouldBlockNavigation(true, false));
        assertTrue(StartupNavGate.shouldBlockNavigation(false, true));
        assertTrue(StartupNavGate.shouldBlockNavigation(true, true));
        assertFalse(StartupNavGate.shouldBlockNavigation(false, false));
    }

    @Test
    public void swallowsNavButNotVolume() {
        assertTrue(StartupNavGate.shouldSwallowKey(true, false));
        assertFalse(StartupNavGate.shouldSwallowKey(true, true));
        assertFalse(StartupNavGate.shouldSwallowKey(false, false));
        assertFalse(StartupNavGate.shouldSwallowKey(false, true));
    }
}
