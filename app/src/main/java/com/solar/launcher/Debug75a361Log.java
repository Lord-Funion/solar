package com.solar.launcher;

import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;

/**
 * 2026-07-20 — Debug session 75a361: Stem Melody pad missing guitar (Classico).
 * Layman: breadcrumbs for which stem file becomes the Melody pad.
 * Tech: NDJSON → logcat + SD .solar + host .cursor (no HTTP — Y1 audio budget).
 * Pull: adb pull /storage/sdcard0/.solar/debug-75a361.log .cursor/
 * Reversal: delete this class and #region agent log call sites after fix confirmed.
 */
public final class Debug75a361Log {
    private static final String TAG = "SolarDbg75a361";
    private static final String SESSION = "75a361";
    private static final String FILE = "debug-75a361.log";
    private static final String HOST_PATH =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-75a361.log";

    /** On while hunting Melody pad picking residual over guitar. Flip false before ship. */
    public static final boolean ENABLED = false; // 2026-07-20 — off: no SD/HTTP metering on device

    private Debug75a361Log() {}

    /**
     * 2026-07-20 — Append one NDJSON breadcrumb for hypothesis {@code hypothesisId}.
     * Layman: writes what pad file we picked so we can see why guitar vanished.
     * Technical: never throws; sync SD + host only.
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
            o.put("runId", "melody-pad-guitar");
            o.put("data", data != null ? data : new JSONObject());
            String line = o.toString();
            Log.e(TAG, line);
            // During Stem jam: logcat only — SD I/O can starve audio. 2026-07-20
            if (StemOrMixSession.isActive()) return;
            append(new File(HOST_PATH), line);
            append(new File("/storage/sdcard0/.solar", FILE), line);
            append(new File("/storage/sdcard1/.solar", FILE), line);
            append(new File("/sdcard/.solar", FILE), line);
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
}
