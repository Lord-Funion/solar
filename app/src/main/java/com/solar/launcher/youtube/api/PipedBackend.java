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
 * 2026-07-15 — Piped API client (docs.piped.video).
 * Layman: another public YouTube frontend; also the best source for audio-only saves.
 * Technical: /search, /playlists, /streams, /comments; proxyUrl rewrites media paths.
 * Reversal: remove from InstancePool; Invidious/YtApiLegacy remain.
 */
public final class PipedBackend implements YoutubeBackend {

    private static final String UA = "SolarLauncher/YouTube";
    private final String baseUrl;
    private final String proxyUrl;

    /**
     * @param pipedEntry base URL, or "base,proxy" as in notPipe.json piped rows
     */
    public PipedBackend(String pipedEntry) {
        if (pipedEntry == null) pipedEntry = "";
        String[] parts = pipedEntry.split(",", 2);
        this.baseUrl = YoutubeApiUtil.trimSlash(parts[0].trim());
        this.proxyUrl = YoutubeApiUtil.trimSlash(
                parts.length > 1 ? parts[1].trim() : parts[0].trim());
    }

    @Override
    public String getName() {
        return "Piped";
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
        return false;
    }

    @Override
    public List<YouTubeVideo> getPopularVideos() throws IOException {
        // 2026-07-16 — Regional trending first; hype playlist fallback.
        String region = YoutubeApiUtil.regionCode();
        try {
            String trendUrl = baseUrl + "/trending?region=" + YoutubeApiUtil.urlEncode(region);
            String trendBody = httpGet(trendUrl);
            List<YouTubeVideo> trending = parsePipedStreams(new JSONArray(trendBody));
            if (trending != null && !trending.isEmpty()) return trending;
        } catch (Exception ignored) {}
        String url = baseUrl + "/playlists/" + YoutubeApiUtil.getHypePlaylist();
        String body = httpGet(url);
        try {
            JSONArray arr = new JSONObject(body).getJSONArray("relatedStreams");
            return parsePipedStreams(arr);
        } catch (Exception e) {
            throw new IOException("Piped popular parse: " + e.getMessage(), e);
        }
    }

    @Override
    public List<YouTubeVideo> search(String query) throws IOException {
        String region = YoutubeApiUtil.regionCode();
        String url = baseUrl + "/search?q=" + YoutubeApiUtil.urlEncode(query)
                + "&filter=all&region=" + YoutubeApiUtil.urlEncode(region);
        String body = httpGet(url);
        try {
            JSONArray arr = new JSONObject(body).getJSONArray("items");
            List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                // 2026-07-19 — Piped uses type=stream; skip channel/playlist rows and empty ids.
                String type = j.optString("type", "stream");
                if ("channel".equals(type) || "playlist".equals(type)) continue;
                String id = YoutubeApiUtil.pipedVideoId(j.optString("url", ""));
                if (id.length() == 0) continue;
                out.add(new YouTubeVideo(
                        id,
                        j.optString("title", ""),
                        j.optString("uploaderName", ""),
                        YoutubeApiUtil.formatDuration(j.optInt("duration", 0))));
            }
            return out;
        } catch (Exception e) {
            throw new IOException("Piped search parse: " + e.getMessage(), e);
        }
    }

    @Override
    public List<YouTubeComment> getComments(String videoId) throws IOException {
        String url = baseUrl + "/comments/" + YoutubeApiUtil.urlEncode(videoId);
        try {
            String body = httpGet(url);
            JSONArray arr = new JSONObject(body).getJSONArray("comments");
            List<YouTubeComment> out = new ArrayList<YouTubeComment>();
            int max = Math.min(arr.length(), 80);
            for (int i = 0; i < max; i++) {
                JSONObject j = arr.getJSONObject(i);
                String text = j.optString("commentText", j.optString("content", ""));
                out.add(new YouTubeComment(j.optString("author", ""), stripSimpleHtml(text)));
            }
            return out;
        } catch (Exception e) {
            return new ArrayList<YouTubeComment>();
        }
    }

    @Override
    public String getVideoUrl(String videoId, String quality) throws IOException {
        // notPipe Piped.getVideoUrl: last videoStreams entry (lowest progressive) + proxy rewrite.
        String url = baseUrl + "/streams/" + YoutubeApiUtil.urlEncode(videoId);
        String body = httpGet(url);
        try {
            JSONObject json = new JSONObject(body);
            String err = json.optString("error", "");
            String msg = json.optString("message", "");
            if (err.length() > 0 && !(msg.contains("bot") || msg.contains("protect")
                    || msg.contains("page"))) {
                throw new IOException(msg.length() > 0 ? msg : err);
            }
            JSONArray videoStreams = json.getJSONArray("videoStreams");
            if (videoStreams.length() < 1) throw new IOException("no videoStreams");
            // Prefer quality-scored muxed; fall back to notPipe last-entry.
            String chosen = pickPipedVideoUrl(videoStreams, quality);
            if (chosen == null || chosen.isEmpty()) {
                chosen = videoStreams.getJSONObject(videoStreams.length() - 1)
                        .optString("url", "");
            }
            if (chosen.isEmpty()) throw new IOException("empty stream url");
            return YoutubeApiUtil.parseUrl(proxyUrl, chosen);
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Piped stream parse: " + e.getMessage(), e);
        }
    }

    /** Pick muxed progressive closest to quality; package-visible for tests. */
    public static String pickPipedVideoUrl(JSONArray videoStreams, String quality) {
        if (videoStreams == null || videoStreams.length() < 1) return null;
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        String anyMuxed = null;
        for (int i = 0; i < videoStreams.length(); i++) {
            JSONObject s = videoStreams.optJSONObject(i);
            if (s == null) continue;
            String u = s.optString("url", "");
            if (u.isEmpty()) continue;
            boolean videoOnly = s.optBoolean("videoOnly", true);
            if (!videoOnly && anyMuxed == null) anyMuxed = u;
            String q = s.optString("quality", "");
            int h = s.optInt("height", 0);
            if (h <= 0) h = com.solar.launcher.youtube.YouTubeQuality.qualityHeight(q);
            String mime = s.optString("mimeType", s.optString("format", ""));
            boolean mp4 = mime != null && mime.toLowerCase().contains("mp4");
            int score = com.solar.launcher.youtube.YouTubeQuality.scoreStream(
                    h, !videoOnly, mp4, quality);
            if (score > bestScore) {
                bestScore = score;
                best = u;
            }
        }
        if (best != null) return best;
        if (anyMuxed != null) return anyMuxed;
        return videoStreams.optJSONObject(videoStreams.length() - 1) != null
                ? videoStreams.optJSONObject(videoStreams.length() - 1).optString("url", "")
                : null;
    }

    @Override
    public AudioStream resolveAudio(String videoId) throws IOException {
        String url = baseUrl + "/streams/" + YoutubeApiUtil.urlEncode(videoId);
        String body = httpGet(url);
        try {
            JSONArray audioStreams = new JSONObject(body).getJSONArray("audioStreams");
            // 2026-07-15 — Prefer m4a like Invidious so Music library MediaPlayer/IJK stay happy.
            // Had: opus and m4a same +100k → high-bitrate opus/.webm saves that API 17 can't play.
            return pickBestAudio(audioStreams, proxyUrl);
        } catch (Exception e) {
            throw new IOException("Piped audio parse: " + e.getMessage(), e);
        }
    }

    /**
     * 2026-07-15 — Score Piped audioStreams; package-visible for unit test.
     * Layman: pick AAC/m4a first so saved tracks show up and play in the Music app.
     * Technical: m4a ≫ opus/webm; bitrate breaks ties within the same family.
     */
    static AudioStream pickBestAudio(JSONArray audioStreams, String proxy) {
        if (audioStreams == null) return null;
        AudioStream best = null;
        int bestScore = -1;
        for (int i = 0; i < audioStreams.length(); i++) {
            JSONObject s = audioStreams.optJSONObject(i);
            if (s == null) continue;
            String rel = s.optString("url", "");
            if (rel.isEmpty()) continue;
            String full = YoutubeApiUtil.parseUrl(proxy, rel);
            if (full == null || full.isEmpty()) continue;
            String mime = s.optString("mimeType", "");
            String ext = extFromMime(mime);
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

    /** 2026-07-15 — Guess save extension from Piped mimeType. */
    static String extFromMime(String mime) {
        if (mime == null) return "m4a";
        String lower = mime.toLowerCase();
        if (lower.contains("mp4") || lower.contains("m4a") || lower.contains("aac")) return "m4a";
        if (lower.contains("opus")) return "opus";
        if (lower.contains("webm")) return "webm";
        if (lower.contains("ogg")) return "ogg";
        return "m4a";
    }

    /** Parse Piped trending / playlist relatedStreams arrays into rows. */
    private static List<YouTubeVideo> parsePipedStreams(JSONArray arr) {
        List<YouTubeVideo> out = new ArrayList<YouTubeVideo>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            String type = j.optString("type", "stream");
            if (type.length() > 0 && !"stream".equals(type) && !"video".equals(type)) continue;
            String id = YoutubeApiUtil.pipedVideoId(j.optString("url", ""));
            if (id.isEmpty()) id = j.optString("videoId", "");
            if (id.isEmpty()) continue;
            out.add(new YouTubeVideo(
                    id,
                    j.optString("title", ""),
                    j.optString("uploaderName", j.optString("uploader", "")),
                    YoutubeApiUtil.formatDuration(j.optInt("duration", 0))));
        }
        return out;
    }

    /** Tiny HTML strip for Piped commentText (&lt;br&gt; → newline). */
    static String stripSimpleHtml(String html) {
        if (html == null) return "";
        StringBuilder sb = new StringBuilder(html.length());
        int i = 0;
        int len = html.length();
        while (i < len) {
            int tagStart = html.indexOf('<', i);
            if (tagStart < 0) {
                sb.append(html, i, len);
                break;
            }
            sb.append(html, i, tagStart);
            int tagEnd = html.indexOf('>', tagStart);
            if (tagEnd < 0) {
                sb.append(html, tagStart, len);
                break;
            }
            if (html.regionMatches(true, tagStart, "<br", 0, 3)) {
                sb.append('\n');
            }
            i = tagEnd + 1;
        }
        return sb.toString();
    }

    /** 2026-07-19 — Quick timeouts so dead Piped hosts do not burn the search window. */
    private static String httpGet(String url) throws IOException {
        return new String(SolarHttp.getBytesQuick(url, "application/json", UA, 3, 6), "UTF-8");
    }
}
