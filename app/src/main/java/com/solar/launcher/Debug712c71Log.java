package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session 712c71: YouTube search shows empty for known hits.
 * Layman: breadcrumbs prove whether backends answered, timed out, or parsed to zero.
 * Tech: NDJSON → logcat + filesDir + SD .solar + HTTP ingest (adb reverse tcp:7652).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug712c71Log {

    private static final String TAG = "SolarDbg712c71";
    private static final String SESSION = "712c71";
    private static final String FILE = "debug-712c71.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-712c71.log";

    /** 2026-07-20 — On while hunting empty YouTube search; flip false after confirmed. */
    public static volatile boolean ENABLED = false;

    private Debug712c71Log() {}

    /** Append one NDJSON sample (hypothesisId tags which theory). */
    public static void log(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            o.put("runId", "pre-fix");
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.e(TAG, line);
            try {
                append(new File(HOST_PATH), line);
            } catch (Exception ignoredHost) {}
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            try {
                File primary = DeviceFeatures.getPrimaryStorageRoot();
                if (primary != null) {
                    File solar = new File(primary, ".solar");
                    if (!solar.exists()) solar.mkdirs();
                    append(new File(solar, FILE), line);
                }
            } catch (Exception ignoredSd) {}
            postIngest(line);
        } catch (Exception ignored) {}
    }

    /** No Context — still hits logcat + ingest + host path when reachable. */
    public static void log(String location, String message, String hypothesisId,
            JSONObject data) {
        log(null, location, message, hypothesisId, data);
    }

    private static void append(File f, String line) throws java.io.IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    private static void postIngest(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(INGEST).openConnection();
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    byte[] body = line.getBytes("UTF-8");
                    c.setFixedLengthStreamingMode(body.length);
                    OutputStream os = new BufferedOutputStream(c.getOutputStream());
                    os.write(body);
                    os.flush();
                    os.close();
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "Dbg712c71").start();
    }
}
