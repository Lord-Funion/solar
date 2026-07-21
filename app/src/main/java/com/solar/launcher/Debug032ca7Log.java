package com.solar.launcher;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 2026-07-20 — Debug session 032ca7: context-menu hold feels too long on Y1/Y2/A5.
 * Layman: timestamps from button-down until Options paints.
 * Tech: NDJSON → logcat + SD .solar + HTTP ingest (adb reverse tcp:7386).
 * Pull: adb pull /storage/sdcard0/.solar/debug-032ca7.log .cursor/
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug032ca7Log {
    private static final String TAG = "SolarDbg032ca7";
    private static final String SESSION = "032ca7";
    private static final String FILE = "debug-032ca7.log";
    private static final String INGEST =
            "http://127.0.0.1:7386/ingest/e2ddb16b-6e99-4a8a-88a9-bd955259c699";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-032ca7.log";

    /** Flip false after hold timing fix is verified. */
    public static final boolean ENABLED = false; // 2026-07-20 — off: no SD/HTTP metering on device

    private Debug032ca7Log() {}

    /**
     * 2026-07-20 — One NDJSON breadcrumb for hold-latency hypotheses.
     * Layman: writes a timed note so we can see if hold wait or menu paint is slow.
     * Technical: never throws; logcat + SD + host + ingest.
     */
    public static void log(Context ctx, String location, String message, String hypothesisId,
            JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location != null ? location : "");
            o.put("message", message != null ? message : "");
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            o.put("runId", "hold-latency");
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.e(TAG, line);
            append(new File(HOST_PATH), line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
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
        }, "dbg032ca7").start();
    }
}
