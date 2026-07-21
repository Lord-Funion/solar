package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.deezer.DeezerClient;
import com.solar.launcher.deezer.DeezerMedia;
import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.deezer.DeezerSearch;
import com.solar.launcher.deezer.DeezerTrackData;
import com.solar.launcher.deezer.DeezerTrackResolver;
import com.solar.launcher.stem.LalalAccount;
import com.solar.launcher.stem.LalalClient;
import com.solar.launcher.youtube.YouTubeClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 2026-07-21 — Dedicated Request Handler for `/stems` Web Portal and Stem/Media API Endpoints.
 * Layman: serves the rich dark-mode Stem Preparer & Deezer/YouTube/Library interface on port 8080.
 */
public final class SolarStemPortalHandler {

    private SolarStemPortalHandler() {}

    public static boolean handleRequest(String method, String path, InputStream is, OutputStream os,
            int contentLength, Context context, File rootFolder) throws IOException {
        if (path == null) return false;
        if (!path.equals("/stems") && !path.equals("/portal") && !path.startsWith("/api/")) {
            return false;
        }

        if ((path.equals("/stems") || path.equals("/portal")) && method.equals("GET")) {
            writePortalPage(os);
            return true;
        }

        if (path.startsWith("/api/credentials")) {
            if (method.equals("GET")) {
                writeJsonResponse(os, getCredentialsJson(context));
            } else if (method.equals("POST")) {
                String bodyStr = readBodyString(is, contentLength);
                saveCredentials(context, bodyStr);
                writeJsonResponse(os, "{\"ok\":true}");
            }
            return true;
        }

        if (path.startsWith("/api/library_tracks") && method.equals("GET")) {
            writeJsonResponse(os, getLibraryTracksJson(rootFolder));
            return true;
        }

        if (path.startsWith("/api/deezer_search") && method.equals("GET")) {
            String query = getQueryParam(path, "q");
            writeJsonResponse(os, searchDeezerJson(context, query));
            return true;
        }

        if (path.startsWith("/api/deezer_download") && method.equals("POST")) {
            String bodyStr = readBodyString(is, contentLength);
            String jobId = handleDeezerDownloadPost(context, bodyStr);
            writeJsonResponse(os, "{\"ok\":true,\"job_id\":\"" + (jobId != null ? jobId : "") + "\"}");
            return true;
        }

        if (path.startsWith("/api/deezer_stream_info") && method.equals("GET")) {
            String trackIdStr = getQueryParam(path, "id");
            writeJsonResponse(os, getDeezerStreamInfoJson(context, trackIdStr));
            return true;
        }

        if (path.startsWith("/api/youtube_search") && method.equals("GET")) {
            String query = getQueryParam(path, "q");
            writeJsonResponse(os, searchYouTubeJson(context, query));
            return true;
        }

        if (path.startsWith("/api/youtube_download") && method.equals("POST")) {
            String bodyStr = readBodyString(is, contentLength);
            String jobId = handleYouTubeDownloadPost(context, bodyStr);
            writeJsonResponse(os, "{\"ok\":true,\"job_id\":\"" + (jobId != null ? jobId : "") + "\"}");
            return true;
        }

        if (path.startsWith("/api/stem_prepare_device") && method.equals("POST")) {
            String bodyStr = readBodyString(is, contentLength);
            String jobId = handleStemPrepareDevicePost(context, bodyStr);
            writeJsonResponse(os, "{\"ok\":true,\"job_id\":\"" + (jobId != null ? jobId : "") + "\"}");
            return true;
        }

        if (path.startsWith("/api/stem_status") && method.equals("GET")) {
            writeJsonResponse(os, SolarStemJobManager.getInstance().getJobsJsonString());
            return true;
        }

        if (path.startsWith("/api/upload_stem_file") && method.equals("POST")) {
            String trackPath = getQueryParam(path, "path");
            String stemName = getQueryParam(path, "stem");
            boolean ok = handleStemFileUpload(trackPath, stemName, is, contentLength);
            writeJsonResponse(os, "{\"ok\":" + ok + "}");
            return true;
        }

        return false;
    }

    private static void writeJsonResponse(OutputStream os, String json) throws IOException {
        byte[] bytes = (json != null ? json : "{}").getBytes("UTF-8");
        String response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\nContent-Length: " +
                bytes.length + "\r\n\r\n";
        os.write(response.getBytes("UTF-8"));
        os.write(bytes);
        os.flush();
    }

    private static String readBodyString(InputStream is, int contentLength) throws IOException {
        if (contentLength <= 0) return "";
        byte[] buf = new byte[Math.min(contentLength, 1024 * 1024)];
        int total = 0;
        while (total < contentLength) {
            int r = is.read(buf, total, contentLength - total);
            if (r == -1) break;
            total += r;
        }
        return new String(buf, 0, total, "UTF-8");
    }

    private static String getQueryParam(String path, String key) {
        if (path == null || path.indexOf('?') < 0) return "";
        String q = path.substring(path.indexOf('?') + 1);
        String[] pairs = q.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length >= 2 && kv[0].equals(key)) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (Exception ignored) {
                    return kv[1];
                }
            }
        }
        return "";
    }

    private static String getCredentialsJson(Context context) {
        JSONObject obj = new JSONObject();
        try {
            SharedPreferences deezerPrefs = context.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences lalalPrefs = context.getSharedPreferences(LalalAccount.PREFS_NAME, Context.MODE_PRIVATE);

            String arl = DeezerAccount.loadArl(deezerPrefs);
            obj.put("deezer_configured", arl != null && arl.length() >= 64);
            obj.put("deezer_arl_preview", (arl != null && arl.length() >= 8) ? (arl.substring(0, 4) + "..." + arl.substring(arl.length() - 4)) : "");

            String key = LalalAccount.loadUserKey(lalalPrefs);
            String effective = LalalAccount.effectiveKey(lalalPrefs);
            boolean isUser = LalalAccount.isUserConfigured(lalalPrefs);
            boolean isDemo = !isUser && effective != null && effective.length() >= 8;

            obj.put("lalal_configured", isUser);
            obj.put("lalal_is_demo", isDemo);
            obj.put("lalal_key_preview", (key != null && key.length() >= 6) ? (key.substring(0, 3) + "..." + key.substring(key.length() - 3)) : (isDemo ? "DEMO_KEY_ACTIVE" : ""));
        } catch (Exception ignored) {}
        return obj.toString();
    }

    private static void saveCredentials(Context context, String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            if (body.has("deezer_arl")) {
                String arl = body.optString("deezer_arl", "").trim();
                SharedPreferences deezerPrefs = context.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
                DeezerAccount.saveUserArl(deezerPrefs, arl);
            }
            if (body.has("lalal_key")) {
                String key = body.optString("lalal_key", "").trim();
                SharedPreferences lalalPrefs = context.getSharedPreferences(LalalAccount.PREFS_NAME, Context.MODE_PRIVATE);
                if ("DEMO".equalsIgnoreCase(key) || key.isEmpty()) {
                    LalalAccount.saveUserKey(lalalPrefs, "");
                } else {
                    LalalAccount.saveUserKey(lalalPrefs, key);
                }
            }
        } catch (Exception ignored) {}
    }

    private static String getLibraryTracksJson(File rootFolder) {
        JSONArray arr = new JSONArray();
        List<File> tracks = new ArrayList<File>();
        scanAudioFiles(rootFolder, tracks, 0, 4);
        Collections.sort(tracks, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : tracks) {
            try {
                JSONObject item = new JSONObject();
                item.put("path", f.getAbsolutePath());
                item.put("filename", f.getName());
                String rel = f.getAbsolutePath();
                if (rootFolder != null && rel.startsWith(rootFolder.getAbsolutePath())) {
                    rel = rel.substring(rootFolder.getAbsolutePath().length());
                    if (rel.startsWith("/")) rel = rel.substring(1);
                }
                item.put("rel_path", rel);
                item.put("size", f.length());
                boolean stemsReady = LalalClient.userStemsReady(f);
                item.put("has_stems", stemsReady);
                File sDir = LalalClient.userStemsDir(f);
                item.put("stems_dir", sDir != null ? sDir.getName() : "");
                arr.put(item);
            } catch (Exception ignored) {}
        }
        JSONObject root = new JSONObject();
        try { root.put("tracks", arr); } catch (Exception ignored) {}
        return root.toString();
    }

    private static void scanAudioFiles(File dir, List<File> out, int depth, int maxDepth) {
        if (dir == null || !dir.isDirectory() || depth > maxDepth) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.endsWith(".stems") || "lalal_stems".equals(name) || "lalal_work".equals(name)) continue;
                scanAudioFiles(f, out, depth + 1, maxDepth);
            } else if (f.isFile()) {
                String nameLower = f.getName().toLowerCase(java.util.Locale.US);
                if (nameLower.endsWith(".mp3") || nameLower.endsWith(".flac") || nameLower.endsWith(".wav")
                        || nameLower.endsWith(".ogg") || nameLower.endsWith(".m4a") || nameLower.endsWith(".aac")) {
                    if (!LalalClient.isStemLibraryArtifact(f)) {
                        out.add(f);
                    }
                }
            }
        }
    }

    private static String searchDeezerJson(Context context, String query) {
        JSONArray arr = new JSONArray();
        if (query != null && !query.trim().isEmpty()) {
            try {
                SharedPreferences prefs = context.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
                DeezerClient client = new DeezerClient(prefs);
                DeezerSearch search = new DeezerSearch(client);
                List<DeezerResult> results = search.searchTracks(query);
                if (results != null) {
                    for (DeezerResult r : results) {
                        if (r == null) continue;
                        JSONObject item = new JSONObject();
                        item.put("id", r.id);
                        item.put("title", r.title);
                        item.put("artist", r.artist);
                        item.put("album", r.album);
                        item.put("durationSec", r.durationSec);
                        item.put("coverUrl", r.coverUrl);
                        item.put("previewUrl", r.previewUrl);
                        arr.put(item);
                    }
                }
            } catch (Exception ignored) {}
        }
        JSONObject root = new JSONObject();
        try { root.put("results", arr); } catch (Exception ignored) {}
        return root.toString();
    }

    private static String handleDeezerDownloadPost(Context context, String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            long id = body.optLong("track_id", 0);
            String title = body.optString("title", "");
            String artist = body.optString("artist", "");
            boolean autoStems = body.optBoolean("auto_stems", true);
            if (id > 0) {
                return SolarStemJobManager.getInstance().startDeezerDownloadAndPrepare(context, id, title, artist, autoStems);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String getDeezerStreamInfoJson(Context context, String trackIdStr) {
        JSONObject root = new JSONObject();
        try {
            long trackId = Long.parseLong(trackIdStr);
            SharedPreferences prefs = context.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
            DeezerClient client = new DeezerClient(prefs);
            DeezerTrackResolver resolver = new DeezerTrackResolver(client);
            DeezerMedia media = new DeezerMedia(client);
            if (!client.isSessionValid()) client.initSession();
            DeezerTrackData track = resolver.resolveTrack(trackId);
            String cdnUrl = null;
            try {
                cdnUrl = media.resolveUrl(track.trackToken);
            } catch (IOException e) {
                if (track.fallback != null) {
                    track = track.fallback;
                    cdnUrl = media.resolveUrl(track.trackToken);
                }
            }
            if (cdnUrl != null) {
                root.put("ok", true);
                root.put("url", cdnUrl);
                root.put("title", track.title);
                root.put("artist", track.artist);
            } else {
                root.put("ok", false);
                root.put("error", "Stream resolve failed");
            }
        } catch (Exception e) {
            try {
                root.put("ok", false);
                root.put("error", e.getMessage() != null ? e.getMessage() : "Error");
            } catch (Exception ignored) {}
        }
        return root.toString();
    }

    private static String searchYouTubeJson(Context context, String query) {
        final AtomicReference<String> jsonRef = new AtomicReference<String>("{\"results\":[]}");
        if (query != null && !query.trim().isEmpty()) {
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                YouTubeClient.getInstance(context).search(query, new YouTubeClient.Callback() {
                    @Override
                    public void onSuccess(String payloadJson) {
                        if (payloadJson != null && !payloadJson.isEmpty()) {
                            jsonRef.set(payloadJson);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        latch.countDown();
                    }
                });
                latch.await(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        String raw = jsonRef.get();
        if (raw != null && raw.trim().startsWith("[")) {
            return "{\"results\":" + raw + "}";
        }
        return raw != null ? raw : "{\"results\":[]}";
    }

    private static String handleYouTubeDownloadPost(Context context, String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            String videoId = body.optString("video_id", "");
            String title = body.optString("title", "");
            String author = body.optString("author", "");
            boolean autoStems = body.optBoolean("auto_stems", true);
            if (videoId != null && !videoId.isEmpty()) {
                return SolarStemJobManager.getInstance().startYouTubeDownloadAndPrepare(context, videoId, title, author, autoStems);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String handleStemPrepareDevicePost(Context context, String bodyStr) {
        try {
            JSONObject body = new JSONObject(bodyStr);
            String path = body.optString("path", "");
            if (path != null && !path.isEmpty()) {
                File track = new File(path);
                if (track.isFile()) {
                    return SolarStemJobManager.getInstance().startDeviceStemPreparation(context, track);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean handleStemFileUpload(String trackPath, String stemName, InputStream is, int contentLength) {
        if (trackPath == null || trackPath.isEmpty() || stemName == null || stemName.isEmpty()) return false;
        File track = new File(trackPath);
        if (!track.isFile()) return false;
        File stemsDir = LalalClient.userStemsDir(track);
        if (stemsDir == null) return false;
        if (!stemsDir.exists()) stemsDir.mkdirs();

        if ("DONE".equalsIgnoreCase(stemName)) {
            LalalClient.writeTrackMarkerIfOwned(stemsDir, track);
            return true;
        }

        File dest = new File(stemsDir, stemName);
        try {
            FileOutputStream fos = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int total = 0;
            while (total < contentLength) {
                int r = is.read(buf, 0, (int) Math.min(buf.length, contentLength - total));
                if (r == -1) break;
                fos.write(buf, 0, r);
                total += r;
            }
            fos.flush();
            fos.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writePortalPage(OutputStream os) throws IOException {
        String html = "<!DOCTYPE html>" +
                "<html lang='en'><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<title>Solar Stem Preparer & Web Portal</title>" +
                "<style>" +
                ":root { --bg: #0a0b0e; --panel: #14161c; --card: #1b1e26; --accent: #00ffff; --purple: #9d4edd; --text: #f0f3f8; --sub: #8b92a5; --border: rgba(0,255,255,0.15); }" +
                "* { box-sizing: border-box; margin: 0; padding: 0; }" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Outfit, sans-serif; background: var(--bg); color: var(--text); padding: 20px; min-height: 100vh; }" +
                ".header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid var(--border); margin-bottom: 24px; }" +
                ".logo { font-size: 26px; font-weight: 800; background: linear-gradient(90deg, var(--accent), var(--purple)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; letter-spacing: -0.5px; }" +
                ".nav-links a { color: var(--accent); text-decoration: none; margin-left: 20px; font-weight: 600; font-size: 14px; padding: 8px 16px; border-radius: 20px; border: 1px solid var(--border); transition: all 0.2s; }" +
                ".nav-links a:hover { background: rgba(0,255,255,0.1); box-shadow: 0 0 12px rgba(0,255,255,0.3); }" +
                ".tabs { display: flex; gap: 12px; margin-bottom: 24px; overflow-x: auto; padding-bottom: 4px; }" +
                ".tab-btn { background: var(--panel); border: 1px solid var(--border); color: var(--text); padding: 12px 24px; border-radius: 12px; cursor: pointer; font-weight: 700; font-size: 15px; transition: all 0.2s; display: flex; align-items: center; gap: 8px; }" +
                ".tab-btn.active, .tab-btn:hover { background: linear-gradient(135deg, rgba(0,255,255,0.2), rgba(157,78,221,0.2)); border-color: var(--accent); box-shadow: 0 0 16px rgba(0,255,255,0.2); color: var(--accent); }" +
                ".panel { display: none; background: var(--panel); border-radius: 16px; padding: 24px; border: 1px solid var(--border); box-shadow: 0 8px 32px rgba(0,0,0,0.5); }" +
                ".panel.active { display: block; animation: fadeIn 0.3s ease; }" +
                "@keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }" +
                ".search-bar { display: flex; gap: 12px; margin-bottom: 24px; }" +
                ".search-bar input { flex: 1; background: var(--bg); border: 1px solid var(--border); color: var(--text); padding: 14px 18px; border-radius: 12px; font-size: 16px; outline: none; transition: border 0.2s; }" +
                ".search-bar input:focus { border-color: var(--accent); box-shadow: 0 0 10px rgba(0,255,255,0.2); }" +
                ".btn { background: var(--accent); color: #000; border: none; padding: 12px 24px; border-radius: 12px; font-weight: 800; cursor: pointer; transition: all 0.2s; font-size: 15px; }" +
                ".btn:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(0,255,255,0.4); }" +
                ".btn-purple { background: var(--purple); color: #fff; }" +
                ".btn-purple:hover { box-shadow: 0 4px 16px rgba(157,78,221,0.4); }" +
                ".btn-sm { padding: 8px 16px; font-size: 13px; border-radius: 8px; }" +
                ".grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }" +
                ".card { background: var(--card); border: 1px solid rgba(255,255,255,0.06); border-radius: 14px; padding: 16px; display: flex; flex-direction: column; justify-content: space-between; gap: 14px; transition: all 0.2s; }" +
                ".card:hover { border-color: var(--accent); transform: translateY(-3px); box-shadow: 0 6px 20px rgba(0,0,0,0.6); }" +
                ".card-header { display: flex; gap: 12px; align-items: center; }" +
                ".card-img { width: 54px; height: 54px; border-radius: 10px; object-fit: cover; background: #2a2e3b; flex-shrink: 0; }" +
                ".card-info { overflow: hidden; }" +
                ".card-title { font-weight: 700; font-size: 15px; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }" +
                ".card-artist { font-size: 13px; color: var(--sub); margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }" +
                ".badge { display: inline-block; padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 800; text-transform: uppercase; letter-spacing: 0.5px; }" +
                ".badge-ready { background: rgba(0,255,100,0.15); color: #00ff64; border: 1px solid rgba(0,255,100,0.3); }" +
                ".badge-raw { background: rgba(255,200,0,0.15); color: #ffca00; border: 1px solid rgba(255,200,0,0.3); }" +
                ".card-actions { display: flex; gap: 8px; flex-wrap: wrap; }" +
                ".status-box { background: var(--card); border-radius: 12px; padding: 16px; margin-bottom: 12px; border-left: 4px solid var(--accent); }" +
                ".progress-bar { width: 100%; height: 8px; background: #2a2e3b; border-radius: 4px; overflow: hidden; margin-top: 10px; }" +
                ".progress-fill { height: 100%; background: linear-gradient(90deg, var(--accent), var(--purple)); width: 0%; transition: width 0.3s ease; }" +
                "</style>" +
                "</head><body>" +
                "<div class='header'>" +
                "  <div class='logo'>🚀 SOLAR STEM PORTAL</div>" +
                "  <div class='nav-links'><a href='/'>📁 Storage Upload</a><a href='/deezer'>🎵 Deezer Setup</a><a href='/lalal'>⚙️ Lalal Setup</a></div>" +
                "</div>" +
                "<div class='tabs'>" +
                "  <button class='tab-btn active' onclick='showTab(\"library\")'>🎧 Player Library</button>" +
                "  <button class='tab-btn' onclick='showTab(\"deezer\")'>🎵 Deezer Search</button>" +
                "  <button class='tab-btn' onclick='showTab(\"youtube\")'>📺 YouTube Search</button>" +
                "  <button class='tab-btn' onclick='showTab(\"status\")'>⚡ Live Status &amp; Settings</button>" +
                "</div>" +
                "<div id='tab-library' class='panel active'>" +
                "  <div style='display:flex; justify-content:space-between; align-items:center; margin-bottom:20px;'>" +
                "    <h3>🎵 Music Files on Player</h3>" +
                "    <button class='btn btn-sm' onclick='loadLibrary()'>🔄 Refresh Library</button>" +
                "  </div>" +
                "  <div id='library-list' class='grid'>Loading library...</div>" +
                "</div>" +
                "<div id='tab-deezer' class='panel'>" +
                "  <div class='search-bar'>" +
                "    <input type='text' id='deezer-q' placeholder='Search Deezer tracks (e.g. Daft Punk, Synthwave)...' onkeydown='if(event.key===\"Enter\") searchDeezer()'>" +
                "    <button class='btn' onclick='searchDeezer()'>Search Deezer</button>" +
                "  </div>" +
                "  <div id='deezer-list' class='grid'>Search Deezer to discover tracks and prepare stems!</div>" +
                "</div>" +
                "<div id='tab-youtube' class='panel'>" +
                "  <div class='search-bar'>" +
                "    <input type='text' id='youtube-q' placeholder='Search YouTube audio streams...' onkeydown='if(event.key===\"Enter\") searchYouTube()'>" +
                "    <button class='btn btn-purple' onclick='searchYouTube()'>Search YouTube</button>" +
                "  </div>" +
                "  <div id='youtube-list' class='grid'>Search YouTube for rare tracks, lives, and remixes!</div>" +
                "</div>" +
                "<div id='tab-status' class='panel'>" +
                "  <h3 style='margin-bottom:16px;'>⚡ Active &amp; Recent Processing Jobs</h3>" +
                "  <div id='jobs-container'>No jobs currently running.</div>" +
                "  <hr style='border:0; border-top:1px solid var(--border); margin:24px 0;'>" +
                "  <h3 style='margin-bottom:16px;'>⚙️ Credentials Configuration</h3>" +
                "  <div style='max-width:500px; display:flex; flex-direction:column; gap:12px;'>" +
                "    <div><label style='font-size:13px; color:var(--sub);'>Deezer ARL Cookie:</label>" +
                "      <input type='password' id='setting-deezer-arl' style='width:100%; background:var(--bg); border:1px solid var(--border); color:#fff; padding:10px; border-radius:8px; margin-top:4px;' placeholder='Paste Deezer arl cookie...'></div>" +
                "    <div><label style='font-size:13px; color:var(--sub);'>Lalal.ai API Key (leave empty or type DEMO for silent bundled key):</label>" +
                "      <input type='password' id='setting-lalal-key' style='width:100%; background:var(--bg); border:1px solid var(--border); color:#fff; padding:10px; border-radius:8px; margin-top:4px;' placeholder='Paste Lalal license key...'></div>" +
                "    <button class='btn' onclick='saveSettings()'>Save Settings</button>" +
                "    <div id='settings-status' style='color:#00ff64; font-weight:bold;'></div>" +
                "  </div>" +
                "</div>" +
                "<script>" +
                "function showTab(t) {" +
                "  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));" +
                "  document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));" +
                "  document.querySelector(`[onclick='showTab(\"${t}\")']`).classList.add('active');" +
                "  document.getElementById(`tab-${t}`).classList.add('active');" +
                "  if(t === 'library') loadLibrary();" +
                "  if(t === 'status') { loadJobs(); loadSettings(); }" +
                "}" +
                "async function loadLibrary() {" +
                "  const el = document.getElementById('library-list');" +
                "  const res = await fetch('/api/library_tracks');" +
                "  const data = await res.json();" +
                "  if(!data.tracks || data.tracks.length === 0) { el.innerHTML = '<p>No music files found.</p>'; return; }" +
                "  el.innerHTML = data.tracks.map(t => `" +
                "    <div class='card'>" +
                "      <div class='card-header'>" +
                "        <div class='card-info'>" +
                "          <div class='card-title' title='${t.filename}'>${t.filename}</div>" +
                "          <div class='card-artist'>Folder: ${t.rel_path} • ${(t.size/1024/1024).toFixed(1)} MB</div>" +
                "        </div>" +
                "      </div>" +
                "      <div>${t.has_stems ? '<span class=\"badge badge-ready\">⚡ Stems Ready</span>' : '<span class=\"badge badge-raw\">Raw Track</span>'}</div>" +
                "      <div class='card-actions'>" +
                "        <button class='btn btn-sm' onclick='prepareOnDevice(\"${t.path.replace(/\\/g, \"\\\\\\\\\").replace(/\"/g, \"\\\\\\\"\")}\")'>🎛️ Prepare Stems on Device</button>" +
                "      </div>" +
                "    </div>" +
                "  `).join('');" +
                "}" +
                "async function prepareOnDevice(path) {" +
                "  await fetch('/api/stem_prepare_device', { method:'POST', body:JSON.stringify({ path }) });" +
                "  showTab('status');" +
                "}" +
                "async function searchDeezer() {" +
                "  const q = document.getElementById('deezer-q').value;" +
                "  if(!q) return;" +
                "  const el = document.getElementById('deezer-list');" +
                "  el.innerHTML = 'Searching Deezer...';" +
                "  const res = await fetch(`/api/deezer_search?q=${encodeURIComponent(q)}`);" +
                "  const data = await res.json();" +
                "  if(!data.results || data.results.length === 0) { el.innerHTML = '<p>No tracks found on Deezer.</p>'; return; }" +
                "  el.innerHTML = data.results.map(r => `" +
                "    <div class='card'>" +
                "      <div class='card-header'>" +
                "        ${r.coverUrl ? `<img src='${r.coverUrl}' class='card-img'>` : '<div class=\"card-img\"></div>'}" +
                "        <div class='card-info'>" +
                "          <div class='card-title'>${r.title}</div>" +
                "          <div class='card-artist'>${r.artist} • ${r.album}</div>" +
                "        </div>" +
                "      </div>" +
                "      <div class='card-actions'>" +
                "        <button class='btn btn-sm' onclick='downloadDeezer(${r.id}, \"${r.title.replace(/\"/g, \"\\\\\\\"\")}\", \"${r.artist.replace(/\"/g, \"\\\\\\\"\")}\", true)'>⚡ Download + Stems</button>" +
                "        <button class='btn btn-sm btn-purple' onclick='downloadDeezer(${r.id}, \"${r.title.replace(/\"/g, \"\\\\\\\"\")}\", \"${r.artist.replace(/\"/g, \"\\\\\\\"\")}\", false)'>🎵 Audio Only</button>" +
                "      </div>" +
                "    </div>" +
                "  `).join('');" +
                "}" +
                "async function downloadDeezer(track_id, title, artist, auto_stems) {" +
                "  await fetch('/api/deezer_download', { method:'POST', body:JSON.stringify({ track_id, title, artist, auto_stems }) });" +
                "  showTab('status');" +
                "}" +
                "async function searchYouTube() {" +
                "  const q = document.getElementById('youtube-q').value;" +
                "  if(!q) return;" +
                "  const el = document.getElementById('youtube-list');" +
                "  el.innerHTML = 'Searching YouTube...';" +
                "  const res = await fetch(`/api/youtube_search?q=${encodeURIComponent(q)}`);" +
                "  const data = await res.json();" +
                "  if(!data.results || data.results.length === 0) { el.innerHTML = '<p>No streams found on YouTube.</p>'; return; }" +
                "  el.innerHTML = data.results.map(r => `" +
                "    <div class='card'>" +
                "      <div class='card-header'>" +
                "        <div class='card-info'>" +
                "          <div class='card-title'>${r.title || r.id}</div>" +
                "          <div class='card-artist'>${r.author || ''} • ${r.durationFormatted || ''}</div>" +
                "        </div>" +
                "      </div>" +
                "      <div class='card-actions'>" +
                "        <button class='btn btn-sm btn-purple' onclick='downloadYouTube(\"${r.id}\", \"${(r.title||\"\").replace(/\"/g, \"\\\\\\\"\")}\", \"${(r.author||\"\").replace(/\"/g, \"\\\\\\\"\")}\", true)'>⚡ Download + Stems</button>" +
                "      </div>" +
                "    </div>" +
                "  `).join('');" +
                "}" +
                "async function downloadYouTube(video_id, title, author, auto_stems) {" +
                "  await fetch('/api/youtube_download', { method:'POST', body:JSON.stringify({ video_id, title, author, auto_stems }) });" +
                "  showTab('status');" +
                "}" +
                "async function loadJobs() {" +
                "  const el = document.getElementById('jobs-container');" +
                "  const res = await fetch('/api/stem_status');" +
                "  const data = await res.json();" +
                "  if(!data.jobs || data.jobs.length === 0) { el.innerHTML = '<p style=\"color:var(--sub);\">No jobs currently active or recent.</p>'; return; }" +
                "  el.innerHTML = data.jobs.map(j => `" +
                "    <div class='status-box' style='border-left-color: ${j.phase===\"ready\"?\"#00ff64\":(j.phase===\"error\"?\"#ff4444\":\"#00ffff\")}'>" +
                "      <div style='display:flex; justify-content:space-between; font-weight:bold; font-size:15px;'>" +
                "        <span>[${j.source.toUpperCase()}] ${j.title} ${j.artist ? (\"- \" + j.artist) : \"\"}</span>" +
                "        <span>${j.phase.toUpperCase()} (${j.percent}%)</span>" +
                "      </div>" +
                "      <div style='font-size:13px; color:var(--sub); margin-top:6px;'>${j.detail || j.error}</div>" +
                "      <div class='progress-bar'><div class='progress-fill' style='width: ${j.percent}%; background: ${j.phase===\"ready\"?\"#00ff64\":(j.phase===\"error\"?\"#ff4444\":\"linear-gradient(90deg, #00ffff, #9d4edd)\")}'></div></div>" +
                "    </div>" +
                "  `).join('');" +
                "}" +
                "async function loadSettings() {" +
                "  const res = await fetch('/api/credentials');" +
                "  const d = await res.json();" +
                "  document.getElementById('settings-status').innerText = `Deezer: ${d.deezer_configured ? \"✅ Configured\" : \"❌ Not set\"} • Lalal.ai: ${d.lalal_configured ? \"✅ Configured\" : (d.lalal_is_demo ? \"✨ Bundled Demo Active\" : \"❌ Not set\")}`;" +
                "}" +
                "async function saveSettings() {" +
                "  const arl = document.getElementById('setting-deezer-arl').value;" +
                "  const key = document.getElementById('setting-lalal-key').value;" +
                "  await fetch('/api/credentials', { method:'POST', body:JSON.stringify({ deezer_arl: arl, lalal_key: key }) });" +
                "  document.getElementById('setting-deezer-arl').value = '';" +
                "  document.getElementById('setting-lalal-key').value = '';" +
                "  loadSettings();" +
                "}" +
                "setInterval(() => { if(document.getElementById('tab-status').classList.contains('active')) loadJobs(); }, 2000);" +
                "loadLibrary();" +
                "</script></body></html>";

        byte[] bytes = html.getBytes("UTF-8");
        String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " +
                bytes.length + "\r\n\r\n";
        os.write(response.getBytes("UTF-8"));
        os.write(bytes);
        os.flush();
    }
}
