package com.solar.launcher.youtube.api;

import org.json.JSONArray;
import org.junit.Test;

/**
 * 2026-07-15 — Invidious adaptiveFormats audio pick (Save Audio path).
 * Layman: prove we prefer m4a soundtrack over video rows.
 */
public class InvidiousBackendTest {

    @Test
    public void pickBestAudioPrefersM4aOverWebm() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(obj("video/mp4", "https://cdn.example/v.mp4", 500000, "mp4"));
        arr.put(obj("audio/webm; codecs=\"opus\"", "https://cdn.example/a.webm", 160000, "webm"));
        arr.put(obj("audio/mp4; codecs=\"mp4a.40.2\"", "https://cdn.example/a.m4a", 128000, "m4a"));
        YoutubeBackend.AudioStream pick =
                InvidiousBackend.pickBestAudio(arr, "http://inv.example");
        if (pick == null) throw new AssertionError("expected audio");
        if (!"m4a".equals(pick.ext)) throw new AssertionError("ext=" + pick.ext);
        if (pick.url == null || !pick.url.contains("a.m4a")) {
            throw new AssertionError("url=" + pick.url);
        }
    }

    @Test
    public void pickBestAudioIgnoresVideoOnly() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(obj("video/mp4", "https://cdn.example/v.mp4", 500000, "mp4"));
        if (InvidiousBackend.pickBestAudio(arr, "http://inv.example") != null) {
            throw new AssertionError("video-only must yield null");
        }
    }

    @Test
    public void pickBestAudioNullSafe() {
        if (InvidiousBackend.pickBestAudio(null, "http://inv.example") != null) {
            throw new AssertionError("null formats → null");
        }
    }

    private static org.json.JSONObject obj(String type, String url, int bitrate, String container)
            throws Exception {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("type", type);
        o.put("url", url);
        o.put("bitrate", bitrate);
        o.put("container", container);
        return o;
    }
}
