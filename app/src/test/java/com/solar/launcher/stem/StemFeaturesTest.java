package com.solar.launcher.stem;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

/**
 * Opt-in gates for Stem / Mix / solo menus.
 * 2026-07-19
 */
public class StemFeaturesTest {

    /**
     * Unset pref + bundled demo → Stem menus On (demo key is meant to be used).
     * 2026-07-19 — Was: default off until toggle.
     */
    @Test
    public void unsetPrefOptsInWithBundledDemo() {
        MemPrefs prefs = new MemPrefs();
        assertTrue(LalalAccount.isStemFeaturesEnabled(prefs));
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertTrue(StemFeatures.showCloudStemMenus(prefs));
        assertTrue(StemFeatures.showSoloMenu(prefs, false));
        assertTrue(LalalAccount.hasUsableKey(prefs));
    }

    /**
     * Enable Stem features unlocks menus with bundled demo key (no user key).
     * 2026-07-19
     */
    @Test
    public void stemFeaturesToggleOptsInWithDemo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(LalalAccount.isStemFeaturesEnabled(prefs));
        assertFalse(LalalAccount.isUserConfigured(prefs));
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertTrue(StemFeatures.showCloudStemMenus(prefs));
        assertTrue(StemFeatures.showSoloMenu(prefs, false));
        assertTrue(LalalAccount.hasUsableKey(prefs));
    }

    /** User key unlocks Stem / Mix / solo. 2026-07-19 */
    @Test
    public void userKeyOptsIn() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.saveUserKey(prefs, "user-key-abcdefgh");
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertTrue(StemFeatures.showCloudStemMenus(prefs));
        assertTrue(StemFeatures.showSoloMenu(prefs, false));
        assertTrue(LalalAccount.isStemFeaturesEnabled(prefs));
    }

    /**
     * Pasting the bundled demo string opts in (does not require a different key).
     * 2026-07-19
     */
    @Test
    public void pasteDemoKeyOptsIn() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, false);
        assertFalse(StemFeatures.isOptedIn(prefs));
        LalalAccount.saveUserKey(prefs, LalalAccount.bundledDemoKey());
        assertFalse(LalalAccount.isUserConfigured(prefs));
        assertTrue(LalalAccount.isStemFeaturesEnabled(prefs));
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertTrue(LalalAccount.isBundledDemoKey(LalalAccount.bundledDemoKey()));
    }

    /** Clear / empty key also activates demo path. 2026-07-19 */
    @Test
    public void clearKeyActivatesDemo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.saveUserKey(prefs, "user-key-abcdefgh");
        assertTrue(LalalAccount.isUserConfigured(prefs));
        LalalAccount.saveUserKey(prefs, "");
        assertFalse(LalalAccount.isUserConfigured(prefs));
        assertTrue(LalalAccount.isStemFeaturesEnabled(prefs));
        assertTrue(StemFeatures.isOptedIn(prefs));
    }

    /** Turning off stem_features_enabled hides menus when no user key. 2026-07-19 */
    @Test
    public void disableStemFeaturesHidesMenus() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(StemFeatures.isOptedIn(prefs));
        LalalAccount.setStemFeaturesEnabled(prefs, false);
        assertFalse(StemFeatures.isOptedIn(prefs));
        assertFalse(StemFeatures.showCloudStemMenus(prefs, false));
        assertTrue(StemFeatures.showSoloMenu(prefs, true));
    }

    /**
     * A5: Stem Player / Mix face hidden; solo Instrumental/Vocals still offered when opted in.
     * 2026-07-20
     */
    @Test
    public void a5HidesStemFaceKeepsSolo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertFalse(StemFeatures.supportsStemPlayerFace(true));
        assertFalse(StemFeatures.showCloudStemMenus(prefs, true));
        assertTrue(StemFeatures.showCloudStemMenus(prefs, false));
        assertTrue(StemFeatures.showSoloMenu(prefs, false));
        assertTrue(StemFeatures.canOfferSoloMode(prefs, true, false, false));
    }

    /**
     * Offline + opted-in + nothing local → hide Instrumental/Acapella (no Wi‑Fi toast bait).
     * 2026-07-19
     */
    @Test
    public void offlineOptedInWithoutLocalHidesSolo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(StemFeatures.isOptedIn(prefs));
        assertFalse(StemFeatures.canOfferSoloMode(prefs, /*online*/ false,
                /*localReady*/ false, /*offlineSourceReady*/ false));
    }

    /** Offline + local ready → show even without opt-in. 2026-07-19 */
    @Test
    public void offlineLocalReadyShowsSolo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, false);
        assertFalse(StemFeatures.isOptedIn(prefs));
        assertTrue(StemFeatures.canOfferSoloMode(prefs, false, true, false));
    }

    /** Offline + bake/full-stem source → show without cloud. 2026-07-19 */
    @Test
    public void offlineBakeReadyShowsSolo() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, false);
        assertTrue(StemFeatures.canOfferSoloMode(prefs, false, false, true));
    }

    /** Online + opted-in + no local → show (cloud ensure allowed). 2026-07-19 */
    @Test
    public void onlineOptedInShowsSoloWithoutLocal() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(StemFeatures.canOfferSoloMode(prefs, true, false, false));
    }

    /** Online + not opted-in + no local → hide. 2026-07-19 */
    @Test
    public void onlineNotOptedInHidesSoloWithoutLocal() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, false);
        assertFalse(StemFeatures.canOfferSoloMode(prefs, true, false, false));
        assertTrue(StemFeatures.canOfferSoloMode(prefs, true, true, false));
    }

    /** Global Stems On/Off toggle in Now Playing hides cloud and solo menus when off. 2026-07-21 */
    @Test
    public void stemsGlobalToggleHidesCloudAndSoloMenus() {
        MemPrefs prefs = new MemPrefs();
        LalalAccount.setStemFeaturesEnabled(prefs, true);
        assertTrue(StemFeatures.isStemsGlobalEnabled(prefs));
        assertTrue(StemFeatures.showCloudStemMenus(prefs));
        assertTrue(StemFeatures.showSoloMenu(prefs, true));

        prefs.edit().putBoolean(StemFeatures.PREF_STEMS_GLOBAL_ENABLED, false).commit();
        assertFalse(StemFeatures.isStemsGlobalEnabled(prefs));
        assertFalse(StemFeatures.showCloudStemMenus(prefs));
        assertFalse(StemFeatures.showSoloMenu(prefs, true));
        assertFalse(StemFeatures.canOfferSoloMode(prefs, true, true, false));
        // isOptedIn stays true so the global toggle itself can still appear to turn it back on.
        assertTrue(StemFeatures.isOptedIn(prefs));
    }

    /** Minimal in-memory prefs for JVM tests. 2026-07-19 */
    private static final class MemPrefs implements SharedPreferences {
        private final Map<String, Object> map = new HashMap<String, Object>();

        @Override
        public Map<String, ?> getAll() {
            return map;
        }

        @Override
        public String getString(String key, String defValue) {
            Object v = map.get(key);
            return v instanceof String ? (String) v : defValue;
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            Object v = map.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            Object v = map.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            Object v = map.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            Object v = map.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }

        @Override
        public boolean contains(String key) {
            return map.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor() {
                @Override
                public Editor putString(String key, String value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putStringSet(String key, Set<String> values) {
                    return this;
                }

                @Override
                public Editor putInt(String key, int value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putLong(String key, long value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putFloat(String key, float value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor putBoolean(String key, boolean value) {
                    map.put(key, value);
                    return this;
                }

                @Override
                public Editor remove(String key) {
                    map.remove(key);
                    return this;
                }

                @Override
                public Editor clear() {
                    map.clear();
                    return this;
                }

                @Override
                public boolean commit() {
                    return true;
                }

                @Override
                public void apply() {}
            };
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
    }
}
