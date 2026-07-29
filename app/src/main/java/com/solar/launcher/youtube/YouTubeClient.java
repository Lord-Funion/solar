package com.solar.launcher.youtube;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.youtube.official.YouTubeOfficialApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async facade for official YouTube Data API v3 metadata.
 *
 * Solar deliberately exposes no YouTube audiovisual stream resolver. Metadata
 * results lead only to bookmarks, authorized-provider searches, and a displayed
 * canonical URL.
 */
public final class YouTubeClient {

    public interface Callback {
        void onSuccess(String payloadJson);
        void onError(String message);
    }

    private static final long DEFAULT_TIMEOUT_MS = 22_000L;
    private static final long PROBE_TIMEOUT_MS = 3_000L;
    public static final String ACQUISITION_BLOCKED = "metadata_only";

    private static volatile YouTubeClient instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    // Bounded for the Y1: at most two API requests can consume heap/sockets.
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final YouTubeOfficialApi api;

    private YouTubeClient(Context context) {
        api = new YouTubeOfficialApi(context.getApplicationContext());
    }

    public static YouTubeClient getInstance(Context context) {
        if (instance == null) {
            synchronized (YouTubeClient.class) {
                if (instance == null) instance = new YouTubeClient(context);
            }
        }
        return instance;
    }

    /** Retained for source compatibility with the established video player. */
    public static String preferredVideoQuality() {
        return YouTubeQuality.preferredVideoQuality();
    }

    /** Retained for local-video callers; remote YouTube resolution is disabled. */
    public static String fallbackVideoQuality(String failedQuality) {
        return YouTubeQuality.fallbackVideoQuality(failedQuality);
    }

    public void probe(final Callback callback) {
        runTimed(PROBE_TIMEOUT_MS, callback, new Work() {
            @Override
            public String call() throws Exception {
                if (!api.isConfigured()) throw new Exception("youtube_setup_required");
                JSONObject result = new JSONObject();
                result.put("version", "official-data-api-v3");
                result.put("metadataOnly", true);
                return result.toString();
            }
        });
    }

    public void fetchPopular(Callback callback) {
        fetchPopular("", callback);
    }

    public void fetchPopular(final String pageToken, final Callback callback) {
        runTimed(DEFAULT_TIMEOUT_MS, callback, new Work() {
            @Override
            public String call() throws Exception {
                return pageToJson(api.popular(deviceRegion(), pageToken));
            }
        });
    }

    public void search(String query, Callback callback) {
        search(query, "", callback);
    }

    public void search(final String query, final String pageToken,
            final Callback callback) {
        runTimed(DEFAULT_TIMEOUT_MS, callback, new Work() {
            @Override
            public String call() throws Exception {
                return pageToJson(api.search(query, pageToken, deviceRegion()));
            }
        });
    }

    /**
     * Explicit policy boundary. The official Data API does not expose media
     * streams, and Solar will not fall back to scraping/front-end instances.
     */
    public void resolveStream(String videoId, Callback callback) {
        postPolicyError(callback);
    }

    public void resolveStream(String videoId, String quality, Callback callback) {
        postPolicyError(callback);
    }

    public void resolveAudioStream(String videoId, Callback callback) {
        postPolicyError(callback);
    }

    public void fetchComments(final String videoId, final Callback callback) {
        runTimed(DEFAULT_TIMEOUT_MS, callback, new Work() {
            @Override
            public String call() throws Exception {
                return commentsToJson(api.comments(videoId));
            }
        });
    }

    private void postPolicyError(final Callback callback) {
        if (callback == null) return;
        main.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(ACQUISITION_BLOCKED);
            }
        });
    }

    private void runTimed(final long timeoutMs, final Callback callback,
            final Work work) {
        if (callback == null) return;
        final Object gate = new Object();
        final boolean[] done = new boolean[] { false };
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                synchronized (gate) {
                    if (done[0]) return;
                    done[0] = true;
                }
                callback.onError("timeout");
            }
        }, timeoutMs);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String payload = work.call();
                    synchronized (gate) {
                        if (done[0]) return;
                        done[0] = true;
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(payload);
                        }
                    });
                } catch (Exception error) {
                    final String message = safeMessage(error);
                    synchronized (gate) {
                        if (done[0]) return;
                        done[0] = true;
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(message);
                        }
                    });
                }
            }
        });
    }

    static String pageToJson(YouTubeOfficialApi.Page page) throws Exception {
        JSONObject result = new JSONObject();
        result.put("items", videosArray(page != null ? page.videos : null));
        result.put("nextPageToken", page != null ? page.nextPageToken : "");
        return result.toString();
    }

    /** Legacy array shape retained for migration tests and cached old results. */
    static String videosToJson(List<YouTubeVideo> videos) throws Exception {
        return videosArray(videos).toString();
    }

    private static JSONArray videosArray(List<YouTubeVideo> videos) throws Exception {
        JSONArray array = new JSONArray();
        if (videos == null) return array;
        for (int i = 0; i < videos.size(); i++) {
            YouTubeVideo video = videos.get(i);
            if (video == null) continue;
            JSONObject item = new JSONObject();
            item.put("id", nonNull(video.id));
            item.put("title", nonNull(video.title));
            item.put("author", nonNull(video.author));
            item.put("length", nonNull(video.duration));
            array.put(item);
        }
        return array;
    }

    static String commentsToJson(List<YouTubeComment> comments) throws Exception {
        JSONArray array = new JSONArray();
        if (comments == null) return array.toString();
        for (int i = 0; i < comments.size(); i++) {
            YouTubeComment comment = comments.get(i);
            if (comment == null) continue;
            JSONObject item = new JSONObject();
            item.put("author", nonNull(comment.author));
            item.put("content", nonNull(comment.content));
            array.put(item);
        }
        return array.toString();
    }

    private static String deviceRegion() {
        String country = Locale.getDefault().getCountry();
        return country != null && country.matches("[A-Za-z]{2}")
                ? country.toUpperCase(Locale.US) : "US";
    }

    private static String safeMessage(Exception error) {
        String message = error != null ? error.getMessage() : "";
        if (message == null || message.trim().length() == 0) return "youtube_error";
        // API helpers return only stable reason codes; never surface response bodies/tokens.
        return message.length() <= 80 ? message : "youtube_error";
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private interface Work {
        String call() throws Exception;
    }
}
