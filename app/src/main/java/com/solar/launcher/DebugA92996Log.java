package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session a92996: Search… header index misplacement (context / stem / solo).
 * Layman: breadcrumbs prove focused row vs file used for menus and playback.
 * Tech: NDJSON → logcat + SD .solar + HTTP ingest (host .cursor/debug-a92996.log).
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class DebugA92996Log {
    private static final String TAG = "SolarDbgA92996";
    private static final String SESSION = "a92996";
    private static final String FILE = "debug-a92996.log";
    private static final String INGEST =
            "http://127.0.0.1:7386/ingest/e2ddb16b-6e99-4a8a-88a9-bd955259c699";

    /** On while hunting Search-header off-by-one. Flip false before ship. */
    public static final boolean ENABLED = false; // 2026-07-20 — off: no SD/HTTP metering on device

    private DebugA92996Log() {}

    /**
     * 2026-07-20 — Append one NDJSON breadcrumb for hypothesis {@code hypothesisId}.
     * Layman: writes what happened so we can see wrong song vs focused row.
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
            // Prefer runId from data when present (post-fix vs pre-fix). 2026-07-20
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
            // 2026-07-20 — Always-writable fallback when SD .solar mkdir fails.
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
        }, "dbgA92996").start();
    }
}
