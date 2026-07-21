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
 * 2026-07-20 — Debug session 6eff1e: blank All Songs titles + can't focus Artists/Albums/…
 * Layman: write timed notes while you open Music shelves so we can see what broke.
 * Technical: NDJSON → logcat + filesDir + SD + HTTP ingest (session 6eff1e).
 * Pull: adb logcat -d -s SolarDbg6eff1e:I ; or .cursor/debug-6eff1e.log via ingest/adb pull.
 * Reversal: ENABLED=false; delete class + #region agent log call sites after verify.
 */
public final class Debug6eff1eLog {

    private static final String TAG = "SolarDbg6eff1e";
    private static final String SESSION = "6eff1e";
    private static final String FILE = "debug-6eff1e.log";
    private static final String INGEST =
            "http://127.0.0.1:7652/ingest/a52e4428-848e-4c3a-b047-de416047f443";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-6eff1e.log";

    /**
     * 2026-07-21 — OFF after dual-line Approach A. Was true during 6eff1e blank-title hunt.
     * Reversal: true while re-checking segmented blank shelves.
     */
    public static final boolean ENABLED = false;

    private static volatile String runId = "pre-fix";
    private static long lastBlankLogMs;
    private static long lastFocusLogMs;

    private Debug6eff1eLog() {}

    /** 2026-07-20 — Tag this reproduce pass (pre-fix / post-fix). */
    public static void setRunId(String id) {
        if (id != null && id.length() > 0) runId = id;
    }

    /**
     * 2026-07-20 — One NDJSON breadcrumb for library blank/focus hypotheses.
     * Layman: write a timed note about what the Music menus are doing.
     */
    public static void log(Context ctx, String location, String message,
            String hypothesisId, JSONObject data) {
        if (!ENABLED) return;
        try {
            JSONObject o = new JSONObject();
            o.put("sessionId", SESSION);
            o.put("timestamp", System.currentTimeMillis());
            o.put("location", location != null ? location : "");
            o.put("message", message != null ? message : "");
            o.put("hypothesisId", hypothesisId != null ? hypothesisId : "");
            o.put("runId", runId);
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.i(TAG, line);
            append(new File(HOST_PATH), line);
            if (ctx != null) {
                try {
                    append(new File(ctx.getFilesDir(), FILE), line);
                } catch (Exception ignored) {}
                try {
                    File sdRoot = DeviceFeatures.getPrimaryStorageRoot();
                    if (sdRoot != null) {
                        File sd = new File(sdRoot, ".solar");
                        if (!sd.isDirectory()) sd.mkdirs();
                        append(new File(sd, FILE), line);
                    }
                } catch (Exception ignored) {}
            }
            postAsync(line);
        } catch (Exception ignored) {}
    }

    /**
     * 2026-07-20 — Rate-limit blank song-row notes (~4/sec) so wheel spin does not flood.
     * Layman: only jot empty titles a few times a second while the list paints.
     */
    public static boolean allowBlankSample() {
        long now = System.currentTimeMillis();
        if (now - lastBlankLogMs < 250L) return false;
        lastBlankLogMs = now;
        return true;
    }

    /**
     * 2026-07-20 — Rate-limit Music hub wheel focus notes (~8/sec).
     * Layman: don’t spam every dial click; keep a usable trail.
     */
    public static boolean allowFocusSample() {
        long now = System.currentTimeMillis();
        if (now - lastFocusLogMs < 120L) return false;
        lastFocusLogMs = now;
        return true;
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

    private static void postAsync(final String line) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection c = null;
                try {
                    URL url = new URL(INGEST);
                    c = (HttpURLConnection) url.openConnection();
                    c.setRequestMethod("POST");
                    c.setConnectTimeout(400);
                    c.setReadTimeout(400);
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
        }, "dbg6eff1e").start();
    }
}
