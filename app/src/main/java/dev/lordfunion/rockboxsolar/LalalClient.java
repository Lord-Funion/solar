package dev.lordfunion.rockboxsolar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

final class LalalClient {
    interface Progress { void onProgress(String status); }
    static final class SplitResult {
        String sourceId;
        String taskId;
        final ArrayList<RemoteTrack> tracks = new ArrayList<RemoteTrack>();
    }
    static final class RemoteTrack {
        String label;
        String type;
        String url;
    }

    private static final String BASE = "https://www.lalal.ai/api/v1";
    private final OkHttpClient client;
    private final String key;

    LalalClient(OkHttpClient client, String key) { this.client = client; this.key = key; }

    double minutesLeft() throws Exception {
        JSONObject root = postJson("/limits/minutes_left/", new JSONObject());
        return root.optDouble("minutes_left", -1d);
    }

    SplitResult split(File file, String stem, String extraction, Progress progress) throws Exception {
        if (!file.isFile()) throw new IOException("File not found: " + file);
        progress.onProgress("Uploading " + file.getName() + "…");
        String sourceId = upload(file);
        progress.onProgress("Starting " + stem + " separation…");
        String taskId = startSplit(sourceId, stem, extraction);
        SplitResult result = new SplitResult();
        result.sourceId = sourceId; result.taskId = taskId;
        for (int attempt = 0; attempt < 240; attempt++) {
            progress.onProgress("Processing stems… " + (attempt * 5) + "s");
            JSONObject checked = check(taskId);
            JSONObject task = checked.optJSONObject("result");
            if (task != null) task = task.optJSONObject(taskId);
            if (task == null && checked.has(taskId)) task = checked.optJSONObject(taskId);
            if (task != null) {
                String status = task.optString("status", "");
                if ("error".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
                    throw new IOException(task.optString("error", task.optString("message", "LALAL.AI task failed")));
                }
                JSONObject output = task.optJSONObject("result");
                if (output != null) {
                    JSONArray tracks = output.optJSONArray("tracks");
                    if (tracks != null && tracks.length() > 0) {
                        for (int i = 0; i < tracks.length(); i++) {
                            JSONObject item = tracks.optJSONObject(i);
                            if (item == null) continue;
                            RemoteTrack track = new RemoteTrack();
                            track.label = item.optString("label", item.optString("type", "track"));
                            track.type = item.optString("type", "stem");
                            track.url = item.optString("url", "");
                            if (track.url.length() > 0) result.tracks.add(track);
                        }
                        if (!result.tracks.isEmpty()) return result;
                    }
                }
            }
            Thread.sleep(5000L);
        }
        throw new IOException("Stem task timed out");
    }

    List<File> downloadAll(SplitResult result, File directory, String baseName, Progress progress) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create " + directory);
        ArrayList<File> files = new ArrayList<File>();
        for (RemoteTrack track : result.tracks) {
            String safe = sanitize(baseName) + " - " + sanitize(track.label) + extension(track.url);
            File output = unique(directory, safe);
            progress.onProgress("Downloading " + track.label + "…");
            Request request = new Request.Builder().url(track.url).build();
            Response response = client.newCall(request).execute();
            try {
                if (!response.isSuccessful() || response.body() == null) throw new IOException("Track HTTP " + response.code());
                InputStream input = response.body().byteStream();
                File part = new File(output.getAbsolutePath() + ".part");
                FileOutputStream stream = new FileOutputStream(part);
                byte[] buffer = new byte[32768]; int read;
                while ((read = input.read(buffer)) >= 0) stream.write(buffer, 0, read);
                stream.flush(); stream.getFD().sync(); stream.close(); input.close();
                if (!part.renameTo(output)) throw new IOException("Could not finalize " + output.getName());
                files.add(output);
            } finally { response.close(); }
        }
        return files;
    }

    void deleteSource(String sourceId) {
        try { postJson("/delete/", new JSONObject().put("source_id", sourceId)); } catch (Exception ignored) { }
    }

    private String upload(File file) throws Exception {
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        Request request = new Request.Builder().url(BASE + "/upload/")
                .header("X-License-Key", key)
                .header("Content-Disposition", "attachment; filename=\"" + file.getName().replace("\"", "_") + "\"")
                .post(body).build();
        Response response = client.newCall(request).execute();
        try {
            JSONObject json = parse(response);
            String id = json.optString("id", json.optString("source_id", ""));
            if (id.length() == 0) throw new IOException("Upload response did not contain a source ID");
            return id;
        } finally { response.close(); }
    }

    private String startSplit(String sourceId, String stem, String extraction) throws Exception {
        JSONObject presets = new JSONObject();
        presets.put("stem", stem);
        presets.put("splitter", "auto");
        presets.put("extraction_level", extraction);
        JSONObject payload = new JSONObject();
        payload.put("source_id", sourceId);
        payload.put("presets", presets);
        JSONObject json = postJson("/split/stem_separator/", payload);
        String task = json.optString("task_id", json.optString("id", ""));
        if (task.length() == 0) {
            JSONObject result = json.optJSONObject("result");
            if (result != null) task = result.optString("task_id", result.optString("id", ""));
        }
        if (task.length() == 0) throw new IOException("Split response did not contain a task ID");
        return task;
    }

    private JSONObject check(String taskId) throws Exception {
        JSONArray ids = new JSONArray(); ids.put(taskId);
        return postJson("/check/", new JSONObject().put("task_ids", ids));
    }

    private JSONObject postJson(String path, JSONObject payload) throws Exception {
        RequestBody body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString());
        Request request = new Request.Builder().url(BASE + path).header("X-License-Key", key).post(body).build();
        Response response = client.newCall(request).execute();
        try { return parse(response); } finally { response.close(); }
    }

    private static JSONObject parse(Response response) throws Exception {
        String text = response.body() == null ? "" : response.body().string();
        JSONObject json = text.length() == 0 ? new JSONObject() : new JSONObject(text);
        if (!response.isSuccessful()) {
            String message = json.optString("detail", json.optString("error", json.optString("message", "HTTP " + response.code())));
            throw new IOException(message);
        }
        return json;
    }

    private static String sanitize(String value) {
        String clean = value == null ? "track" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return clean.length() == 0 ? "track" : clean;
    }

    private static String extension(String url) {
        String lower = url == null ? "" : url.toLowerCase();
        if (lower.contains(".flac")) return ".flac";
        if (lower.contains(".wav")) return ".wav";
        if (lower.contains(".m4a")) return ".m4a";
        return ".mp3";
    }

    private static File unique(File directory, String name) {
        File file = new File(directory, name); if (!file.exists()) return file;
        int dot = name.lastIndexOf('.'); String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) { file = new File(directory, base + " (" + i + ")" + ext); if (!file.exists()) return file; }
        return new File(directory, System.currentTimeMillis() + "-" + name);
    }
}
