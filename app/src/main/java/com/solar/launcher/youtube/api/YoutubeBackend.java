package com.solar.launcher.youtube.api;

import com.solar.launcher.youtube.YouTubeComment;
import com.solar.launcher.youtube.YouTubeVideo;

import java.io.IOException;
import java.util.List;

/**
 * 2026-07-15 — One Invidious/Piped/YtApiLegacy host implementing metadata + streams.
 * Layman: a single public frontend Solar can ask for search, popular, comments, play links.
 * Technical: notPipe Metadata + VideoStream surfaces, Solar-owned types.
 * Reversal: delete; InstancePool has nothing to call.
 */
public interface YoutubeBackend {

    /** Short backend family name for logs (Invidious / Piped / YtApiLegacy). */
    String getName();

    /** Host part of the base URL. */
    String getHost();

    /** True when this host can resolve muxed ~360p (or any) stream. */
    boolean supportsVideo360();

    /** True when this host can honour quality=480 (and higher). */
    boolean supportsHqVideo();

    List<YouTubeVideo> getPopularVideos() throws IOException;

    List<YouTubeVideo> search(String query) throws IOException;

    List<YouTubeComment> getComments(String videoId) throws IOException;

    /**
     * Direct playable URL for progressive quality ("360" / "480").
     * Layman: the file link Solar IJK opens.
     */
    String getVideoUrl(String videoId, String quality) throws IOException;

    /**
     * Best audio-only URL + extension, or null if this host has none.
     * 2026-07-15 — Invidious + Piped both resolve; YtApiLegacy may still return null.
     * Was: Piped-only; empty Piped seeds → Save Audio always failed.
     */
    AudioStream resolveAudio(String videoId) throws IOException;

    /** Audio-only pick for save-to-Music. */
    final class AudioStream {
        public final String url;
        public final String ext;

        public AudioStream(String url, String ext) {
            this.url = url;
            this.ext = ext != null ? ext : "m4a";
        }
    }
}
