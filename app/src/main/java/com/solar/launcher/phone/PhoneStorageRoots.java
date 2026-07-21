package com.solar.launcher.phone;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-20 — Phone chrome maps one user folder to Internal/ + MicroSD/ children.
 * DeviceFeatures reads these only while chrome is active; Y1/Y2/A5 paths stay unchanged.
 * Was: phones used stock /storage mounts. Now: optional override under a picked folder.
 * Reversal: clear PhoneChromePrefs storage root — stock DeviceFeatures roots return.
 */
public final class PhoneStorageRoots {

    public static final String INTERNAL_DIR = "Internal";
    public static final String MICRO_SD_DIR = "MicroSD";

    private PhoneStorageRoots() {}

    /**
     * 2026-07-20 — Ensure Internal/ and MicroSD/ exist under parent; return true if usable.
     * Pure File logic — safe for JVM unit tests.
     */
    public static boolean ensureChildren(File parent) {
        if (parent == null) return false;
        if (!parent.exists() && !parent.mkdirs()) return false;
        if (!parent.isDirectory()) return false;
        File internal = new File(parent, INTERNAL_DIR);
        File micro = new File(parent, MICRO_SD_DIR);
        if (!internal.exists() && !internal.mkdirs()) return false;
        if (!micro.exists() && !micro.mkdirs()) return false;
        return internal.isDirectory() && micro.isDirectory();
    }

    /** Internal child under parent, or null. */
    public static File internalChild(File parent) {
        if (parent == null) return null;
        return new File(parent, INTERNAL_DIR);
    }

    /** MicroSD child under parent, or null. */
    public static File microSdChild(File parent) {
        if (parent == null) return null;
        return new File(parent, MICRO_SD_DIR);
    }

    /**
     * 2026-07-20 — Live MicroSD override when chrome active + folder configured.
     * Returns null to mean “use stock DeviceFeatures path”.
     */
    public static File microSdRootOverride(Context ctx) {
        File parent = configuredParent(ctx);
        if (parent == null) return null;
        if (!ensureChildren(parent)) return null;
        File micro = microSdChild(parent);
        return (micro != null && micro.isDirectory()) ? micro : null;
    }

    /** Live Internal override when chrome active + folder configured. */
    public static File internalRootOverride(Context ctx) {
        File parent = configuredParent(ctx);
        if (parent == null) return null;
        if (!ensureChildren(parent)) return null;
        File internal = internalChild(parent);
        return (internal != null && internal.isDirectory()) ? internal : null;
    }

    /**
     * 2026-07-20 — Parent from prefs via writable ladder (candidate → app dirs).
     * Was: raw mkdirs on stored path only — stuck when shared /sdcard blocked.
     * Reversal: restore naive new File(path) + mkdirs without PhoneStorageAccess.
     */
    public static File configuredParent(Context ctx) {
        if (ctx == null) return null;
        if (!PhoneChromePolicy.active(ctx)) return null;
        String path = PhoneChromePrefs.storageRootPath(ctx);
        if (path == null || path.length() == 0) return null;
        File candidate = new File(path);
        File win = PhoneStorageAccess.resolveWritableParent(ctx, candidate);
        if (win == null) return null;
        // Persist fallback so next scan does not keep retrying a dead shared path.
        if (PhoneStorageAccess.usedFallback(candidate, win)) {
            PhoneChromePrefs.setStorageRootPath(ctx, win.getAbsolutePath());
        }
        return win;
    }

    /**
     * 2026-07-20 — True when chrome is on but the user has not picked a folder yet.
     * Onboarding + customize prompt; library must not fall open to /storage/sdcard0.
     */
    public static boolean needsStoragePrompt(Context ctx) {
        if (ctx == null) return false;
        if (!PhoneChromePolicy.active(ctx)) return false;
        String path = PhoneChromePrefs.storageRootPath(ctx);
        return path == null || path.length() == 0;
    }

    /**
     * 2026-07-20 — When true, DeviceFeatures must not use stock mounts for new media.
     * Y1/Y2/A5 never trip this (chrome inactive). Reversal: always return false.
     */
    public static boolean blocksStockStorageFallOpen(Context ctx) {
        return needsStoragePrompt(ctx);
    }
}
