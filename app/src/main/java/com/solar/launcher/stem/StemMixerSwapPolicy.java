package com.solar.launcher.stem;

/**
 * Magical origin ↔ pads swap — matched playhead + short gain crossfade.
 * Layman: flip Stems on/off and the song keeps the same place without a jump.
 * Technical: clamp position; fade-out source / fade-in dest over SoloLayerGains.FADE_MS.
 * Was: hardCutToSoloFile / ensureSolo cut. Reversal: ignore — hard-cut again.
 * 2026-07-21
 */
public final class StemMixerSwapPolicy {

    private StemMixerSwapPolicy() {}

    /**
     * Playhead both sides must seek to before crossfade (clamped to duration).
     * 2026-07-21
     */
    public static int matchedPositionMs(int positionMs, int durationMs) {
        if (positionMs < 0) positionMs = 0;
        if (durationMs > 0 && positionMs > durationMs) {
            // Leave a tiny tail so completion logic still fires once. 2026-07-21
            positionMs = Math.max(0, durationMs - 50);
        }
        return positionMs;
    }

    /**
     * True when two clocks are close enough to call the swap gapless.
     * 2026-07-21
     */
    public static boolean positionsMatch(int aMs, int bMs, int toleranceMs) {
        int tol = toleranceMs < 0 ? 0 : toleranceMs;
        return Math.abs(aMs - bMs) <= tol;
    }

    /** Default match window for OMX seek jitter on Y1. 2026-07-21 */
    public static int defaultToleranceMs() {
        return 80;
    }

    /**
     * Origin feed starts at full when leaving pad mode (or entering origin-only).
     * 2026-07-21
     */
    public static float originGainAtSwapStart(boolean fadingInOrigin) {
        return fadingInOrigin ? 0f : 1f;
    }

    /**
     * Origin feed ends at full after fade-in, silent after fade-out.
     * 2026-07-21
     */
    public static float originGainAtSwapEnd(boolean fadingInOrigin) {
        return fadingInOrigin ? 1f : 0f;
    }

    /**
     * Pad zone targets at swap start (silent when fading pads in).
     * 2026-07-21
     */
    public static float[] padGainsAtSwapStart(boolean fadingInPads, boolean wantVocals,
            boolean wantInstr) {
        if (!fadingInPads) {
            return NpStemPadGains.targets(wantVocals, wantInstr);
        }
        return new float[] {0f, 0f, 0f, 0f};
    }

    /**
     * Pad zone targets after fade-in completes.
     * 2026-07-21
     */
    public static float[] padGainsAtSwapEnd(boolean fadingInPads, boolean wantVocals,
            boolean wantInstr) {
        if (!fadingInPads) {
            return new float[] {0f, 0f, 0f, 0f};
        }
        return NpStemPadGains.targets(wantVocals, wantInstr);
    }

    /** Crossfade length matches NP layer feel. 2026-07-21 */
    public static long crossfadeMs() {
        return SoloLayerGains.FADE_MS;
    }
}
