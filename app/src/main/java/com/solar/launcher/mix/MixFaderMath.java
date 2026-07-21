package com.solar.launcher.mix;

import com.solar.launcher.stem.StemControls;

/**
 * Pure Mix fader geometry + focus clamps — unit-testable without a View.
 * Layman: maths for where the volume knob sits and which deck is lit.
 * Technical: gain→Y mapping + deck index clamp for MixFaderFaceView / MixPlayerHost.
 * Was: StemFaceView LED arm mapping (zone+1). Reversal: restore puck paintFace mapping.
 * 2026-07-20
 */
public final class MixFaderMath {
    private MixFaderMath() {}

    /**
     * Clamp deck index into 0..DECK_COUNT-1; negative → 0.
     * Layman: keep the highlight on a real fader.
     * 2026-07-20
     */
    public static int clampFocusDeck(int deck) {
        if (deck < 0) return 0;
        if (deck >= MixSession.DECK_COUNT) return MixSession.DECK_COUNT - 1;
        return deck;
    }

    /**
     * True when deck is a valid Mix slot.
     * 2026-07-20
     */
    public static boolean isValidDeck(int deck) {
        return deck >= 0 && deck < MixSession.DECK_COUNT;
    }

    /**
     * Knob centre Y: gain 0 at track bottom, gain 1 at track top.
     * Layman: quiet sits low; loud rides high like a DJ fader.
     * Technical: linear map after StemControls.clampGain.
     * 2026-07-20
     */
    public static float knobCenterY(float gain, float trackTop, float trackBottom) {
        float g = StemControls.clampGain(gain);
        float span = trackBottom - trackTop;
        if (span <= 0f) return trackTop;
        return trackBottom - g * span;
    }

    /**
     * Fill height from bottom of track up to the knob (0..span).
     * 2026-07-20
     */
    public static float fillHeight(float gain, float trackTop, float trackBottom) {
        float g = StemControls.clampGain(gain);
        float span = trackBottom - trackTop;
        if (span <= 0f) return 0f;
        return g * span;
    }

    /**
     * Column centre X for deck i across full width (equal thirds).
     * 2026-07-20
     */
    public static float columnCenterX(int deck, float width) {
        int d = clampFocusDeck(deck);
        float colW = width / MixSession.DECK_COUNT;
        return colW * (d + 0.5f);
    }
}
