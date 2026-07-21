package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session 10d371: NP Vocals/Instrumentals toggle without stems
 * (no progress + original still playing after split).
 * Layman: breadcrumbs prove whether ensure started, progress UI armed, and mix swapped audio.
 * Tech: NDJSON → logcat + SD .solar + HTTP ingest (host .cursor/debug-10d371.log).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug10d371Log {
    private static final String TAG = "SolarDbg10d371";
    private static final String SESSION = "10d371";
    private static final String FILE = "debug-10d371.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";

    /**
     * 2026-07-20 — OFF: SD/HTTP ingest must not sit armed next to wheel/UI paths.
     * Was true during NP solo hunt. Reversal: true only while re-debugging stems.
     */
    public static final boolean ENABLED = false;

    private Debug10d371Log() {}

    /**
     * 2026-07-20 — Append one NDJSON breadcrumb for hypothesis {@code hypothesisId}.
     * Layman: writes what happened so we can see where the solo path stalled.
     * Technical: never throws; skip heavy I/O during Stem/Mix jam.
     */
    public static void log(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location != null ? location : "");
            o.put("message", message != null ? message : "");
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            String runId = "pre-fix";
            if (data != null && data.has("runId")) {
                runId = data.optString("runId", runId);
            }
            o.put("runId", runId);
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.e(TAG, line);
            if (StemOrMixSession.isActive()) return;
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
            append(new File("/data/local/tmp", FILE), line);
            postIngest(line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) {
        try {
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileWriter w = new FileWriter(f, true);
            w.write(line);
            w.write('\n');
            w.close();
        } catch (Exception ignored) {}
    }

    private static void postIngest(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(INGEST).openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    OutputStream os = c.getOutputStream();
                    os.write(line.getBytes("UTF-8"));
                    os.close();
                    c.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (c != null) c.disconnect();
                }
            }
        }, "dbg10d371").start();
    }
}
