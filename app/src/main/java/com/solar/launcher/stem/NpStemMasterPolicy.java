package com.solar.launcher.stem;

/**
 * NP Stems master + Instrumentals/Vocals menu rules.
 * Layman: Stems off = full original song; Stems on unlocks voice/band toggles.
 * Technical: never both layers off while master on; layers hidden when master off.
 * Was: always show Instrumentals/Vocals (2-file solo). Reversal: drop master gate.
 * 2026-07-21
 */
public final class NpStemMasterPolicy {

    private NpStemMasterPolicy() {}

    /**
     * Show Instrumentals/Vocals rows only when Stems master is on.
     * 2026-07-21
     */
    public static boolean showLayerToggles(boolean stemsMasterOn) {
        return stemsMasterOn;
    }

    /**
     * At least one of Instrumentals/Vocals must stay checked while Stems is on.
     * Layman: ignore a tap that would mute the whole mix.
     * Technical: reject wantVocals=false && wantInstr=false when master on.
     * 2026-07-21
     */
    public static boolean allowLayerToggle(boolean stemsMasterOn, boolean wantVocals,
            boolean wantInstr) {
        if (!stemsMasterOn) return false;
        return wantVocals || wantInstr;
    }

    /**
     * Clamp layer prefs after a toggle — keep previous if both would go off.
     * 2026-07-21
     */
    public static boolean[] clampLayers(boolean stemsMasterOn, boolean wantVocals,
            boolean wantInstr, boolean prevVocals, boolean prevInstr) {
        if (!stemsMasterOn) {
            return new boolean[] {true, true};
        }
        if (!wantVocals && !wantInstr) {
            return new boolean[] {prevVocals, prevInstr};
        }
        return new boolean[] {wantVocals, wantInstr};
    }

    /**
     * Stems off always means origin file (ignore layer mutes).
     * 2026-07-21
     */
    public static boolean playOriginFile(boolean stemsMasterOn) {
        return !stemsMasterOn;
    }

    /**
     * Stems on means 4-pad mix when pads exist.
     * 2026-07-21
     */
    public static boolean playPadMix(boolean stemsMasterOn, boolean padsReady) {
        return stemsMasterOn && padsReady;
    }

    /**
     * Need multistem cook when master turns on and pads are missing.
     * 2026-07-21
     */
    public static boolean needsCook(boolean stemsMasterOn, boolean padsReady) {
        return stemsMasterOn && !padsReady;
    }
}
