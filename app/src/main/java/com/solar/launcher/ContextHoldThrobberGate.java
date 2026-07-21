package com.solar.launcher;

/**
 * 2026-07-20 — Pure gates for status-bar spinner during context-menu hold.
 * Layman: show the little spinner while Options is coming, for any hold button; hide if you let go early.
 * Tech: shared by Back/OK key paths, Y2 Power broadcast, A5 edge hold — menu paint clears separately.
 * Reversal: inline the booleans at each call site; drop this class.
 */
public final class ContextHoldThrobberGate {

    private ContextHoldThrobberGate() {}

    /**
     * 2026-07-20 — Arm spinner when a context-hold timer starts and Options is not already up.
     * Layman: start spinning as soon as you begin holding for the menu.
     */
    public static boolean shouldArmOnHoldStart(boolean menuAlreadyShowing) {
        return !menuAlreadyShowing;
    }

    /**
     * 2026-07-20 — Clear spinner only if this hold never opened Options.
     * Layman: finger up before the menu → spinner goes away; after open, leave it until paint finishes.
     */
    public static boolean shouldClearOnHoldCancel(boolean menuOpenedByThisHold) {
        return !menuOpenedByThisHold;
    }
}
