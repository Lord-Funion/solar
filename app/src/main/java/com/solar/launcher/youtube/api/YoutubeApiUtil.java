package com.solar.launcher.youtube.api;

import com.solar.launcher.SolarApplication;
import com.solar.launcher.SolarGeoRegion;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 2026-07-15 — Shared URL/duration helpers for YouTube API backends.
 * Layman: sticks host + path together and formats video lengths like 3:45.
 * Technical: rewrite of notPipe Utils.parseUrl / formatDuration / getHypePlaylist.
 * Reversal: inline helpers per backend; behaviour unchanged.
 */
public final class YoutubeApiUtil {

    // 2026-07-15 — YouTube “hyped” playlist IDs (same pool as notPipe Utils).
    private static final List<String> HYPE_PLAYLISTS = Arrays.asList(
            "OLXuPDPDe3URMWBdmS4_jxdqVF08DAlAXWQ",
            "OLd7LoUR1ndxIsyTJ_5pK4tvrWBcvkewBLg",
            "OLNUaVf-BhE20xpEEdOmnxF6oyKQwpN1tBQ",
            "OLUZ2nAWnPVwwoXomaTvbFi1Cje8td4Z0zg",
            "OLewrgOzMLeOADYzQ1ewNeqtPlJP20RE_Zg",
            "OLbXum44nJ19cZmR7GxDm5rF_Nj_coRoO5g",
            "OLPPB3977IfFUtPW8213fFOsf2lmAWfahDg",
            "OLZyZj8vWFMkIOGlFzyKuFlLA2PY42VhRuA");

    private static final Random RANDOM = new Random();

    private YoutubeApiUtil() {}

    /**
     * 2026-07-16 — ISO country for trending/search (from SolarGeoRegion IP cache / locale).
     * Layman: tell YouTube frontends where home is so Popular matches local charts.
     */
    public static String regionCode() {
        try {
            android.content.Context ctx = SolarApplication.getAppContext();
            if (ctx != null) {
                String r = SolarGeoRegion.youtubeRegion(ctx);
                if (r != null && r.length() == 2) return r.toUpperCase(Locale.US);
            }
        } catch (Exception ignored) {}
        try {
            String lc = Locale.getDefault().getCountry();
            if (lc != null && lc.length() == 2) return lc.toUpperCase(Locale.US);
        } catch (Exception ignored) {}
        return "US";
    }

    /** Pick a hyped playlist id for “Popular” browse. */
    public static String getHypePlaylist() {
        return HYPE_PLAYLISTS.get(RANDOM.nextInt(HYPE_PLAYLISTS.size()));
    }

    /**
     * Join base + relative/CDN url the way Invidious/Piped expect (proxy rewrite).
     * Layman: if the link is a path, hang it on our instance host.
     */
    public static String parseUrl(String baseUrl, String url) {
        if (url == null) return "";
        if (url.startsWith("/")) {
            return trimSlash(baseUrl) + url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            int scheme = url.indexOf("://");
            int pathStart = url.indexOf('/', scheme + 3);
            if (pathStart != -1) {
                return trimSlash(baseUrl) + url.substring(pathStart);
            }
            return trimSlash(baseUrl);
        }
        return url;
    }

    /** Seconds → M:SS or H:MM:SS for list subtitles. */
    public static String formatDuration(int totalSeconds) {
        if (totalSeconds < 1) return "";
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + ":" + two(minutes) + ":" + two(seconds);
        }
        return minutes + ":" + two(seconds);
    }

    /** UTF-8 query escape without java.nio charset (API 17). */
    public static String urlEncode(String s) {
        if (s == null) return "";
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** Strip trailing slash from instance base. */
    public static String trimSlash(String base) {
        if (base == null) return "";
        int end = base.length();
        while (end > 0 && base.charAt(end - 1) == '/') end--;
        return base.substring(0, end);
    }

    /** Piped channel/video urls are often "/watch?v=ID" — take id after last '=' or '/'. */
    public static String pipedVideoId(String urlField) {
        if (urlField == null || urlField.isEmpty()) return "";
        // 2026-07-15 — notPipe used substring(9) on "/watch?v="; keep that fast path.
        if (urlField.startsWith("/watch?v=") && urlField.length() > 9) {
            return urlField.substring(9);
        }
        int eq = urlField.lastIndexOf('=');
        if (eq >= 0 && eq < urlField.length() - 1) return urlField.substring(eq + 1);
        int slash = urlField.lastIndexOf('/');
        if (slash >= 0 && slash < urlField.length() - 1) return urlField.substring(slash + 1);
        return urlField;
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
