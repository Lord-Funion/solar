package com.solar.launcher.phone;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-20 — Phone-only writable parent ladder for Internal/ + MicroSD/ children.
 * Candidate shared folder → app external files → filesDir; keeps java.io.File I/O alive.
 * Was: naive Environment.getExternalStorageDirectory mkdirs (fails on API 17 emu / scoped).
 * Reversal: delete this class; callers mkdir candidate only — onboarding may stick again.
 */
public final class PhoneStorageAccess {

    /** Folder name under external / filesDir when seeding defaults. */
    public static final String APP_FOLDER = "SolarPhone";

    /** Calm toast when we could not use the user-picked / shared path. */
    public static final String FALLBACK_TOAST =
            "Using app storage on this Android version";

    private PhoneStorageAccess() {}

    /**
     * 2026-07-20 — True when parent can host Internal/ and MicroSD/ (mkdir + dir check).
     * Pure File probe — safe for JVM unit tests without Context.
     */
    public static boolean isWritableParent(File candidate) {
        return PhoneStorageRoots.ensureChildren(candidate);
    }

    /**
     * 2026-07-20 — App-specific external SolarPhone dir, or null if OS has none.
     * Prefer this over shared /sdcard when scoped storage blocks mkdirs.
     */
    public static File externalAppParent(Context ctx) {
        if (ctx == null) return null;
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext == null) return null;
            return new File(ext, APP_FOLDER);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 2026-07-20 — Always-available private filesDir/SolarPhone fallback.
     * Last rung of the ladder — onboarding must still complete here.
     */
    public static File privateAppParent(Context ctx) {
        if (ctx == null) return null;
        return new File(ctx.getFilesDir(), APP_FOLDER);
    }

    /**
     * 2026-07-20 — Pick first writable parent: candidate → externalFiles → filesDir.
     * Does not touch prefs — caller persists the winner.
     */
    public static File resolveWritableParent(Context ctx, File candidate) {
        return resolveWritableParent(
                candidate,
                externalAppParent(ctx),
                privateAppParent(ctx));
    }

    /**
     * 2026-07-20 — Pure ladder for JVM tests (temp dirs, no Android Context).
     * Same order as the live Context overload.
     */
    public static File resolveWritableParent(File candidate, File externalApp, File privateApp) {
        if (candidate != null && isWritableParent(candidate)) {
            return candidate;
        }
        if (externalApp != null && isWritableParent(externalApp)) {
            return externalApp;
        }
        if (privateApp != null && isWritableParent(privateApp)) {
            return privateApp;
        }
        return null;
    }

    /**
     * 2026-07-20 — Resolve ladder and save winning absolute path into PhoneChromePrefs.
     * Returns winner or null; ensureChildren already ran inside the probe.
     */
    public static File resolveAndPersist(Context ctx, File candidate) {
        File win = resolveWritableParent(ctx, candidate);
        if (win == null || ctx == null) return win;
        PhoneChromePrefs.setStorageRootPath(ctx, win.getAbsolutePath());
        return win;
    }

    /**
     * 2026-07-20 — True when winner is not the same path as the requested candidate.
     * Used for calm “app storage” toast after a failed shared-folder mkdir.
     */
    public static boolean usedFallback(File candidate, File winner) {
        if (winner == null) return true;
        if (candidate == null) return true;
        try {
            return !candidate.getAbsolutePath().equals(winner.getAbsolutePath());
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 2026-07-20 — True when path lives under app-private dirs (no legacy storage perm).
     * Shared /sdcard picks need READ/WRITE on API 23–28; app dirs do not.
     */
    public static boolean isAppPrivatePath(Context ctx, File path) {
        if (ctx == null || path == null) return false;
        String abs;
        try {
            abs = path.getAbsolutePath();
        } catch (Throwable t) {
            return false;
        }
        if (abs == null || abs.length() == 0) return false;
        try {
            File files = ctx.getFilesDir();
            if (files != null) {
                String p = files.getAbsolutePath();
                if (p != null && (abs.equals(p) || abs.startsWith(p + File.separator))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            File ext = ctx.getExternalFilesDir(null);
            if (ext != null) {
                String p = ext.getAbsolutePath();
                if (p != null && (abs.equals(p) || abs.startsWith(p + File.separator))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
