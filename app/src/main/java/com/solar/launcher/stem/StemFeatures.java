package com.solar.launcher.stem;

import android.content.SharedPreferences;

import com.solar.launcher.DeviceFeatures;

/**
 * 2026-07-19 — Opt-in gate + active cloud stem provider.
 * Layman: Stem / Mix stay hidden until you enable Stem features or paste your own API key.
 * Technical: {@link LalalAccount#isStemFeaturesEnabled} OR {@link LalalAccount#isUserConfigured}.
 * Was: user key only. Reversal: gate on isUserConfigured alone (ignore stem_features_enabled).
 * 2026-07-20 — A5: Instrumental/Vocals OK; Gen1 Stem Player / Mix face hidden (no pad input map).
 */
public final class StemFeatures {

    public static final String PROVIDER_LALAL = "lalal";

    private StemFeatures() {}

    public static final String PREF_STEMS_GLOBAL_ENABLED = "stems_global_enabled";

    public static boolean isStemsGlobalEnabled(SharedPreferences prefs) {
        return prefs == null || prefs.getBoolean(PREF_STEMS_GLOBAL_ENABLED, true);
    }

    /**
     * Opted in via Enable Stem features (demo) or a pasted user key.
     * 2026-07-19
     */
    public static boolean isOptedIn(SharedPreferences prefs) {
        return LalalAccount.isStemFeaturesEnabled(prefs) || LalalAccount.isUserConfigured(prefs);
    }

    /**
     * Active separator — LALAL when opted in; null otherwise.
     * Future: prefs pick provider id + per-vendor keys.
     * 2026-07-19
     */
    public static StemSeparatorProvider activeProvider(SharedPreferences prefs) {
        if (!isOptedIn(prefs)) return null;
        String key = LalalAccount.effectiveKey(prefs);
        if (key == null || key.length() < 8) return null;
        return new LalalStemSeparator(key);
    }

    /**
     * True when this device can run the Gen1 Stem Player / Mix face (wheel pad map).
     * Layman: A5 is touch — no Kano-style pad facsimile; Y1/Y2 keep the full jam UI.
     * Technical: !{@link DeviceFeatures#isA5()}. Reversal: always return true.
     * 2026-07-20
     */
    public static boolean supportsStemPlayerFace() {
        return supportsStemPlayerFace(DeviceFeatures.isA5());
    }

    /**
     * Testable face-support gate (pass A5 flag without DeviceFeatures).
     * 2026-07-20
     */
    public static boolean supportsStemPlayerFace(boolean a5Device) {
        return !a5Device;
    }

    /**
     * Show Stem Player / Mix rows — opt-in and a device with pad input (not A5).
     * Was: isOptedIn alone (A5 could open Stem face with no usable keymap).
     * Reversal: return isOptedIn(prefs) only.
     * 2026-07-19 / 2026-07-20
     */
    public static boolean showCloudStemMenus(SharedPreferences prefs) {
        return showCloudStemMenus(prefs, DeviceFeatures.isA5());
    }

    /**
     * Testable Stem Player / Mix menu gate.
     * 2026-07-20
     */
    public static boolean showCloudStemMenus(SharedPreferences prefs, boolean a5Device) {
        return supportsStemPlayerFace(a5Device) && isOptedIn(prefs) && isStemsGlobalEnabled(prefs);
    }

    /**
     * Show Play Instrumental / Acapella — opt-in, or local cache already present.
     * Online-only helper; prefer {@link #canOfferSoloMode} when offline matters.
     * A5 included — layer toggles do not need the Stem Player face.
     * 2026-07-19
     */
    public static boolean showSoloMenu(SharedPreferences prefs, boolean localReady) {
        return isStemsGlobalEnabled(prefs) && (localReady || isOptedIn(prefs));
    }

    /**
     * Whether Play Instrumental / Acapella should appear for this mode.
     * Layman: online — show if file ready or Stem features on; offline — only if already on disk or bakeable.
     * Technical: localReady always wins; online → opted-in; offline → offlineSourceReady only.
     * Was: showSoloMenu = local || opted-in even offline (Wi‑Fi toast loop). Reversal: that OR.
     * 2026-07-19
     */
    public static boolean canOfferSoloMode(SharedPreferences prefs, boolean online,
            boolean localReady, boolean offlineSourceReady) {
        if (!isStemsGlobalEnabled(prefs)) return false;
        if (localReady) return true;
        if (online) return isOptedIn(prefs);
        return offlineSourceReady;
    }
}
