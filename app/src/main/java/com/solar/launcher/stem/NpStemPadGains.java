package com.solar.launcher.stem;

/**
 * Map NP Instrumentals/Vocals toggles onto StemMixer zone gains.
 * Layman: Vocals dial = voice pad; Instrumentals dial = drums+bass+melody together.
 * Technical: zone0 ↔ vocals; zones 1–3 ↔ instrumentals group; Melody stays in mix when instr on.
 * Was: TransportLayerPair 2-file gains. Reversal: ignore — use solo layer pair again.
 * 2026-07-21
 */
public final class NpStemPadGains {

    /** Vocals pad index in StemMixer. 2026-07-21 */
    public static final int ZONE_VOCALS = 0;
    /** Drums. 2026-07-21 */
    public static final int ZONE_DRUMS = 1;
    /** Bass. 2026-07-21 */
    public static final int ZONE_BASS = 2;
    /** Melody catch-all (premix). 2026-07-21 */
    public static final int ZONE_MELODY = 3;

    private NpStemPadGains() {}

    /**
     * Four zone targets for Vocals ✓ / Instrumentals ✓.
     * Whole song when both on (Melody included). Silent vocals when Instrumentals only.
     * 2026-07-21
     */
    public static float[] targets(boolean wantVocals, boolean wantInstr) {
        float v = SoloLayerGains.targetGain(wantVocals);
        float i = SoloLayerGains.targetGain(wantInstr);
        return new float[] {v, i, i, i};
    }

    /**
     * Gain for one zone under current layer prefs.
     * 2026-07-21
     */
    public static float targetForZone(int zone, boolean wantVocals, boolean wantInstr) {
        float[] t = targets(wantVocals, wantInstr);
        if (zone < 0 || zone >= t.length) return 0f;
        return t[zone];
    }

    /**
     * True when all four pads at full = reconstituted whole song.
     * 2026-07-21
     */
    public static boolean isFullSongMix(boolean wantVocals, boolean wantInstr) {
        return wantVocals && wantInstr;
    }
}
