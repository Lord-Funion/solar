package dev.lordfunion.rockboxsolar;

import android.os.Build;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class YouTubeSearch {
    static final class Result {
        final String videoId;
        final String title;
        final String channel;
        final String channelId;
        final String description;
        final String publishedAt;
        final String thumbnailUrl;
        String duration = "";
        long views = -1L;

        Result(String videoId, String title, String channel, String channelId,
               String description, String publishedAt, String thumbnailUrl) {
            this.videoId = videoId;
            this.title = title;
            this.channel = channel;
            this.channelId = channelId;
            this.description = description;
            this.publishedAt = publishedAt;
            this.thumbnailUrl = thumbnailUrl;
        }

        String url() { return "https://www.youtube.com/watch?v=" + videoId; }

        String summary() {
            String detail = channel;
            if (duration.length() > 0) detail += " • " + duration;
            if (views >= 0) detail += " • " + compactNumber(views) + " views";
            return title + " — " + detail;
        }
    }

    static final class Page {
        final List<Result> results;
        final String nextPageToken;

        Page(List<Result> results, String nextPageToken) {
            this.results = results;
            this.nextPageToken = nextPageToken;
        }
    }

    static Page search(OkHttpClient client, String apiKey, String query, String pageToken) throws Exception {
        HttpUrl base = HttpUrl.parse("https://www.googleapis.com/youtube/v3/search");
        if (base == null) throw new IOException("Invalid YouTube API URL");
        HttpUrl.Builder url = base.newBuilder()
                .addQueryParameter("part", "snippet")
                .addQueryParameter("type", "video")
                .addQueryParameter("maxResults", "20")
                .addQueryParameter("safeSearch", "moderate")
                .addQueryParameter("q", query)
                .addQueryParameter("key", apiKey);
        if (pageToken != null && pageToken.length() > 0) url.addQueryParameter("pageToken", pageToken);
        JSONObject root = getJson(client, url.build());
        throwApiError(root);
        JSONArray items = root.optJSONArray("items");
        ArrayList<Result> results = new ArrayList<Result>();
        ArrayList<String> ids = new ArrayList<String>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                JSONObject id = item.optJSONObject("id");
                JSONObject snippet = item.optJSONObject("snippet");
                if (id == null || snippet == null) continue;
                String videoId = id.optString("videoId", "");
                if (videoId.length() == 0) continue;
                JSONObject thumbnails = snippet.optJSONObject("thumbnails");
                String thumb = "";
                if (thumbnails != null) {
                    JSONObject medium = thumbnails.optJSONObject("medium");
                    if (medium == null) medium = thumbnails.optJSONObject("default");
                    if (medium != null) thumb = medium.optString("url", "");
                }
                Result result = new Result(videoId,
                        decode(snippet.optString("title", "Untitled")),
                        decode(snippet.optString("channelTitle", "Unknown channel")),
                        snippet.optString("channelId", ""),
                        decode(snippet.optString("description", "")),
                        snippet.optString("publishedAt", ""), thumb);
                results.add(result);
                ids.add(videoId);
            }
        }
        enrichVideos(client, apiKey, results, ids);
        return new Page(results, root.optString("nextPageToken", ""));
    }

    private static void enrichVideos(OkHttpClient client, String apiKey,
                                     List<Result> results, List<String> ids) throws Exception {
        if (ids.isEmpty()) return;
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        HttpUrl base = HttpUrl.parse("https://www.googleapis.com/youtube/v3/videos");
        if (base == null) throw new IOException("Invalid YouTube videos API URL");
        HttpUrl url = base.newBuilder()
                .addQueryParameter("part", "contentDetails,statistics")
                .addQueryParameter("id", joined.toString())
                .addQueryParameter("key", apiKey)
                .build();
        JSONObject root = getJson(client, url);
        throwApiError(root);
        JSONArray items = root.optJSONArray("items");
        Map<String, JSONObject> byId = new HashMap<String, JSONObject>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null) byId.put(item.optString("id", ""), item);
            }
        }
        for (Result result : results) {
            JSONObject item = byId.get(result.videoId);
            if (item == null) continue;
            JSONObject details = item.optJSONObject("contentDetails");
            JSONObject statistics = item.optJSONObject("statistics");
            if (details != null) result.duration = formatIsoDuration(details.optString("duration", ""));
            if (statistics != null) {
                try { result.views = Long.parseLong(statistics.optString("viewCount", "-1")); }
                catch (Exception ignored) { result.views = -1; }
            }
        }
    }

    private static JSONObject getJson(OkHttpClient client, HttpUrl url) throws Exception {
        Response response = client.newCall(new Request.Builder().url(url)
                .header("User-Agent", "RockboxSolar/0.2 YouTube metadata client").build()).execute();
        try {
            ResponseBody body = response.body();
            String text = body == null ? "" : body.string();
            if (text.length() == 0) throw new IOException("Empty YouTube response (HTTP " + response.code() + ")");
            return new JSONObject(text);
        } finally {
            response.close();
        }
    }

    private static void throwApiError(JSONObject root) throws IOException {
        JSONObject error = root.optJSONObject("error");
        if (error == null) return;
        String message = error.optString("message", "YouTube API error");
        JSONArray errors = error.optJSONArray("errors");
        if (errors != null && errors.length() > 0) {
            JSONObject detail = errors.optJSONObject(0);
            String reason = detail == null ? "" : detail.optString("reason", "");
            if (reason.length() > 0) message += " (" + reason + ")";
        }
        throw new IOException(message);
    }

    @SuppressWarnings("deprecation")
    private static String decode(String value) {
        if (value == null) return "";
        if (Build.VERSION.SDK_INT >= 24) return Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        return Html.fromHtml(value).toString();
    }

    private static String formatIsoDuration(String value) {
        if (value == null || !value.startsWith("PT")) return "";
        int hours = 0, minutes = 0, seconds = 0;
        String number = "";
        for (int i = 2; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) number += c;
            else if (number.length() > 0) {
                int parsed = Integer.parseInt(number);
                if (c == 'H') hours = parsed;
                else if (c == 'M') minutes = parsed;
                else if (c == 'S') seconds = parsed;
                number = "";
            }
        }
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private static String compactNumber(long value) {
        if (value >= 1000000000L) return String.format(Locale.US, "%.1fB", value / 1000000000d);
        if (value >= 1000000L) return String.format(Locale.US, "%.1fM", value / 1000000d);
        if (value >= 1000L) return String.format(Locale.US, "%.1fK", value / 1000d);
        return Long.toString(value);
    }
}
