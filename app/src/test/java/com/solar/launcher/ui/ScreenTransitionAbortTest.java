package com.solar.launcher.ui;

import org.junit.Test;

/**
 * 2026-07-19 — abort() must clear animating without requiring View instrumentation.
 */
public class ScreenTransitionAbortTest {

    @Test
    public void abortClearsAnimatingFlag() {
        // cancel() alone left animators running; abort() always clears the flag.
        ScreenTransition.cancel();
        ScreenTransition.abort();
        if (ScreenTransition.isAnimating()) {
            throw new AssertionError("abort must clear isAnimating");
        }
    }
}
