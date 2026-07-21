package com.solar.launcher.stem;

/**
 * Stem / Lalal prep progress copy + toast throttle (pure helpers).
 * Layman: tell the user what’s cooking without spamming every byte.
 * Technical: phase→registry key; milestone toast gates; status line format.
 * Was: one “Stems being prepared…” toast then silence. Reversal: ignore helpers.
 * 2026-07-21 — audio path unchanged (IO-only UI).
 */
public final class StemPrepProgressUi {
    /** Toast at most every N percent while same phase. 2026-07-21 */
    public static final int TOAST_PERCENT_STEP = 25;

    private StemPrepProgressUi() {}

    /**
     * Map Lalal phase string → queue prep registry key.
     * Layman: queue row / dial shows the right busy label.
     * 2026-07-21
     */
    public static String registryKeyForPhase(String phase) {
        if (phase == null || phase.length() == 0) return QueuePrepStatus.KEY_STEMS;
        if ("ready".equals(phase) || "idle".equals(phase)) return QueuePrepStatus.KEY_IDLE;
        if ("upload".equals(phase)) return QueuePrepStatus.KEY_DOWNLOADING;
        if ("download".equals(phase)) return QueuePrepStatus.KEY_DOWNLOADING;
        if ("split".equals(phase) || "vocals".equals(phase)) return QueuePrepStatus.KEY_VOCALS;
        if ("mix".equals(phase) || "publish".equals(phase)) return QueuePrepStatus.KEY_STEMS;
        if ("bake".equals(phase) || "instrumental".equals(phase)) {
            return QueuePrepStatus.KEY_BAKE_INSTRUMENTAL;
        }
        return QueuePrepStatus.KEY_STEMS;
    }

    /**
     * Human status line — phase + % + detail (same spirit as StemPlayerHost.phaseLabel).
     * 2026-07-21
     */
    public static String statusLine(String phase, int percent, String detail) {
        String head;
        if ("upload".equals(phase)) head = "Uploading";
        else if ("split".equals(phase)) head = "Separating stems";
        else if ("download".equals(phase)) head = "Downloading stems";
        else if ("mix".equals(phase)) head = "Mixing Melody pad";
        else if ("publish".equals(phase)) head = "Saving stems";
        else if ("ready".equals(phase)) head = "Ready";
        else if ("start".equals(phase)) head = "Loading stems";
        else if ("fail".equals(phase)) head = "Stem prep failed";
        else head = phase != null && phase.length() > 0 ? phase : "Loading stems";
        StringBuilder sb = new StringBuilder();
        sb.append(head);
        if (!"ready".equals(phase) && !"fail".equals(phase) && percent >= 0 && percent <= 100) {
            sb.append("… ").append(percent).append('%');
        }
        if (detail != null && detail.length() > 0
                && !"ready".equals(phase) && !"fail".equals(phase)) {
            sb.append(" · ").append(detail);
        }
        return sb.toString();
    }

    /**
     * Short toast line with optional track name prefix.
     * 2026-07-21
     */
    public static String toastLine(String trackName, String phase, int percent) {
        String base = statusLine(phase, percent, null);
        if (trackName == null || trackName.length() == 0) return base;
        String shortName = StemControls.stripTrackDisplayName(trackName);
        if (shortName.length() > 18) shortName = shortName.substring(0, 16) + "…";
        return shortName + " · " + base;
    }

    /**
     * Whether to fire a milestone toast (start / phase change / % step / done / fail).
     * Layman: ping at big steps, not every tick.
     * Was: toast only at begin. Reversal: always return false.
     * 2026-07-21
     */
    public static boolean shouldToastMilestone(String lastPhase, int lastPercent,
            String phase, int percent) {
        if (phase == null) return false;
        if ("ready".equals(phase) || "fail".equals(phase) || "start".equals(phase)) {
            return lastPhase == null || !phase.equals(lastPhase);
        }
        if (lastPhase == null || !phase.equals(lastPhase)) return true;
        if (percent < 0) return false;
        int lastBucket = lastPercent < 0 ? -1 : (lastPercent / TOAST_PERCENT_STEP);
        int bucket = percent / TOAST_PERCENT_STEP;
        return bucket > lastBucket;
    }

    /**
     * Marquee with live percent while busy (queue subtitle).
     * Layman: “Loading stems… 40%” on the queue row.
     * Was: static “Loading stems…”. Reversal: return marqueeFor(key) only.
     * 2026-07-21
     */
    public static String marqueeWithPercent(String prepKey, int percent) {
        String m = QueuePrepStatus.marqueeFor(prepKey);
        if (m.length() == 0) return "";
        if (percent < 0 || percent > 100) return m;
        if (m.endsWith("…")) {
            return m.substring(0, m.length() - 1) + " " + percent + "%…";
        }
        return m + " " + percent + "%";
    }
}
