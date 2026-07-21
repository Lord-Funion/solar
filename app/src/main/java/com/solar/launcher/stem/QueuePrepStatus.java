package com.solar.launcher.stem;

/**
 * Queue row prep marquees — Music / Stem / Mix / solo share the same copy.
 * Layman: while a song is cooking or downloading, the queue line says what’s happening.
 * Technical: status keys → short secondary-colour marquee strings; clear when idle.
 * Was: no per-row prep text (corner throbber only). Reversal: return "" always.
 * 2026-07-21
 */
public final class QueuePrepStatus {
    public static final String KEY_IDLE = "idle";
    public static final String KEY_STEMS = "stems";
    public static final String KEY_VOCALS = "vocals";
    public static final String KEY_INSTRUMENTAL = "instrumental";
    public static final String KEY_BAKE_INSTRUMENTAL = "bake_instrumental";
    public static final String KEY_BUFFERING = "buffering";
    public static final String KEY_DOWNLOADING = "downloading";
    public static final String KEY_PREPARING_MIX = "preparing_mix";

    private QueuePrepStatus() {}

    /**
     * Human marquee for a prep key (empty = idle / clear subtitle overlay).
     * 2026-07-21
     */
    public static String marqueeFor(String key) {
        if (key == null || key.length() == 0 || KEY_IDLE.equals(key)) return "";
        if (KEY_STEMS.equals(key)) return "Loading stems…";
        if (KEY_VOCALS.equals(key)) return "Separating vocals…";
        if (KEY_INSTRUMENTAL.equals(key)) return "Separating instrumental…";
        if (KEY_BAKE_INSTRUMENTAL.equals(key)) return "Baking instrumental…";
        if (KEY_BUFFERING.equals(key)) return "Buffering…";
        if (KEY_DOWNLOADING.equals(key)) return "Downloading…";
        if (KEY_PREPARING_MIX.equals(key)) return "Preparing for mix…";
        return "";
    }

    /**
     * Merge prep marquee into a queue subtitle (prep wins when non-empty).
     * Layman: show “Loading stems…” instead of the folder name while busy.
     * 2026-07-21
     */
    public static String mergeSubtitle(String baseSubtitle, String prepKey) {
        String m = marqueeFor(prepKey);
        if (m.length() > 0) return m;
        return baseSubtitle != null ? baseSubtitle : "";
    }

    /** True when row should show busy chrome / jam-face throbber mirror. 2026-07-21 */
    public static boolean isBusy(String key) {
        String m = marqueeFor(key);
        return m.length() > 0;
    }
}
