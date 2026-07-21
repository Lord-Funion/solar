package com.solar.launcher.youtube;

import java.util.Locale;

/**
 * 2026-07-19 — Now Playing title/artist for YouTube Audio Play.
 * Layman: keep the search-result name on screen; ignore junk tags that say “Failed”.
 * Technical: catalog metadata beats MediaMetadataRetriever garbage on progressive m4a.
 * Was: playTrackList used file/ID3 only → NP showed “Failed” while audio still played.
 * Reversal: pickTitle returns tagTitle when non-empty (drop catalog preference).
 */
public final class YouTubeDisplayMeta {

    private YouTubeDisplayMeta() {}

    /**
     * True when an embedded/ID3 title is safe to show on Now Playing.
     * Layman: skip empty or error-looking titles from broken containers.
     */
    public static boolean isUsableTagTitle(String title) {
        if (title == null) return false;
        String s = title.trim();
        if (s.isEmpty()) return false;
        String lower = s.toLowerCase(Locale.US);
        if ("failed".equals(lower) || "unknown".equals(lower) || "null".equals(lower)) {
            return false;
        }
        if (lower.startsWith("load failed")) return false;
        return true;
    }

    /**
     * Prefer YouTube browse title, then usable tags, then file basename.
     * Layman: show the name you tapped, not a broken tag.
     */
    public static String pickTitle(String catalogTitle, String tagTitle, String fileName) {
        if (catalogTitle != null) {
            String c = catalogTitle.trim();
            if (c.length() > 0) return c;
        }
        if (isUsableTagTitle(tagTitle)) return tagTitle.trim();
        if (fileName == null || fileName.length() == 0) return "";
        String base = fileName;
        int slash = base.lastIndexOf('/');
        if (slash >= 0 && slash < base.length() - 1) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base;
    }

    /**
     * Prefer YouTube author over “Unknown Artist” / “Failed” tag junk.
     * Layman: show the channel name from search when tags are empty or wrong.
     */
    public static String pickArtist(String catalogAuthor, String tagArtist) {
        if (catalogAuthor != null) {
            String a = catalogAuthor.trim();
            if (a.length() > 0 && isUsableTagTitle(a)
                    && !"unknown artist".equalsIgnoreCase(a)) {
                return a;
            }
        }
        if (tagArtist != null) {
            String t = tagArtist.trim();
            if (t.length() > 0 && isUsableTagTitle(t)
                    && !"unknown artist".equalsIgnoreCase(t)) {
                return t;
            }
        }
        return "";
    }
}
