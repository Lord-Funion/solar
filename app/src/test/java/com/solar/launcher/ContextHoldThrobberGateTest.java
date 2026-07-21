package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-20 — Context-hold spinner must arm for every hold path (Back / OK / Power / A5 edge).
 * Layman: spinner while waiting for Options, no matter which button you hold.
 */
public class ContextHoldThrobberGateTest {

    @Test
    public void armWhenMenuClosed() {
        if (!ContextHoldThrobberGate.shouldArmOnHoldStart(false)) {
            throw new AssertionError("arm spinner when Options not yet open");
        }
    }

    @Test
    public void skipArmWhenMenuAlreadyShowing() {
        if (ContextHoldThrobberGate.shouldArmOnHoldStart(true)) {
            throw new AssertionError("no second arm while Options already painted");
        }
    }

    @Test
    public void clearWhenHoldCancelledBeforeMenu() {
        if (!ContextHoldThrobberGate.shouldClearOnHoldCancel(false)) {
            throw new AssertionError("clear spinner when finger lifts before Options");
        }
    }

    @Test
    public void keepBusyWhenMenuOpenedByThisHold() {
        // Power UP after fire must not wipe spinner mid-populate.
        if (ContextHoldThrobberGate.shouldClearOnHoldCancel(true)) {
            throw new AssertionError("do not cancel throbber after menu open started");
        }
    }
}
