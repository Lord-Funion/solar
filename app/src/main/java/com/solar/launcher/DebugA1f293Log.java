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
 * 2026-07-20 — Debug session a1f293: empty library / 10min wheel / Y2 no-start.
 * Layman: breadcrumbs for boot, prep, library scan, and wheel gate.
 * Tech: logcat + filesDir NDJSON + HTTP ingest (adb reverse tcp:7386).
 * Reversal: ENABLED=false; delete class + #region agent log sites after verify.
 */
public final class DebugA1f293Log {

    private static final String TAG = "SolarDbgA1f293";
    private static final String SESSION = "a1f293";
    private static final String FILE = "debug-a1f293.log";
    private static final String INGEST =
            "http://127.0.0.1:7386/ingest/e2ddb16b-6e99-4a8a-88a9-bd955259c699";

    public static final boolean ENABLED = false; // 2026-07-20 — off: no SD/HTTP metering on device

    private static volatile String runId = "pre-fix";

    private DebugA1f293Log() {}

    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /** One NDJSON breadcrumb for startup / library / wheel hypotheses. */
    public static void log(Context ctx, String location, String message,
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
            if (data != null) o.put("data", data);
            String line = o.toString();
            Log.i(TAG, line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
                try {
                    File sd = new File(DeviceFeatures.getPrimaryStorageRoot(), ".solar");
                    if (!sd.isDirectory()) sd.mkdirs();
                    append(new File(sd, FILE), line);
                } catch (Exception ignored) {}
            }
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
                    c.setConnectTimeout(120);
                    c.setReadTimeout(120);
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
        }, "dbgA1f293").start();
    }
}
