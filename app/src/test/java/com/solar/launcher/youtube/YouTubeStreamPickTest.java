package com.solar.launcher.youtube;

import com.solar.launcher.youtube.api.InvidiousBackend;
import com.solar.launcher.youtube.api.PipedBackend;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 2026-07-16 — Quality-aware progressive stream picks for Y1 360 / A5 240. */
public class YouTubeStreamPickTest {

    @Test
    public void invidiousPicksExact360Over720() throws Exception {
        JSONArray arr = new JSONArray();
        arr.put(stream("https://x/720.mp4", "720p", 720, "video/mp4"));
        arr.put(stream("https://x/360.mp4", "360p", 360, "video/mp4"));
        arr.put(stream("https://x/240.mp4", "240p", 240, "video/mp4"));
        String url = InvidiousBackend.pickFormatStreamUrl(arr, "360");
        if (url == null || !url.contains("360")) {
            throw new AssertionError("expected 360 pick, got " + url);
        }
    }

    @Test
    public void pipedPrefersMuxedNearQuality() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject vo = new JSONObject();
        vo.put("url", "https://x/onlyvideo");
        vo.put("videoOnly", true);
        vo.put("quality", "720p");
        vo.put("height", 720);
        vo.put("mimeType", "video/mp4");
        arr.put(vo);
        JSONObject mux = new JSONObject();
        mux.put("url", "https://x/mux360");
        mux.put("videoOnly", false);
        mux.put("quality", "360p");
        mux.put("height", 360);
        mux.put("mimeType", "video/mp4");
        arr.put(mux);
        String url = PipedBackend.pickPipedVideoUrl(arr, "360");
        if (url == null || !url.contains("mux360")) {
            throw new AssertionError("expected muxed 360, got " + url);
        }
    }

    private static JSONObject stream(String url, String label, int h, String type) throws Exception {
        JSONObject o = new JSONObject();
        o.put("url", url);
        o.put("qualityLabel", label);
        o.put("quality", label);
        o.put("height", h);
        o.put("type", type);
        return o;
    }
}
