package com.solar.launcher.xposed.bridge;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * 2026-07-19 — Stock SystemUI owns {@code UsbStorageActivity}; Solar must not finish it.
 * Layman: Android’s USB storage screen stays on screen when you plug into a PC.
 * Was: finish + Solar concierge (broke stock UI when app preferred stock). Reversal: restore
 * finishAndRoute hooks from git history before this comment.
 */
final class UsbStorageHooks {

    private UsbStorageHooks() {}

    /**
     * Intentionally installs nothing in {@code com.android.systemui}.
     * Auto-Connect / Solar UMS still use USB_STATE + Settings paths — not this activity.
     */
    static void installSystemUi(LoadPackageParam lpparam) {
        SolarContextBridge.log("UsbStorageActivity hooks off — stock SystemUI USB UI");
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("hooksInstalled", false);
            Bridge050a40DebugLog.log("UsbStorageHooks.installSystemUi",
                    "hooks disabled for stock USB dialog", "H1", d);
        } catch (Throwable ignored) {}
        // #endregion
    }
}
