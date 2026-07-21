package com.solar.launcher.stem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre-Save Stems batch policy — cook album/playlist picks; 4→2 vocals+instrumental bake.
 * Layman: cook stems in advance; when full pads exist, bake a karaoke instrumental and keep vocals.
 * Technical: filter needing cook; lossless warn via {@link StemMixLossless}; bake uses LalalClient.
 * Was: on-demand stem prep only at jam open. Reversal: drop Pre-Save menu; keep on-demand.
 * 2026-07-21
 */
public final class StemMixPreSavePolicy {
    private StemMixPreSavePolicy() {}

    /**
     * Files that still need full-stem cook (not already ready).
     * 2026-07-21
     */
    public static List<File> needingCook(File[] tracks, boolean[] readyByIndex) {
        List<File> out = new ArrayList<File>();
        if (tracks == null) return out;
        for (int i = 0; i < tracks.length; i++) {
            File f = tracks[i];
            if (f == null) continue;
            boolean ready = readyByIndex != null && i < readyByIndex.length && readyByIndex[i];
            if (!ready) out.add(f);
        }
        return out;
    }

    /**
     * After full stems land, also bake instrumental sibling + reuse vocals (4→2).
     * Layman: four stem pads become “vocals + band” for Now Playing Instrumental/Acapella.
     * Technical: caller runs {@link LalalClient#bakeInstrumentalFromFullStems} when true.
     * 2026-07-21
     */
    public static boolean shouldBakeInstrumentalAfterFullStems(boolean fullStemsReady,
            boolean instrumentalAlreadyReady) {
        return fullStemsReady && !instrumentalAlreadyReady;
    }

    /**
     * Progress marquee key while Pre-Save runs one track.
     * 2026-07-21
     */
    public static String prepKeyForPhase(String phase) {
        if (phase == null) return QueuePrepStatus.KEY_STEMS;
        String p = phase.toLowerCase();
        if (p.contains("bake") || p.contains("mix") || p.contains("instrumental")) {
            return QueuePrepStatus.KEY_BAKE_INSTRUMENTAL;
        }
        if (p.contains("vocal")) return QueuePrepStatus.KEY_VOCALS;
        if (p.contains("download")) return QueuePrepStatus.KEY_DOWNLOADING;
        return QueuePrepStatus.KEY_STEMS;
    }

    /** Batch empty → nothing to do. 2026-07-21 */
    public static boolean isNoop(List<File> needingCook) {
        return needingCook == null || needingCook.isEmpty();
    }
}
