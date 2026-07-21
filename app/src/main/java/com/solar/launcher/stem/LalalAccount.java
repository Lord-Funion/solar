package com.solar.launcher.stem;

import android.content.SharedPreferences;

/**
 * Lalal.ai license key — user prefs first, bundled demo like Deezer ARL.
 * Layman: ships a play-with-it key; Settings can replace it with yours.
 * Technical: never persists bundled key; PREF_USER_CONFIGURED gates “own key” copy.
 * 2026-07-18
 * 2026-07-19 — Demo key is a real opt-in path (paste / reset / Enable Stem features), not a
 * dead end that forces a different key before Stem / Mix unlock.
 */
public final class LalalAccount {
    public static final String PREFS_NAME = "SOLAR_SETTINGS";
    public static final String PREF_LICENSE_KEY = "lalal_license_key";
    public static final String PREF_USER_CONFIGURED = "lalal_user_key_configured";
    /**
     * Experimental: decode+blend Melody stems to one WAV before play.
     * Default off — StemMixer plays piano/guitars/residual together (synced gain/loop).
     * 2026-07-19
     */
    public static final String PREF_STEM_PREMIX_EXPERIMENTAL = "stem_premix_experimental";

    /**
     * Explicit Stem features opt-in (demo key path).
     * Layman: turn on to try Stem / Mix with the built-in demo key.
     * Technical: OR'd with {@link #isUserConfigured} in {@link StemFeatures#isOptedIn}.
     * 2026-07-19 — Default On while a bundled demo key ships (unset pref → demo active).
     * Was: default off until toggle. Reversal: getBoolean(..., false) and ignore contains().
     */
    public static final String PREF_STEM_FEATURES_ENABLED = "stem_features_enabled";

    /**
     * Bundled demo license — REMOVE / rotate before wide public launch.
     * Was: no Lalal integration. Reversal: clear constant + disable Stem Player entry.
     * 2026-07-18
     */
    private static final String BUNDLED_DEMO_KEY = "0aa329e86be9454b";

    private LalalAccount() {}

    /** True when the built-in demo key is present for silent fallback. */
    public static boolean hasBundledDemoKey() {
        return BUNDLED_DEMO_KEY != null && BUNDLED_DEMO_KEY.trim().length() >= 8;
    }

    /** Runtime-only demo key — never written to prefs. */
    public static String bundledDemoKey() {
        return hasBundledDemoKey() ? BUNDLED_DEMO_KEY.trim() : "";
    }

    /**
     * True when {@code key} is the bundled demo (trim + equals).
     * Layman: pasting the demo string still counts as “use demo,” not “own account.”
     * 2026-07-19
     */
    public static boolean isBundledDemoKey(String key) {
        if (!hasBundledDemoKey()) return false;
        String k = key != null ? key.trim() : "";
        return k.length() >= 8 && k.equals(bundledDemoKey());
    }

    /**
     * Settings → Media "Enable Stem features" — demo unlock without pasting a key.
     * 2026-07-19 — Unset pref defaults On when demo is bundled so the key is usable.
     */
    public static boolean isStemFeaturesEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (!prefs.contains(PREF_STEM_FEATURES_ENABLED)) {
            return hasBundledDemoKey();
        }
        return prefs.getBoolean(PREF_STEM_FEATURES_ENABLED, false);
    }

    /** Persist Stem features toggle. 2026-07-19 */
    public static void setStemFeaturesEnabled(SharedPreferences prefs, boolean on) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_STEM_FEATURES_ENABLED, on).commit();
    }

    /** Flip Stem features enable; returns new value. 2026-07-19 */
    public static boolean toggleStemFeaturesEnabled(SharedPreferences prefs) {
        if (prefs == null) return false;
        boolean next = !isStemFeaturesEnabled(prefs);
        setStemFeaturesEnabled(prefs, next);
        return next;
    }

    /** User pasted their own key in Settings (not the silent demo). */
    public static boolean isUserConfigured(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (!prefs.getBoolean(PREF_USER_CONFIGURED, false)) return false;
        String k = loadUserKey(prefs);
        return k.length() >= 8 && !isBundledDemoKey(k);
    }

    /** Session key: user key if configured, else bundled demo. */
    public static String effectiveKey(SharedPreferences prefs) {
        if (isUserConfigured(prefs)) return loadUserKey(prefs);
        return bundledDemoKey();
    }

    /** Can open Stem Player (demo or user key). */
    public static boolean hasUsableKey(SharedPreferences prefs) {
        String k = effectiveKey(prefs);
        return k != null && k.length() >= 8;
    }

    /**
     * Settings subtitle — own key vs demo active vs stem features off.
     * 2026-07-19 — Was: demo-on and demo-off both returned notConfigured (looked broken).
     */
    public static String settingsStatusLabel(SharedPreferences prefs, String notConfigured,
            String configured) {
        return settingsStatusLabel(prefs, notConfigured, configured, null);
    }

    /**
     * @param usingDemo label when Stem features On with bundled demo (may be null → notConfigured)
     * 2026-07-19
     */
    public static String settingsStatusLabel(SharedPreferences prefs, String notConfigured,
            String configured, String usingDemo) {
        if (isUserConfigured(prefs)) {
            return configured != null ? configured : "Configured";
        }
        if (isStemFeaturesEnabled(prefs) && hasBundledDemoKey()) {
            if (usingDemo != null && usingDemo.length() > 0) return usingDemo;
            return notConfigured != null ? notConfigured : "Using demo key";
        }
        return notConfigured != null ? notConfigured : "Not configured";
    }

    public static String loadUserKey(SharedPreferences prefs) {
        if (prefs == null) return "";
        String k = prefs.getString(PREF_LICENSE_KEY, "");
        return k != null ? k.trim() : "";
    }

    /**
     * Save user key from Settings keyboard / PC page.
     * Layman: paste your key, or paste/clear to the demo string to unlock Stem with the demo.
     * 2026-07-19 — Demo paste or clear → stem_features_enabled On (was: rejected as not configured).
     * Reversal: demo/empty only clear user key; do not set stem_features_enabled.
     */
    public static void saveUserKey(SharedPreferences prefs, String key) {
        if (prefs == null) return;
        String k = key != null ? key.trim() : "";
        SharedPreferences.Editor ed = prefs.edit();
        if (k.length() < 8 || isBundledDemoKey(k)) {
            ed.remove(PREF_LICENSE_KEY);
            ed.putBoolean(PREF_USER_CONFIGURED, false);
            // Demo path — empty clear or paste of demo string activates bundled key.
            if (hasBundledDemoKey()) {
                ed.putBoolean(PREF_STEM_FEATURES_ENABLED, true);
            }
        } else {
            ed.putString(PREF_LICENSE_KEY, k);
            ed.putBoolean(PREF_USER_CONFIGURED, true);
            ed.putBoolean(PREF_STEM_FEATURES_ENABLED, true);
        }
        ed.commit();
    }

    /**
     * Premix Melody to one file (slow on Y1). Off = live multi-player zone 3.
     * Was: always premix. Reversal: remove pref; always live or always premix.
     * 2026-07-19
     */
    public static boolean isPremixExperimental(SharedPreferences prefs) {
        return prefs != null && prefs.getBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, false);
    }

    /** Toggle experimental Melody premix; returns new value. 2026-07-19 */
    public static boolean togglePremixExperimental(SharedPreferences prefs) {
        if (prefs == null) return false;
        boolean next = !isPremixExperimental(prefs);
        prefs.edit().putBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, next).commit();
        return next;
    }

    public static void setPremixExperimental(SharedPreferences prefs, boolean on) {
        if (prefs == null) return;
        prefs.edit().putBoolean(PREF_STEM_PREMIX_EXPERIMENTAL, on).commit();
    }
}
