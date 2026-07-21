package com.solar.launcher;

import android.content.ComponentCallbacks2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 2026-07-16/20 — Pure threshold tests for 256MB-class RAM gate (no Robolectric). */
public class LowMemoryGateTest {

    @Test
    public void roomyDeviceNotPressured() {
        assertFalse(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void lowAvailMemIsPressured() {
        // Under default 28MB floor.
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                20L * 1024L * 1024L,
                80L * 1024L * 1024L,
                false,
                -1));
    }

    /** 2026-07-20 — ~50MB avail is healthy on 256MB/~64MB-avail (was pressured under 48MB floor). */
    @Test
    public void midAvailNotPressuredOnDefaultFloor() {
        assertFalse(LowMemoryGate.isPressuredSnapshot(
                50L * 1024L * 1024L,
                80L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void lowMemFreeIsPressured() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                100L * 1024L * 1024L,
                8L * 1024L * 1024L,
                false,
                -1));
    }

    @Test
    public void systemLowMemoryFlag() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                true,
                -1));
    }

    @Test
    public void trimRunningLow() {
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                200L * 1024L * 1024L,
                100L * 1024L * 1024L,
                false,
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
    }

    /** 2026-07-20 — 256MB total → ~26MB floor (10%, clamped). */
    @Test
    public void availFloorTracksTotalMemFraction() {
        assertEquals(20L * 1024 * 1024, LowMemoryGate.availFloorBytes(150L * 1024 * 1024));
        long floor256 = LowMemoryGate.availFloorBytes(256L * 1024 * 1024);
        assertTrue(floor256 >= LowMemoryGate.AVAIL_FLOOR_MIN_BYTES);
        assertTrue(floor256 <= LowMemoryGate.AVAIL_FLOOR_MAX_BYTES);
        assertTrue(LowMemoryGate.isPressuredSnapshot(
                floor256 - 1L, 80L * 1024 * 1024, false, -1, 256L * 1024 * 1024));
        assertFalse(LowMemoryGate.isPressuredSnapshot(
                floor256 + 1L, 80L * 1024 * 1024, false, -1, 256L * 1024 * 1024));
    }

    @Test
    public void floorsAndDeferAreRetuned() {
        assertEquals(28L * 1024 * 1024, LowMemoryGate.AVAIL_FLOOR_BYTES);
        assertEquals(16L * 1024 * 1024, LowMemoryGate.MEMFREE_FLOOR_BYTES);
        assertEquals(6_000L, LowMemoryGate.DEFER_MS);
        assertTrue(LowMemoryGate.DEFER_MS < 12_000L);
    }
}
