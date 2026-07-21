package com.solar.launcher.platform;

import android.content.Context;
import android.util.Log;

import com.solar.launcher.DeviceFeatures;
import com.solar.launcher.RootShell;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * 2026-07-19 — Install AirPods RTP libbluetoothdrv proxy (Y1 3.0.7 MD5 gate).
 * Layman: fix silent AirPods that pair but play no sound.
 * Technical: stock → libbluetoothdrv_real.so; asset proxy → libbluetoothdrv.so.
 * Soft-skips on Y2/A5 or unknown MD5. Reboot required.
 * Reversal: restore _real over libbluetoothdrv.so.
 */
public final class AirpodsRtpProxyInstaller {

    private static final String TAG = "AirpodsRtp";
    /** Official Innioasis Y1 firmware 3.0.7 stock driver. */
    static final String STOCK_MD5_Y1_307 = "32f1af87e46acaf1efa3f083340495cb";
    private static final String ASSET_PROXY = "platform/bluetooth/libbluetoothdrv.so";
    private static final String PATH_PROXY = "/system/lib/libbluetoothdrv.so";
    private static final String PATH_REAL = "/system/lib/libbluetoothdrv_real.so";

    public static final class Result {
        public boolean applied;
        public boolean skipped;
        public boolean rebootRequired;
        public String detail;
    }

    private AirpodsRtpProxyInstaller() {}

    /** Apply when Y1 + root + matching stock MD5 + bundled proxy asset. */
    public static Result apply(Context ctx) {
        Result out = new Result();
        if (ctx == null || !RootShell.canRun()) {
            out.skipped = true;
            out.detail = "no root";
            return out;
        }
        if (DeviceFeatures.isY2() || DeviceFeatures.isA5()) {
            out.skipped = true;
            out.detail = "Y1-only RTP proxy";
            return out;
        }
        if (!PlatformProbe.fileExists(PATH_PROXY)) {
            out.skipped = true;
            out.detail = "no libbluetoothdrv.so";
            return out;
        }
        // Already proxied.
        if (PlatformProbe.fileExists(PATH_REAL)) {
            out.skipped = true;
            out.detail = "already installed";
            return out;
        }

        String md5 = md5OfSystemFile(PATH_PROXY);
        if (md5 == null || !STOCK_MD5_Y1_307.equalsIgnoreCase(md5)) {
            out.skipped = true;
            out.detail = "MD5 mismatch (" + md5 + ")";
            Log.i(TAG, out.detail);
            return out;
        }

        File extracted = PlatformAssetExtractor.extractAsset(ctx, ASSET_PROXY);
        if (extracted == null || !extracted.isFile()) {
            out.skipped = true;
            out.detail = "proxy asset missing";
            return out;
        }

        RootShell.run("mount -o remount,rw /system 2>/dev/null || true");
        RootShell.run("cp -a " + PlatformProbe.shellQuote(PATH_PROXY) + " "
                + PlatformProbe.shellQuote(PATH_REAL));
        RootShell.run("cp " + PlatformProbe.shellQuote(extracted.getAbsolutePath()) + " "
                + PlatformProbe.shellQuote(PATH_PROXY)
                + " && chmod 644 " + PlatformProbe.shellQuote(PATH_PROXY) + " "
                + PlatformProbe.shellQuote(PATH_REAL));

        out.applied = true;
        out.rebootRequired = true;
        out.detail = "AirPods RTP proxy installed";
        Log.i(TAG, out.detail);
        return out;
    }

    private static String md5OfSystemFile(String path) {
        // Prefer root md5sum for /system readability on locked mounts.
        String out = RootShell.runCapture("md5sum " + PlatformProbe.shellQuote(path) + " 2>/dev/null");
        if (out != null) {
            String[] parts = out.trim().split("\\s+");
            if (parts.length > 0 && parts[0].length() == 32) return parts[0];
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream in = new FileInputStream(path);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            in.close();
            byte[] dig = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
