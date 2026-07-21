package com.solar.launcher.stem;

import java.io.File;

/**
 * Pure math for NP Instrumentals/Vocals dual-pad mix (same dial feel as Stem Player).
 * Layman: each layer is a volume fader; never mute both at once.
 * Technical: target gains for vocals/instr toggles + linear fade step.
 * Was: hardCutToSoloFile swap. Reversal: ignore — use single-file cut again.
 * 2026-07-20
 */
public final class SoloLayerGains {
    /** Match StemControls fade feel for pad mute (~200ms). 2026-07-20 */
    public static final long FADE_MS = StemControls.TEMP_GAIN_FADE_MS;
    public static final int FADE_TICK_MS = 32;

    private SoloLayerGains() {}

    /**
     * Target gain for a layer given on/off (full or silent).
     * 2026-07-20
     */
    public static float targetGain(boolean layerOn) {
        return layerOn ? 1f : 0f;
    }

    /**
     * True when the single deck is already a vocals/instrumental stem file.
     * Layman: playing acapella or band alone — not the full song.
     * Technical: absolute-path equals either stem load file (sibling enter → hard-swap).
     * Was: no check; enterLayers always crossfaded → doubled stem. Reversal: drop helper use.
     * 2026-07-21
     */
    public static boolean isActiveStemSibling(File activePath, File vocalsStem, File instrumentalStem) {
        if (activePath == null) return false;
        String ap = activePath.getAbsolutePath();
        if (ap == null || ap.length() == 0) return false;
        if (vocalsStem != null && ap.equals(vocalsStem.getAbsolutePath())) return true;
        if (instrumentalStem != null && ap.equals(instrumentalStem.getAbsolutePath())) return true;
        return false;
    }

    /**
     * Map wantVocals + wantInstr → SoloMode for UI chrome (null = both on = “full”).
     * Never both off — returns previousMode unchanged if invalid.
     * 2026-07-20
     */
    public static SoloMode modeForLayers(boolean wantVocals, boolean wantInstr, SoloMode previousMode) {
        if (!wantVocals && !wantInstr) return previousMode;
        if (wantVocals && wantInstr) return null;
        if (!wantVocals && wantInstr) return SoloMode.INSTRUMENTAL;
        return SoloMode.ACAPELLA;
    }

    /**
     * One fade tick: move current toward target by step sized for FADE_MS / FADE_TICK_MS.
     * 2026-07-20
     */
    public static float stepToward(float current, float target) {
        float c = StemControls.clampGain(current);
        float t = StemControls.clampGain(target);
        int ticks = Math.max(1, (int) (FADE_MS / FADE_TICK_MS));
        float step = 1f / ticks;
        if (c < t) {
            float n = c + step;
            return n >= t ? t : n;
        }
        if (c > t) {
            float n = c - step;
            return n <= t ? t : n;
        }
        return t;
    }

    /** True when fade has landed (within silent epsilon of target). 2026-07-20 */
    public static boolean fadeDone(float current, float target) {
        return Math.abs(StemControls.clampGain(current) - StemControls.clampGain(target))
                <= StemControls.SILENT_GAIN;
    }
}
