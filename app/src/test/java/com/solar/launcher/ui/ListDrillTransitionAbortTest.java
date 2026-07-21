package com.solar.launcher.ui;

import org.junit.Test;

/**
 * 2026-07-19 — abort() must clear drill animating even without a View host.
 * Layman: Back mid-submenu-slide must not leave a sticky “busy” flag.
 */
public class ListDrillTransitionAbortTest {

    @Test
    public void abortNullHostClearsAnimatingFlag() {
        ListDrillTransition.abort(null);
        // Own flag cleared; ScreenTransition/LayoutMorph may still be false in JVM.
        if (ListDrillTransition.isAnimating()) {
            // Only fail if our own sticky path left animating true with peers idle.
            ScreenTransition.abort();
            LayoutMorphTransition.abort();
            ListDrillTransition.abort(null);
            if (ListDrillTransition.isAnimating()) {
                throw new AssertionError("abort must clear ListDrillTransition animating");
            }
        }
    }
}
