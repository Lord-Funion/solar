package com.solar.launcher.youtube.api;

import com.solar.launcher.debug.Debug2241b1Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 2026-07-15 — Piped comment HTML strip + m4a-preferring audio picker. */
public class PipedBackendTest {

    @Test
    public void stripSimpleHtmlBreaks() {
        String out = PipedBackend.stripSimpleHtml("a<br>b<br/>c");
        if (!out.contains("\n") || !out.contains("a") || !out.contains("c")) {
            throw new AssertionError("br not converted: " + out);
        }
    }

    @Test
    public void stripSimpleHtmlNullSafe() {
        if (!"".equals(PipedBackend.stripSimpleHtml(null))) {
            throw new AssertionError("null should yield empty");
        }
    }

    @Test
    public void pickBestAudioPrefersM4aOverHigherBitrateOpus() throws Exception {
        JSONArray arr = new JSONArray();
        JSONObject opus = new JSONObject();
        opus.put("url", "https://proxy.example/opus");
        opus.put("mimeType", "audio/webm; codecs=\"opus\"");
        opus.put("bitrate", 160000);
        arr.put(opus);
        JSONObject m4a = new JSONObject();
        m4a.put("url", "https://proxy.example/m4a");
        m4a.put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"");
        m4a.put("bitrate", 128000);
        arr.put(m4a);
        YoutubeBackend.AudioStream best = PipedBackend.pickBestAudio(arr, "https://proxy.example");
        if (best == null || !"m4a".equals(best.ext)) {
            throw new AssertionError("expected m4a, got " + (best != null ? best.ext : "null"));
        }
        // #region agent log
        JSONObject d = new JSONObject();
        d.put("ext", best.ext);
        d.put("hypothesis", "YT1_m4aPreferred");
        Debug2241b1Log.log("PipedBackendTest.pickBestAudio", "m4a wins over opus", "YT1",
                "post-fix", d);
        // #endregion
    }
}
