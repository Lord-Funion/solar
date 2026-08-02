package com.solar.launcher.youtube;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-06 — Parse notPipe bridge JSON into YouTubeVideo rows.
 * Layman: turns bridge text into video list entries Solar can show.
 * Technical: mirrors Xposed NotPipeJson field names.
 * Reversal: delete; NotPipeClient cannot decode results.
 */
public final class YouTubeResultJson {

    private YouTubeResultJson() {}

    public static List<YouTubeVideo> parseVideos(String json) throws Exception {
        List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
        if (json == null || json.trim().isEmpty()) return out;
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new YouTubeVideo(
                    o.optString("id", ""),
                    o.optString("title", ""),
                    o.optString("author", ""),
                    o.optString("length", "")));
        }
        return out;
    }

    public static String parseStreamUrl(String json) throws Exception {
        StreamResult r = parseStreamResult(json);
        return r != null ? r.url : null;
    }

    /** Resolved stream URL plus file extension hint from bridge. */
    public static StreamResult parseStreamResult(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) return null;
        JSONObject o = new JSONObject(json);
        String url = o.optString("url", "");
        if (url.isEmpty()) return null;
        String ext = o.optString("ext", "");
        if (ext.isEmpty()) ext = guessExtFromUrl(url);
        return new StreamResult(url, ext);
    }

    private static String guessExtFromUrl(String url) {
        int q = url.indexOf('?');
        String path = q >= 0 ? url.substring(0, q) : url;
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot >= path.length() - 1) return "mp4";
        String ext = path.substring(dot + 1).toLowerCase();
        return ext.length() > 6 ? "mp4" : ext;
    }

    public static final class StreamResult {
        public final String url;
        public final String ext;

        public StreamResult(String url, String ext) {
            this.url = url;
            this.ext = ext != null ? ext : "mp4";
        }
    }

    /** Parse GET_COMMENTS payload: [{author, content}, …]. */
    public static List<YouTubeComment> parseComments(String json) throws Exception {
        List<YouTubeComment> out = new ArrayList<YouTubeComment>();
        if (json == null || json.trim().isEmpty()) return out;
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            out.add(new YouTubeComment(
                    o.optString("author", ""),
                    o.optString("content", "")));
        }
        return out;
    }
}
