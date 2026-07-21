package com.solar.launcher;

/**
 * 2026-07-20 — When cold-start should swallow menu keys and keep the status throbber.
 * Layman: until Solar is ready, the dial and OK do nothing so a half-built home is not confusing.
 * Technical: pure policy for UiBusy REASON_STARTUP + first-ready overlay; volume always allowed.
 * Reversal: always return false (keys work during paint again).
 */
public final class StartupNavGate {

    private StartupNavGate() {}

    /**
     * True while navigation must wait (startup busy and/or first-ready face still up).
     * 2026-07-20
     */
    public static boolean shouldBlockNavigation(boolean startupBusy, boolean firstReadyOverlayActive) {
        return startupBusy || firstReadyOverlayActive;
    }

    /**
     * Volume keys stay live during startup (safety / muscle memory).
     * Other nav / select / Back / media skip are swallowed.
     * 2026-07-20
     */
    public static boolean shouldSwallowKey(boolean blockNavigation, boolean isVolumeKey) {
        if (!blockNavigation) return false;
        return !isVolumeKey;
    }
}
