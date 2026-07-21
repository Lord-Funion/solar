package com.solar.launcher.stem;

/**
 * 2026-07-20 — When dual-pad layers may auto-start inside SolarTransport.
 * Layman: picking a song plays that song; Instrumental / Vocals is a separate choice.
 * Technical: gate for maybeEnterSoloLayerMixAfterPrepare — never auto-promote.
 * Was: after prepare, both stems → trySoloLayerMix while main engine still ran.
 * Reversal: return true from shouldAutoEnterAfterPrepare when bothStemsReady.
 */
public final class SoloLayerMixPolicy {

    private SoloLayerMixPolicy() {}

    /**
     * True only if prepareMusicTrack should enter transport layers when both stems exist.
     * Always false — dual mix starts only from Play Instrumental / Acapella / layer toggles.
     */
    public static boolean shouldAutoEnterAfterPrepare(boolean bothStemsReady) {
        return false;
    }

    /** Explicit context-menu solo entry remains enabled. */
    public static boolean allowsExplicitSoloEntry() {
        return true;
    }
}
