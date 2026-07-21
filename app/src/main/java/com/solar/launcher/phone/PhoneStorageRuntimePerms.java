package com.solar.launcher.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;

/**
 * 2026-07-20 — Phone-only legacy READ/WRITE request for API 23–28 shared folders.
 * Y1/Y2/A5 never prompt (chrome inactive). App-private ladder paths skip this.
 * Was: no runtime storage ask — shared SolarPhone mkdir failed silently on M–P.
 * Reversal: noop requestIfNeeded — rely on install-time perms / app-dir fallback only.
 */
public final class PhoneStorageRuntimePerms {

    /** requestCode for MainActivity → PhoneChromeHost. */
    public static final int REQ_STORAGE = 0x5054; // 'PT'

    private PhoneStorageRuntimePerms() {}

    /**
     * 2026-07-20 — True on Marshmallow–Pie when chrome wants a non-app-private path.
     * API 29+ scoped: do not request; ladder falls to app dirs instead.
     */
    public static boolean needsLegacyStoragePerms(Context ctx, File path) {
        if (ctx == null) return false;
        if (!PhoneChromePolicy.active(ctx)) return false;
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT > 28) return false;
        if (path == null) return false;
        if (PhoneStorageAccess.isAppPrivatePath(ctx, path)) return false;
        return !hasLegacyStoragePerms(ctx);
    }

    /**
     * 2026-07-20 — Both READ and WRITE granted (or pre-M install-time grant).
     */
    public static boolean hasLegacyStoragePerms(Context ctx) {
        if (ctx == null) return false;
        if (Build.VERSION.SDK_INT < 23) return true;
        try {
            return ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                    && ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 2026-07-20 — Ask for legacy storage when needed; return true if a dialog was shown.
     * Caller waits for onRequestPermissionsResult before retrying the shared path.
     */
    public static boolean requestIfNeeded(Activity activity, File candidate) {
        if (activity == null) return false;
        if (!needsLegacyStoragePerms(activity, candidate)) return false;
        try {
            activity.requestPermissions(
                    new String[] {
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    REQ_STORAGE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 2026-07-20 — Pure SDK gate for unit tests (no Activity).
     * API 23–28 inclusive; else false.
     */
    public static boolean sdkWantsLegacyRuntimePerms(int sdkInt) {
        return sdkInt >= 23 && sdkInt <= 28;
    }
}
