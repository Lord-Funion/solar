package com.solar.launcher.youtube;

/**
 * 2026-07-06 — One YouTube search/popular row from notPipe metadata.
 * Layman: title, channel, and id for a video the user can pick.
 * Technical: parsed from bridge JSON payload.
 * Reversal: delete; browse UI has no row model.
 */
public final class YouTubeVideo {
    public final String id;
    public final String title;
    public final String author;
    public final String duration;

    public YouTubeVideo(String id, String title, String author, String duration) {
        this.id = id != null ? id : "";
        this.title = title != null ? title : "";
        this.author = author != null ? author : "";
        this.duration = duration != null ? duration : "";
    }

    /** 2026-07-06 — Subtitle for list row — channel plus optional duration. */
    public String subtitle() {
        if (duration != null && !duration.isEmpty()) {
            return author + " · " + duration;
        }
        return author;
    }
}
