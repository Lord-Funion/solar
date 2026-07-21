package com.solar.launcher.youtube.api;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-15 — Persisted Invidious/Piped/YtApiLegacy instance lists for Solar YouTube.
 * Layman: remembers which public YouTube frontends to try after we refresh the list.
 * Technical: SharedPreferences string lists; seeds match notPipe Config.applyDefaults +.
 * Reversal: clear prefs solar_youtube_instances; InstancePool rebuilds seeds only.
 */
public final class InstancesConfig {

    private static final String PREFS = "solar_youtube_instances";
    private static final String KEY_INVIDIOUS = "invidious";
    private static final String KEY_PIPED = "piped";
    private static final String KEY_YTAPI = "ytapilegacy";
    private static final String KEY_LAST_UPDATE = "last_update_ms";
    private static final String KEY_UPDATE_URL = "update_url";

    /** Same default updater URL as notPipe 0.3.0. */
    public static final String DEFAULT_UPDATE_URL = "http://144.31.189.129/notPipe.json";

    /** notPipe seed HQ host when lists empty. */
    public static final String DEFAULT_YTAPI = "http://45.132.96.44:2823";

    // 2026-07-15 — Fail-open Invidious seeds when remote JSON unreachable.
    // Prefer http:// so Y2/A5 still resolve when system CA / Conscrypt trust drifts (2026-07-19).
    private static final String[] SEED_INVIDIOUS = new String[] {
            "http://76.82.152.76:3000",
            "http://82.65.13.217:7601",
            "http://87.106.60.151:3000"
    };

    /** 2026-07-19 — Package seeds for InstancesUpdater merge (HTTP fail-open). */
    public static List<String> seedInvidious() {
        return listOf(SEED_INVIDIOUS);
    }

    /** 2026-07-19 — YtApi legacy seed host(s) for merge after remote refresh. */
    public static List<String> seedYtApi() {
        return listOf(new String[] { DEFAULT_YTAPI });
    }

    private final SharedPreferences prefs;

    public InstancesConfig(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getUpdateUrl() {
        return prefs.getString(KEY_UPDATE_URL, DEFAULT_UPDATE_URL);
    }

    public long getLastUpdateMs() {
        return prefs.getLong(KEY_LAST_UPDATE, 0L);
    }

    public List<String> getInvidious() {
        return readList(KEY_INVIDIOUS, SEED_INVIDIOUS);
    }

    public List<String> getPiped() {
        return readList(KEY_PIPED, new String[0]);
    }

    public List<String> getYtApiLegacy() {
        return readList(KEY_YTAPI, new String[] { DEFAULT_YTAPI });
    }

    /** Replace all three lists from a successful remote update. */
    public void saveLists(List<String> invidious, List<String> piped, List<String> ytapi) {
        SharedPreferences.Editor ed = prefs.edit();
        ed.putString(KEY_INVIDIOUS, toJson(invidious));
        ed.putString(KEY_PIPED, toJson(piped));
        ed.putString(KEY_YTAPI, toJson(ytapi));
        ed.putLong(KEY_LAST_UPDATE, System.currentTimeMillis());
        ed.commit();
    }

    /** Ensure non-empty seed lists exist (first run or wiped prefs). */
    public void ensureSeeds() {
        if (!prefs.contains(KEY_YTAPI)) {
            saveLists(listOf(SEED_INVIDIOUS), new ArrayList<String>(),
                    listOf(new String[] { DEFAULT_YTAPI }));
        }
    }

    private List<String> readList(String key, String[] seed) {
        String raw = prefs.getString(key, null);
        if (raw == null || raw.length() == 0) {
            return listOf(seed);
        }
        try {
            JSONArray arr = new JSONArray(raw);
            List<String> out = new ArrayList<String>();
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s.length() > 0) out.add(s);
            }
            if (out.isEmpty()) return listOf(seed);
            return out;
        } catch (Exception e) {
            return listOf(seed);
        }
    }

    private static List<String> listOf(String[] seed) {
        List<String> out = new ArrayList<String>();
        if (seed == null) return out;
        for (int i = 0; i < seed.length; i++) {
            if (seed[i] != null && seed[i].length() > 0) out.add(seed[i]);
        }
        return out;
    }

    private static String toJson(List<String> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) != null) arr.put(list.get(i));
            }
        }
        return arr.toString();
    }
}
