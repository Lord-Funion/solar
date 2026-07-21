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
 * 2026-07-19 — Debug session 9cd8d5: Y1 wheel repeats / large-library catch-up.
 * Layman: breadcrumbs proving whether dial spam or late ticks walk the list after lift.
 * Tech: logcat + filesDir NDJSON + HTTP ingest (adb reverse tcp:7386). No SD sync.
 * Reversal: ENABLED=false; delete class + #region agent log call sites after verify.
 */
public final class Debug9cd8d5Log {

    private static final String TAG = "SolarDbg9cd8d5";
    private static final String SESSION = "9cd8d5";
    private static final String FILE = "debug-9cd8d5.log";
    private static final String INGEST =
            "http://127.0.0.1:7386/ingest/e2ddb16b-6e99-4a8a-88a9-bd955259c699";

    /** Active for this debug session — disable after verification. */
    public static final boolean ENABLED = false; // 2026-07-20 — off: no SD/HTTP metering on device

    private static volatile String runId = "pre-fix";
    private static volatile long lastEmitMs;
    private static final long MIN_EMIT_GAP_MS = 8L;

    private Debug9cd8d5Log() {}

    /** Tag verification runs after a fix. */
    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — Boot / library milestones (never rate-limited).
     * Layman: timestamps for “how long until menus work”.
     * Tech: same sinks as {@link #log}; use for cold-start perf only.
     */
    public static void boot(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("runId", runId);
            o.put("kind", "boot");
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            postAsync(line);
        } catch (Exception ignored) {}
    }

    /**
     * One NDJSON breadcrumb — rate-limited so a fast spin cannot stall the UI thread.
     * Layman: writes a short note about this wheel tick without slowing the dial.
     */
    public static void log(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        long now = android.os.SystemClock.uptimeMillis();
        // Always allow non-live decisions (drop/stop); throttle live spam.
        boolean important = hypothesisId != null
                && (hypothesisId.indexOf('A') >= 0 || hypothesisId.indexOf('B') >= 0
                || hypothesisId.indexOf('C') >= 0);
        if (!important && now - lastEmitMs < MIN_EMIT_GAP_MS) return;
        lastEmitMs = now;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location);
            o.put("message", message);
            o.put("hypothesisId", hypothesisId);
            o.put("runId", runId);
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
            }
            // Async HTTP — never block wheel path on connect (short timeouts).
            postAsync(line);
        } catch (Exception ignored) {}
    }

    private static void append(File f, String line) throws Exception {
        FileWriter w = new FileWriter(f, true);
        w.write(line);
        w.write('\n');
        w.close();
    }

    private static void postAsync(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(INGEST).openConnection();
                    c.setConnectTimeout(80);
                    c.setReadTimeout(80);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type", "application/json");
                    c.setRequestProperty("X-Debug-Session-Id", SESSION);
                    c.setDoOutput(true);
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
        }, "dbg9cd8d5").start();
    }
}
