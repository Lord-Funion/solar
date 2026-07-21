package com.solar.launcher;

import android.content.Context;

import com.solar.launcher.podcast.OpenRssClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Cold-start queue restore — small JSON in app files dir. */
public final class PlayQueueStore {
    private static final String FILE = "play_queue.json";

    /** Last restored seek ms (-1 = unset). */
    public static volatile int lastRestoredSeekMs = -1;
    /** Last restored playing flag. */
    public static volatile boolean lastRestoredPlaying;
    /** Last restored epoch from disk. */
    public static volatile long lastRestoredEpoch;

    private PlayQueueStore() {}

    public static void save(Context ctx, PlayQueue queue) {
        save(ctx, queue, -1, false, AsyncPlayQueueWriter.memoryEpoch());
    }

    /**
     * Persist queue + playback resume fields.
     * 2026-07-19 — seekMs/playing/epoch for kill-relaunch resume.
     */
    public static void save(Context ctx, PlayQueue queue, int seekMs, boolean playing, long epoch) {
        if (ctx == null || queue == null) return;
        saveToDir(ctx.getFilesDir(), queue, seekMs, playing, epoch);
    }

    static void saveToDir(File dir, PlayQueue queue) {
        saveToDir(dir, queue, -1, false, 0L);
    }

    static void saveToDir(File dir, PlayQueue queue, int seekMs, boolean playing, long epoch) {
        if (dir == null || queue == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (PlayQueue.QueueItem q : queue.items()) {
                JSONObject o = new JSONObject();
                o.put("kind", q.kind.name());
                if (q.file != null) o.put("path", q.file.getAbsolutePath());
                if (q.episode != null) {
                    o.put("epTitle", q.episode.title);
                    o.put("epUrl", q.episode.audioUrl);
                    o.put("epShow", q.podcastShowTitle);
                    o.put("epSaved", q.podcastFromSaved);
                }
                if (q.reachMeta != null) o.put("reachMeta", q.reachMeta);
                if (q.reachPeerUsername != null) o.put("reachPeer", q.reachPeerUsername);
                if (q.deezerMeta != null) o.put("deezerMeta", q.deezerMeta);
                if (q.deezerTrackId > 0) o.put("deezerTrackId", q.deezerTrackId);
                if (q.kind == PlayQueue.ItemKind.NAVIDROME_STREAM) {
                    o.put("navidromeId", q.navidromeSongId);
                    o.put("navidromeTitle", q.navidromeTitle);
                    o.put("navidromeArtist", q.navidromeArtist);
                    o.put("navidromeAlbum", q.navidromeAlbum);
                    if (q.navidromeCoverArtId != null && !q.navidromeCoverArtId.isEmpty()) {
                        o.put("navidromeCover", q.navidromeCoverArtId);
                    }
                } else if (q.kind == PlayQueue.ItemKind.PLEX_STREAM) {
                    o.put("plexId", q.navidromeSongId);
                    o.put("plexTitle", q.navidromeTitle);
                    o.put("plexArtist", q.navidromeArtist);
                    o.put("plexAlbum", q.navidromeAlbum);
                    if (q.navidromeCoverArtId != null && !q.navidromeCoverArtId.isEmpty()) {
                        o.put("plexCover", q.navidromeCoverArtId);
                    }
                    if (q.plexMediaPartKey != null && !q.plexMediaPartKey.isEmpty()) {
                        o.put("plexPartKey", q.plexMediaPartKey);
                    }
                    if (q.plexContainer != null && !q.plexContainer.isEmpty()) {
                        o.put("plexContainer", q.plexContainer);
                    }
                } else if (q.kind == PlayQueue.ItemKind.JELLYFIN_STREAM) {
                    o.put("jellyfinId", q.navidromeSongId);
                    o.put("jellyfinTitle", q.navidromeTitle);
                    o.put("jellyfinArtist", q.navidromeArtist);
                    o.put("jellyfinAlbum", q.navidromeAlbum);
                    if (q.navidromeCoverArtId != null && !q.navidromeCoverArtId.isEmpty()) {
                        o.put("jellyfinCover", q.navidromeCoverArtId);
                    }
                }
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("index", queue.index());
            root.put("items", arr);
            // 2026-07-19 — Resume fields + epoch for async writer restore guard.
            if (seekMs >= 0) root.put("seekMs", seekMs);
            root.put("playing", playing);
            if (epoch > 0) root.put("epoch", epoch);
            // Keep volatile in sync for same-process readers (overlay uses restore). 2026-07-20
            lastRestoredPlaying = playing;
            if (seekMs >= 0) lastRestoredSeekMs = seekMs;
            if (epoch > 0) lastRestoredEpoch = epoch;
            File f = new File(dir, FILE);
            File tmp = new File(dir, FILE + ".tmp");
            BufferedWriter w = new BufferedWriter(new FileWriter(tmp));
            w.write(root.toString());
            w.close();
            if (f.exists() && !f.delete()) return;
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
        } catch (Exception ignored) {}
    }

    public static boolean restore(Context ctx, PlayQueue queue) {
        if (ctx == null || queue == null) return false;
        return restoreFromDir(ctx.getFilesDir(), queue);
    }

    static boolean restoreFromDir(File dir, PlayQueue queue) {
        if (dir == null || queue == null) return false;
        File f = new File(dir, FILE);
        if (!f.isFile()) return false;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("items");
            if (arr == null || arr.length() == 0) return false;
            int savedIndex = root.optInt("index", 0);
            java.util.ArrayList<PlayQueue.QueueItem> items = new java.util.ArrayList<PlayQueue.QueueItem>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String kind = o.optString("kind", "");
                if ("PODCAST_EPISODE".equals(kind)) {
                    OpenRssClient.Episode ep = new OpenRssClient.Episode(
                            o.optString("epTitle", ""),
                            o.optString("epUrl", ""),
                            "");
                    items.add(PlayQueue.QueueItem.podcast(ep,
                            o.optString("epShow", ""), o.optBoolean("epSaved", false)));
                } else {
                    String path = o.optString("path", "");
                    if ("NAVIDROME_STREAM".equals(kind)) {
                        String navId = o.optString("navidromeId", "");
                        if (navId.isEmpty()) {
                            if (i < savedIndex) savedIndex--;
                            continue;
                        }
                        items.add(PlayQueue.QueueItem.navidrome(navId,
                                o.optString("navidromeTitle", ""),
                                o.optString("navidromeArtist", ""),
                                o.optString("navidromeAlbum", ""),
                                o.optString("navidromeCover", "")));
                        continue;
                    }
                    if ("PLEX_STREAM".equals(kind)) {
                        String plexId = o.optString("plexId", "");
                        if (plexId.isEmpty()) {
                            if (i < savedIndex) savedIndex--;
                            continue;
                        }
                        // 2026-07-15: Restore Part.key/container when present (older JSON omits them).
                        items.add(PlayQueue.QueueItem.plex(plexId,
                                o.optString("plexTitle", ""),
                                o.optString("plexArtist", ""),
                                o.optString("plexAlbum", ""),
                                o.optString("plexCover", ""),
                                o.optString("plexPartKey", ""),
                                o.optString("plexContainer", "")));
                        continue;
                    }
                    if ("JELLYFIN_STREAM".equals(kind)) {
                        String jfId = o.optString("jellyfinId", "");
                        if (jfId.isEmpty()) {
                            if (i < savedIndex) savedIndex--;
                            continue;
                        }
                        items.add(PlayQueue.QueueItem.jellyfin(jfId,
                                o.optString("jellyfinTitle", ""),
                                o.optString("jellyfinArtist", ""),
                                o.optString("jellyfinAlbum", ""),
                                o.optString("jellyfinCover", "")));
                        continue;
                    }
                    if (path.isEmpty()) {
                        if (i < savedIndex) savedIndex--;
                        continue;
                    }
                    File file = new File(path);
                    if ("REACH_STREAM".equals(kind)) {
                        String peer = o.optString("reachPeer", "");
                        items.add(PlayQueue.QueueItem.reach(file, o.optString("reachMeta", file.getName()),
                                peer.isEmpty() ? null : peer));
                    } else if ("DEEZER_STREAM".equals(kind)) {
                        items.add(PlayQueue.QueueItem.deezer(file,
                                o.optString("deezerMeta", file.getName()),
                                o.optLong("deezerTrackId", 0)));
                    } else {
                        items.add(PlayQueue.QueueItem.music(file));
                    }
                }
            }
            if (items.isEmpty()) return false;
            savedIndex = Math.max(0, Math.min(savedIndex, items.size() - 1));
            queue.setAll(items, savedIndex);
            lastRestoredSeekMs = root.optInt("seekMs", -1);
            lastRestoredPlaying = root.optBoolean("playing", false);
            lastRestoredEpoch = root.optLong("epoch", 0L);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lightweight: true if play_queue.json exists and has ≥1 item (no full parse of paths).
     * Was: full JSON parse via persistedItemCount on every save. Reversal: that method body.
     * 2026-07-19
     */
    public static boolean hasPersistedQueue(Context ctx) {
        if (ctx == null) return false;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile() || f.length() < 8) return false;
        try {
            BufferedReader r = new BufferedReader(new FileReader(f));
            String first = r.readLine();
            r.close();
            return first != null && first.contains("\"items\"");
        } catch (Exception e) {
            return false;
        }
    }

    /** Items in on-disk JSON (including paths not yet mounted). */
    public static int persistedItemCount(Context ctx) {
        if (ctx == null) return 0;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile()) return 0;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("items");
            return arr != null ? arr.length() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static int countMissingPaths(Context ctx) {
        if (ctx == null) return 0;
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.isFile()) return 0;
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("items");
            if (arr == null) return 0;
            int missing = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                // ponytail: stream kinds / radio stations are not static local files — skip check.
                String kind = o.optString("kind", "");
                if ("REACH_STREAM".equals(kind) || "DEEZER_STREAM".equals(kind) || "PODCAST_EPISODE".equals(kind)
                        || "FM_STATION".equals(kind) || "INTERNET_RADIO_STATION".equals(kind)
                        || "NAVIDROME_STREAM".equals(kind) || "PLEX_STREAM".equals(kind)
                        || "JELLYFIN_STREAM".equals(kind)) continue;
                String path = o.optString("path", "");
                if (path.isEmpty()) continue;
                if (!new File(path).isFile()) missing++;
            }
            return missing;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void clear(Context ctx) {
        if (ctx == null) return;
        File f = new File(ctx.getFilesDir(), FILE);
        if (f.exists()) f.delete();
    }
}
