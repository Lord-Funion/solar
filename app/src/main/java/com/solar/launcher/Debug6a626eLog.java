package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session 6a626e: home theme icons show music_circle / holobubble.
 * Layman: breadcrumbs for which theme file we tried and whether the preview got a real bitmap.
 * Tech: NDJSON → logcat + host .cursor + device .solar + HTTP ingest.
 * Reversal: ENABLED=false; delete this class + #region agent log sites after fix confirmed.
 */
public final class Debug6a626eLog {
    private static final String TAG = "SolarDbg6a626e";
    private static final String SESSION = "6a626e";
    private static final String FILE = "debug-6a626e.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-6a626e.log";

    /**
     * 2026-07-20 — OFF: sync SD/HTTP on home preview stalled Y1/Y2 wheel (a177c4 H-A).
     * Was true during icon hunt. Reversal: true only while re-debugging home art.
     */
    public static final boolean ENABLED = false;

    private Debug6a626eLog() {}

    /**
     * 2026-07-20 — Append one NDJSON breadcrumb for hypothesis {@code hypothesisId}.
     * Layman: notes what icon path we tried so we can see why Aura art is missing.
     * Technical: never throws; logcat + host + SD + ingest.
     */
    public static void log(String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location != null ? location : "");
            o.put("message", message != null ? message : "");
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            o.put("runId", "home-theme-icons");
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.e(TAG, line);
            append(new File(HOST_PATH), line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
            postIngest(line);
        } catch (Exception ignored) {}
        // #region agent log
        // 2026-07-20 — Meter how long this leftover debug write blocks (a177c4 H-A).
        DebugA177c4Log.onDbg6aWrite(android.os.SystemClock.uptimeMillis() - t0);
        // #endregion
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
        }, "dbg6a626e").start();
    }
}
