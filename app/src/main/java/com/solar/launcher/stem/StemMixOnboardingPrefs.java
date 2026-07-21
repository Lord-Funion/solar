package com.solar.launcher.stem;

import android.content.SharedPreferences;

/**
 * Stem/Mix queue + hold onboarding prefs — Don't show again + Settings re-show.
 * Layman: first jam gets a short tip tour; you can hide it or open Help to see it again.
 * Technical: SharedPreferences booleans; journey pages are host-owned glyphs.
 * Was: only Mix fader/assign tips. Reversal: ignore these prefs; no queue journey.
 * 2026-07-21
 */
public final class StemMixOnboardingPrefs {
    /** Full Stem/Mix queue journey seen (or Don't show again). 2026-07-21 */
    public static final String PREF_QUEUE_JOURNEY_SEEN = "stem_mix_queue_journey_seen";
    /** Scrub tip seen once. 2026-07-21 */
    public static final String PREF_SCRUB_TIP_SEEN = "stem_mix_scrub_tip_seen";
    /** Pre-Save tip seen once. 2026-07-21 */
    public static final String PREF_PRESAVE_TIP_SEEN = "stem_mix_presave_tip_seen";

    /** Journey page ids for host carousel. 2026-07-21 */
    public static final int PAGE_QUEUE = 0;
    public static final int PAGE_HOLD_SCRUB = 1;
    public static final int PAGE_DUAL_HOLD = 2;
    public static final int PAGE_FOOTER_ADD = 3;
    public static final int PAGE_PRESAVE = 4;
    public static final int PAGE_COUNT = 5;

    private StemMixOnboardingPrefs() {}

    /** True when first-open journey should show. 2026-07-21 */
    public static boolean needsQueueJourney(SharedPreferences prefs) {
        if (prefs == null) return false;
        return !prefs.getBoolean(PREF_QUEUE_JOURNEY_SEEN, false);
    }

    /** Mark journey done (Got it or Don't show again). 2026-07-21 */
    public static void markQueueJourneySeen(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_QUEUE_JOURNEY_SEEN, true).apply();
    }

    /**
     * Settings → Help re-show: clear seen so next Stem/Mix open runs the journey.
     * Layman: turn the tips back on from Help.
     * 2026-07-21
     */
    public static void reShowQueueJourney(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
                .putBoolean(PREF_QUEUE_JOURNEY_SEEN, false)
                .putBoolean(PREF_SCRUB_TIP_SEEN, false)
                .putBoolean(PREF_PRESAVE_TIP_SEEN, false)
                .apply();
    }

    public static boolean needsScrubTip(SharedPreferences prefs) {
        if (prefs == null) return false;
        return !prefs.getBoolean(PREF_SCRUB_TIP_SEEN, false);
    }

    public static void markScrubTipSeen(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_SCRUB_TIP_SEEN, true).apply();
    }

    public static boolean needsPreSaveTip(SharedPreferences prefs) {
        if (prefs == null) return false;
        return !prefs.getBoolean(PREF_PRESAVE_TIP_SEEN, false);
    }

    public static void markPreSaveTipSeen(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_PRESAVE_TIP_SEEN, true).apply();
    }

    /**
     * Short page copy for journey (no glyphs — host may prepend HardwareButtonGlyph).
     * 2026-07-21
     */
    public static String pageProse(int page) {
        if (page == PAGE_QUEUE) {
            return "Your playlist is the play queue — Stem and Mix use the same list.";
        }
        if (page == PAGE_HOLD_SCRUB) {
            return "Hold Prev or Next to swap that track or Scrub with a soft blend.";
        }
        if (page == PAGE_DUAL_HOLD) {
            return "Hold Prev and Next together for Play queue, TRANSITION length, or Exit.";
        }
        if (page == PAGE_FOOTER_ADD) {
            return "In the queue, the last row Add song appends more tracks.";
        }
        if (page == PAGE_PRESAVE) {
            return "Pre-Save Stems cooks tracks ahead so jams start faster.";
        }
        return "";
    }
}
