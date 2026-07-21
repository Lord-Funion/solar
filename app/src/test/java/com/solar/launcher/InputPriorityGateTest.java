package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Input-over-background idle window pure logic.
 * 2026-07-20 — IDLE_MS dialed 3000→1500; keep above ScrollIdleGate dial contract.
 */
public class InputPriorityGateTest {

    @Test
    public void defersWithinIdleWindow() {
        long t0 = 1_000_000L;
        long idle = InputPriorityGate.IDLE_MS;
        if (!InputPriorityGate.shouldDefer(t0 + 500L, t0)) {
            throw new AssertionError("must defer at 0.5s idle");
        }
        if (!InputPriorityGate.shouldDefer(t0 + idle - 1L, t0)) {
            throw new AssertionError("must defer just under IDLE_MS");
        }
        if (InputPriorityGate.shouldDefer(t0 + idle, t0)) {
            throw new AssertionError("must allow at IDLE_MS boundary");
        }
        if (InputPriorityGate.shouldDefer(t0 + 10_000L, t0)) {
            throw new AssertionError("must allow after long idle");
        }
    }

    @Test
    public void msUntilAllowed() {
        long t0 = 5_000L;
        long idle = InputPriorityGate.IDLE_MS;
        long wait = InputPriorityGate.msUntilAllowed(t0 + 1000L, t0);
        if (wait != idle - 1000L) {
            throw new AssertionError("wait=" + wait + " expected=" + (idle - 1000L));
        }
        if (InputPriorityGate.msUntilAllowed(t0 + idle, t0) != 0L) {
            throw new AssertionError("zero at boundary");
        }
        if (InputPriorityGate.msUntilAllowed(t0, 0L) != 0L) {
            throw new AssertionError("unset interaction allows now");
        }
    }

    @Test
    public void nextPeriodMsQuietVsBusy() {
        long t0 = 10_000L;
        long idle = InputPriorityGate.IDLE_MS;
        long quiet = InputPriorityGate.nextPeriodMs(t0 + idle + 2000L, t0, 500L, 250L);
        if (quiet != 500L) {
            throw new AssertionError("quiet period=" + quiet);
        }
        long busy = InputPriorityGate.nextPeriodMs(t0 + 100L, t0, 500L, 250L);
        // Remaining quiet window (~IDLE−100) floored at busyMinMs.
        if (busy < idle - 200L) {
            throw new AssertionError("busy should wait ~" + (idle - 100L) + "ms, got " + busy);
        }
    }

    @Test
    public void idleMsStaysAboveDialPaintFloor() {
        // 2026-07-20 — Must not fight index-first dial (ScrollIdleGate ~180–220ms).
        if (InputPriorityGate.IDLE_MS < 1000L) {
            throw new AssertionError("IDLE_MS too aggressive vs dial");
        }
        if (InputPriorityGate.IDLE_MS > 2500L) {
            throw new AssertionError("IDLE_MS not dialed back enough");
        }
    }
}
