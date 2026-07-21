package com.solar.launcher.platform;

import android.content.Context;
import android.util.Log;

import com.solar.launcher.RootShell;

/**
 * 2026-07-19 — Apply Koensayr bluetooth pairing conf on-device (APK self-heal).
 * Layman: unlock Just Works headset pairing without waiting for a new ROM flash.
 * Technical: root sed on /system/etc/bluetooth + append ro.bluetooth.* to build.prop.
 * Soft-skips when etc/bluetooth missing. Reboot recommended after apply.
 * Reversal: restore stock conf from base; remove ro.bluetooth.* lines.
 */
public final class BluetoothPairingConfInstaller {

    private static final String TAG = "BtPairingConf";

    public static final class Result {
        public boolean applied;
        public boolean skipped;
        public boolean rebootSuggested;
        public String detail;
    }

    private BluetoothPairingConfInstaller() {}

    /** Idempotent apply — call with root available. */
    public static Result apply(Context ctx) {
        Result out = new Result();
        if (ctx == null || !RootShell.canRun()) {
            out.skipped = true;
            out.detail = "no root";
            return out;
        }
        // Soft-skip when stock tree has no bluetooth conf dir.
        // 2026-07-19 — runCapture for stdout; run() is boolean exit-only (compile fix).
        String probe = RootShell.runCapture(
                "test -d /system/etc/bluetooth && echo yes || echo no");
        if (probe == null || !probe.contains("yes")) {
            out.skipped = true;
            out.detail = "no /system/etc/bluetooth";
            Log.i(TAG, out.detail);
            return out;
        }

        RootShell.run("mount -o remount,rw /system 2>/dev/null || true");

        // audio.conf — Enable + Master (create keys if missing via sed fallbacks).
        RootShell.run(
                "if [ -f /system/etc/bluetooth/audio.conf ]; then "
                        + "grep -q '^Enable=' /system/etc/bluetooth/audio.conf "
                        + "&& sed -i 's/^Enable=.*/Enable=Source,Control,Target/' "
                        + "/system/etc/bluetooth/audio.conf "
                        + "|| echo 'Enable=Source,Control,Target' >> /system/etc/bluetooth/audio.conf; "
                        + "grep -q '^Master=' /system/etc/bluetooth/audio.conf "
                        + "&& sed -i 's/^Master=.*/Master=true/' /system/etc/bluetooth/audio.conf "
                        + "|| echo 'Master=true' >> /system/etc/bluetooth/audio.conf; "
                        + "fi");

        RootShell.run(
                "if [ -f /system/etc/bluetooth/auto_pairing.conf ]; then "
                        + "sed -i 's/^AddressBlacklist=.*/AddressBlacklist=/' "
                        + "/system/etc/bluetooth/auto_pairing.conf; "
                        + "sed -i 's/^ExactNameBlacklist=.*/ExactNameBlacklist=/' "
                        + "/system/etc/bluetooth/auto_pairing.conf; "
                        + "sed -i 's/^PartialNameBlacklist=.*/PartialNameBlacklist=/' "
                        + "/system/etc/bluetooth/auto_pairing.conf; "
                        + "fi");

        RootShell.run(
                "if [ -f /system/etc/bluetooth/blacklist.conf ]; then "
                        + "sed -i '/^scoSocket/d' /system/etc/bluetooth/blacklist.conf; "
                        + "fi");

        // Never set persist.bluetooth.avrcpversion (mtkbt cannot honor it).
        appendPropIfAbsent("ro.bluetooth.class", "10486812");
        appendPropIfAbsent("ro.bluetooth.profiles.a2dp.source.enabled", "true");
        appendPropIfAbsent("ro.bluetooth.profiles.avrcp.target.enabled", "true");

        out.applied = true;
        out.rebootSuggested = true;
        out.detail = "bluetooth pairing conf applied";
        Log.i(TAG, out.detail);
        return out;
    }

    private static void appendPropIfAbsent(String key, String value) {
        RootShell.run(
                "grep -q '^" + key + "=' /system/build.prop 2>/dev/null "
                        + "|| echo '" + key + "=" + value + "' >> /system/build.prop");
    }
}
