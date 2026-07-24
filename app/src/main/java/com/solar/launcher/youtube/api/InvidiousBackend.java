package com.solar.launcher.youtube.api;

import com.solar.launcher.net.SolarHttp;
import com.solar.launcher.youtube.YouTubeComment;
import com.solar.launcher.youtube.YouTubeVideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-15 — Invidious API client (docs.invidious.io).
 * Layman: talks to an Invidious site for search, popular, comments, 360p play links.
 * Technical: /api/v1/* endpoints; formatStreams[0] is muxed 360p only.
 * Reversal: remove from InstancePool; Piped/YtApiLegacy still cover browse.
 */
public final class InvidiousBackend implements YoutubeBackend {

    private static final String UA = "SolarLauncher/YouTube";
    private final String baseUrl;

    public InvidiousBackend(String baseUrl) {
        this.baseUrl = YoutubeApiUtil.trimSlash(baseUrl);
    }

    @Override
    public String getName() {
        return "Invidious";
    }

    @Override
    public String getHost() {
        return baseUrl.replace("https://", "").replace("http://", "");
    }

    @Override
    public boolean supportsVideo360() {
        return true;
    }

    @Override
    public boolean supportsHqVideo() {
        // notPipe: Invidious is 360-only (never in hqInstances). formatStreams[0] is 360 muxed.
        return false;
    }

    @Override
    public List<YouTubeVideo> getPopularVideos() throws IOException {
        // 2026-07-16 — Prefer regional trending so Popular feels local; fall back to hype playlist.
        String region = YoutubeApiUtil.regionCode();
        try {
            String trendUrl = baseUrl + "/api/v1/trending?region="
                    + YoutubeApiUtil.urlEncode(region);
            String trendBody = httpGet(trendUrl);
            List<YouTubeVideo> trending = parseInvidiousVideoArray(new JSONArray(trendBody));
            if (trending != null && !trending.isEmpty()) return trending;
        } catch (Exception ignored) {}
        String url = baseUrl + "/api/v1/playlists/" + YoutubeApiUtil.getHypePlaylist();
        String body = httpGet(url);
        try {
            JSONArray arr = new JSONObject(body).getJSONArray("videos");
            return parseInvidiousPlaylistVideos(arr);
        } catch (Exception e) {
            throw new IOException("Invidious popular parse: " + e.getMessage(), e);
        }
    }

    @Override
    public List<YouTubeVideo> search(String query) throws IOException {
        String region = YoutubeApiUtil.regionCode();
        String url = baseUrl + "/api/v1/search?q=" + YoutubeApiUtil.urlEncode(query)
                + "&type=all&region=" + YoutubeApiUtil.urlEncode(region);
        String body = httpGet(url);
        try {
            JSONArray json = new JSONArray(body);
            List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
            for (int i = 0; i < json.length(); i++) {
                JSONObject j = json.getJSONObject(i);
                String type = j.optString("type", "video");
                if ("channel".equals(type) || "playlist".equals(type)) continue;
                String id = j.optString("videoId", "");
                if (id.length() == 0) continue;
                out.add(new YouTubeVideo(
                        id,
                        j.optString("title", ""),
                        j.optString("author", ""),
                        YoutubeApiUtil.formatDuration(j.optInt("lengthSeconds", 0))));
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Invidious search parse: " + e.getMessage(), e);
        }
    }

    @Override
    public List<YouTubeComment> getComments(String videoId) throws IOException {
        String url = baseUrl + "/api/v1/comments/" + YoutubeApiUtil.urlEncode(videoId);
        try {
            String body = httpGet(url);
            JSONArray arr = new JSONObject(body).getJSONArray("comments");
            List<YouTubeComment> out = new ArrayList<YouTubeComment>();
            int max = Math.min(arr.length(), 80);
            for (int i = 0; i < max; i++) {
                JSONObject j = arr.getJSONObject(i);
                out.add(new YouTubeComment(
                        j.optString("author", ""),
                        j.optString("content", "")));
            }
            return out;
        } catch (Exception e) {
            // 2026-07-15 — notPipe returns empty on comment failures; keep browse calm.
            return new ArrayList<YouTubeComment>();
        }
    }

    @Override
    public String getVideoUrl(String videoId, String quality) throws IOException {
        // notPipe Invidious.getVideoUrl: formatStreams[0] + parseUrl through instance (local=true).
        // Prefer quality-scored pick when multiple muxed rows exist; fall back to [0].
        String url = baseUrl + "/api/v1/videos/" + YoutubeApiUtil.urlEncode(videoId)
                + "?local=true";
        String body = httpGet(url);
        try {
            JSONObject json = new JSONObject(body);
            String err = json.optString("error", "");
            if (err.length() > 0 && !(err.contains("bot") || err.contains("protect")
                    || err.contains("page") || err.contains("Companion"))) {
                throw new IOException(err);
            }
            JSONArray formats = json.optJSONArray("formatStreams");
            if (formats == null || formats.length() < 1) throw new IOException("no formatStreams");
            String q = (quality != null && quality.length() > 0) ? quality : "360";
            String stream = pickFormatStreamUrl(formats, q);
            if (stream == null || stream.isEmpty()) {
                stream = formats.getJSONObject(0).optString("url", "");
            }
            if (stream.isEmpty()) throw new IOException("empty stream url");
            // Critical notPipe step: rewrite CDN host → instance base so VideoView/IJK
            // hits the proxy (same origin cookies / local=true).
            return YoutubeApiUtil.parseUrl(baseUrl, stream);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Invidious stream parse: " + e.getMessage(), e);
        }
    }

    /**
     * Prefer muxed progressive closest to requested height; mp4 over webm (IJK API 17).
     * Package-visible for unit tests.
     */
    public static String pickFormatStreamUrl(JSONArray formats, String quality) {
        if (formats == null || formats.length() < 1) return null;
        String bestUrl = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject s = formats.optJSONObject(i);
            if (s == null) continue;
            String rel = s.optString("url", "");
            if (rel.isEmpty()) continue;
            String qLabel = s.optString("qualityLabel", s.optString("quality", ""));
            int h = heightFromLabel(qLabel);
            if (h <= 0) h = s.optInt("height", 0);
            if (h <= 0) h = s.optInt("resolution", 0);
            String type = s.optString("type", s.optString("container", ""));
            boolean mp4 = type != null && (type.toLowerCase().contains("mp4")
                    || type.toLowerCase().contains("avc"));
            // formatStreams are muxed progressive.
            int score = com.solar.launcher.youtube.YouTubeQuality.scoreStream(h, true, mp4, quality);
            if (score > bestScore) {
                bestScore = score;
                bestUrl = rel;
            }
        }
        if (bestUrl != null) return bestUrl;
        return formats.optJSONObject(0) != null
                ? formats.optJSONObject(0).optString("url", "") : null;
    }

    static int heightFromLabel(String label) {
        if (label == null || label.length() == 0) return 0;
        return com.solar.launcher.youtube.YouTubeQuality.qualityHeight(label);
    }

    /**
     * 2026-07-15 — Pick best adaptiveFormats audio-only URL (was stub null → Save Audio died).
     * Layman: ask Invidious for the soundtrack file, prefer m4a/aac.
     * Tech: GET /api/v1/videos/{id} → adaptiveFormats where type starts with audio/.
     * Reversal: return null; Piped-only audio again (fails when Piped list empty).
     */
    @Override
    public AudioStream resolveAudio(String videoId) throws IOException {
        String url = baseUrl + "/api/v1/videos/" + YoutubeApiUtil.urlEncode(videoId)
                + "?local=true";
        String body = httpGet(url);
        try {
            JSONObject json = new JSONObject(body);
            AudioStream pick = pickBestAudio(json.optJSONArray("adaptiveFormats"), baseUrl);
            if (pick == null) throw new IOException("no adaptive audio");
            return pick;
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Invidious audio parse: " + e.getMessage(), e);
        }
    }

    /**
     * 2026-07-15 — Score adaptiveFormats audio rows; package-visible for unit test.
     * Layman: prefer clear m4a over webm/opus so Music library plays more reliably.
     */
    static AudioStream pickBestAudio(JSONArray formats, String base) {
        if (formats == null) return null;
        AudioStream best = null;
        int bestScore = -1;
        for (int i = 0; i < formats.length(); i++) {
            JSONObject s = formats.optJSONObject(i);
            if (s == null) continue;
            String type = s.optString("type", "");
            if (type.length() == 0 || !type.toLowerCase().startsWith("audio/")) continue;
            String rel = s.optString("url", "");
            if (rel.isEmpty()) continue;
            String full = YoutubeApiUtil.parseUrl(base, rel);
            if (full == null || full.isEmpty()) continue;
            String container = s.optString("container", "");
            String ext = extFromAudio(type, container);
            int score = s.optInt("bitrate", 0);
            if ("m4a".equals(ext)) score += 100000;
            else if ("opus".equals(ext) || "webm".equals(ext)) score += 50000;
            if (score > bestScore) {
                bestScore = score;
                best = new AudioStream(full, ext);
            }
        }
        return best;
    }

    /** 2026-07-15 — Guess file extension from mime / container. */
    private static String extFromAudio(String mime, String container) {
        if (container != null) {
            String c = container.toLowerCase();
            if (c.contains("m4a") || c.equals("mp4")) return "m4a";
            if (c.contains("webm")) return "webm";
            if (c.contains("opus")) return "opus";
            if (c.contains("ogg")) return "ogg";
        }
        if (mime == null) return "m4a";
        String lower = mime.toLowerCase();
        if (lower.contains("mp4") || lower.contains("m4a") || lower.contains("aac")) return "m4a";
        if (lower.contains("opus")) return "opus";
        if (lower.contains("webm")) return "webm";
        if (lower.contains("ogg")) return "ogg";
        return "m4a";
    }

    private static List<YouTubeVideo> parseInvidiousVideoArray(JSONArray json) {
        List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
        if (json == null) return out;
        for (int i = 0; i < json.length(); i++) {
            JSONObject j = json.optJSONObject(i);
            if (j == null) continue;
            String type = j.optString("type", "video");
            if (type.length() > 0 && !"video".equals(type) && !"shortVideo".equals(type)) {
                continue;
            }
            String id = j.optString("videoId", "");
            if (id.isEmpty()) continue;
            out.add(new YouTubeVideo(
                    id,
                    j.optString("title", ""),
                    j.optString("author", ""),
                    YoutubeApiUtil.formatDuration(j.optInt("lengthSeconds", 0))));
        }
        return out;
    }

    private static List<YouTubeVideo> parseInvidiousPlaylistVideos(JSONArray arr) {
        List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            if (!"video".equals(j.optString("type", "video"))) continue;
            out.add(new YouTubeVideo(
                    j.optString("videoId", ""),
                    j.optString("title", ""),
                    j.optString("author", ""),
                    YoutubeApiUtil.formatDuration(j.optInt("lengthSeconds", 0))));
        }
        return out;
    }

    /** 2026-07-19 — Quick timeouts so InstancePool can try the next host inside search budget. */
    private static String httpGet(String url) throws IOException {
        return new String(SolarHttp.getBytesQuick(url, "application/json", UA, 3, 6), "UTF-8");
    }
}
