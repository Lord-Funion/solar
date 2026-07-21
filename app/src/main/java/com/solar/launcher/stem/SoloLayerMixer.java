package com.solar.launcher.stem;

/**
 * Retired dual MediaPlayer mixer — Instrumental/Acapella pads now live in
 * {@link com.solar.launcher.audio.TransportLayerPair} inside SolarTransport.
 * Layman: this empty shell keeps old imports from breaking while we delete call sites.
 * Was: two STREAM_MUSIC players + releaseOwnership handoff. Reversal: restore full mixer body
 * from git history (pre transport-layer ownership) and MainActivity trySoloLayerMix handoff.
 * 2026-07-20
 */
@Deprecated
public final class SoloLayerMixer {
    /** @deprecated Use TransportLayerPair.LAYER_VOCALS. 2026-07-20 */
    public static final int LAYER_VOCALS = 0;
    /** @deprecated Use TransportLayerPair.LAYER_INSTR. 2026-07-20 */
    public static final int LAYER_INSTR = 1;

    private SoloLayerMixer() {}
}
