package com.solar.launcher.youtube.api;

import org.junit.Test;

/** 2026-07-15 — Host JVM checks for YouTube API helpers. */
public class YoutubeApiUtilTest {

    @Test
    public void formatDurationMinutes() {
        if (!"1:30".equals(YoutubeApiUtil.formatDuration(90))) {
            throw new AssertionError("90s -> 1:30");
        }
    }

    @Test
    public void formatDurationHours() {
        if (!"1:01:03".equals(YoutubeApiUtil.formatDuration(3663))) {
            throw new AssertionError("3663s -> 1:01:03");
        }
    }

    @Test
    public void parseUrlRelative() {
        String u = YoutubeApiUtil.parseUrl("http://host:3000", "/videoplayback?x=1");
        if (!"http://host:3000/videoplayback?x=1".equals(u)) {
            throw new AssertionError(u);
        }
    }

    @Test
    public void pipedVideoIdWatchPath() {
        if (!"dQw4w9WgXcQ".equals(YoutubeApiUtil.pipedVideoId("/watch?v=dQw4w9WgXcQ"))) {
            throw new AssertionError("piped id parse failed");
        }
    }

    @Test
    public void hypePlaylistNonEmpty() {
        String id = YoutubeApiUtil.getHypePlaylist();
        if (id == null || id.length() < 8) {
            throw new AssertionError("hype playlist id empty");
        }
    }
}
