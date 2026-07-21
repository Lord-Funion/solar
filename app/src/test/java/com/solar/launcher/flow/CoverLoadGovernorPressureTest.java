package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Artwork pressure constants (Workstream E).
 * Layman: prove we still only decode one cover at a time and document the window caps.
 */
public class CoverLoadGovernorPressureTest {

    @Test
    public void concurrentDecodeCapIsOneOrTwo() {
        assertTrue(CoverLoadGovernor.MAX_CONCURRENT_DECODES >= 1);
        assertTrue(CoverLoadGovernor.MAX_CONCURRENT_DECODES <= 2);
        assertEquals(1, CoverLoadGovernor.MAX_CONCURRENT_DECODES);
    }

    @Test
    public void pressureDistanceIsFocusNeighborOnly() {
        assertEquals(1, CoverLoadGovernor.PRESSURE_MAX_DISTANCE);
    }

    @Test
    public void artWindowLruCapsMatchDesign() {
        assertEquals(32, FlowCoverCache.maxEntries());
        assertEquals(56, FlowCoverBakeCache.maxEntries());
    }

    @Test
    public void bakeKeySplitsCoverKey() {
        assertEquals("album:foo",
                FlowCoverBakeCache.coverKeyFromBakeKey("album:foo|120|R"));
        assertEquals(null, FlowCoverBakeCache.coverKeyFromBakeKey(null));
    }
}
