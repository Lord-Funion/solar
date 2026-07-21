package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Start Solar overlay service directly — more reliable than broadcast from system_server.
 */
public final class SolarOverlayClient {

    public static final String PERMISSION = "com.solar.launcher.permission.OVERLAY_TRIGGER";
    public static final String ACTION_SHOW_OVERLAY_POWER =
            "com.solar.launcher.action.SHOW_OVERLAY_POWER";
    public static final String ACTION_SHOW_OVERLAY_APP_MENU =
            "com.solar.launcher.action.SHOW_OVERLAY_APP_MENU";
    public static final String ACTION_SHOW_OVERLAY_VOLUME =
            "com.solar.launcher.action.SHOW_OVERLAY_VOLUME";
    /** Passive toast hint — no key gate; matches {@link com.solar.launcher.OverlayTriggers}. */
    public static final String ACTION_SHOW_OVERLAY_TOAST =
            "com.solar.launcher.action.SHOW_OVERLAY_TOAST";
    public static final String EXTRA_TOAST_TEXT = "toast_text";
    public static final String EXTRA_TOAST_DURATION_MS = "toast_duration_ms";
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_PICKER =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_PICKER";
    public static final String ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY =
            "com.solar.launcher.action.SHOW_OVERLAY_LAUNCHER_RECOVERY";
    public static final String EXTRA_RECOVERY_PROCESS = "recovery_process";
    public static final String ACTION_SHOW_OVERLAY_NATIVE_DIALOG =
            "com.solar.launcher.action.SHOW_OVERLAY_NATIVE_DIALOG";
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE";
    /** 2026-07-08 — UMS exported lock — companion shell, not MainActivity. */
    public static final String ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK =
            "com.solar.launcher.action.SHOW_OVERLAY_USB_STORAGE_LOCK";
    public static final String ACTION_SHOW_OVERLAY_BT_PAIRING =
            "com.solar.launcher.action.SHOW_OVERLAY_BT_PAIRING";
    public static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";
    public static final String EXTRA_BT_PAIRING_MODE = "bt_pairing_mode";
    public static final String EXTRA_BT_PAIRING_ADDRESS = "bt_pairing_address";
    public static final String EXTRA_BT_PAIRING_NAME = "bt_pairing_name";
    public static final String EXTRA_BT_PAIRING_PASSKEY = "bt_pairing_passkey";
    public static final String EXTRA_BT_PAIRING_PIN_PREFILL = "bt_pairing_pin_prefill";
    public static final String EXTRA_USB_STORAGE_LOCK = "usb_storage_lock";
    /** 2026-07-06 — Xposed concierge marker for Java fallback tiers. */
    public static final String SYSPROP_USB_CONCIERGE = "sys.solar.usb.concierge";
    public static final String SYSPROP_USB_CONCIERGE_AT = "sys.solar.usb.concierge_at";
    /** Must match app {@link com.solar.launcher.OverlayTierScheduler} — ANR/crash blocks USB spawn. */
    private static final String SYSPROP_OVERLAY_TIER = "sys.solar.overlay.tier";
    private static final String SYSPROP_PENDING_USB = "sys.solar.overlay.pending_usb";
    private static final String TIER_NATIVE_ERROR = "native_error";
    /** MainActivity USB handoff extras — must match app constants. */
    private static final String EXTRA_USB_OVERLAY_ENABLE = "solar.extra.usb_overlay_enable";
    private static final String EXTRA_USB_OVERLAY_LOCK = "solar.extra.usb_overlay_lock";
    public static final String EXTRA_MENU_TITLES = "menu_titles";
    public static final String EXTRA_MENU_TITLE = "menu_title";
    public static final String EXTRA_MENU_SESSION_ID = "menu_session_id";
    public static final String EXTRA_MENU_CALLER_PACKAGE = "menu_caller_package";
    public static final String EXTRA_MENU_HAS_SUBMENU = "menu_has_submenu";
    public static final String EXTRA_DIALOG_MESSAGE = "dialog_message";
    public static final String EXTRA_DIALOG_BUTTONS = "dialog_buttons";

    private static final String SOLAR_PKG = "com.solar.launcher";
    /** 2026-07-14 — Solar ThemedContextMenu is the one overlay shell; companion Chip is opt-in. */
    private static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    private static final String COMPANION_OVERLAY = COMPANION_PKG + ".GlobalContextOverlayService";
    private static final String OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";
    /** Rollback with companion_shell=1: set legacy_shell=1 to force Solar again. */
    private static final String LEGACY_SHELL_PROP = "persist.solar.overlay.legacy_shell";
    private static final String OPEN_RECEIVER = SOLAR_PKG + ".PowerOverlayOpenReceiver";
    private static final String TRIGGER_RECEIVER = SOLAR_PKG + ".OverlayTriggerReceiver";
    private static final String APP_MENU_RECEIVER = SOLAR_PKG + ".AppMenuOverlayReceiver";
    private static final String NATIVE_DIALOG_RECEIVER = SOLAR_PKG + ".NativeDialogOverlayReceiver";
    private static final String USB_STORAGE_RECEIVER = SOLAR_PKG + ".UsbStorageOverlayReceiver";
    private static final String MAIN_ACTIVITY = SOLAR_PKG + ".MainActivity";
    private static final String BT_PAIRING_RECEIVER = SOLAR_PKG + ".BluetoothPairingOverlayReceiver";
    private static final String LAUNCHER_PICKER_RECEIVER = SOLAR_PKG + ".LauncherPickerOverlayReceiver";
    private static final String LAUNCHER_RECOVERY_RECEIVER = SOLAR_PKG + ".LauncherRecoveryOverlayReceiver";

    private SolarOverlayClient() {}

    /**
     * 2026-07-14 — Sole shell = Solar ThemedContextMenu unless companion_shell=1 opt-in.
     * Layman: one themed system menu that matches Home; chip companion only if forced.
     * Was: companion Chip primary (default legacy_shell=0). Reversal: companion_shell=1.
     */
    private static boolean useCompanionShell(Context ctx) {
        // Explicit chip opt-in.
        if ("1".equals(readSysProp("persist.solar.overlay.companion_shell", "0"))) {
            if ("1".equals(readSysProp(LEGACY_SHELL_PROP, "0"))) return false;
            return isCompanionInstalled(ctx);
        }
        return false;
    }

    private static String shellPkg(Context ctx) {
        return useCompanionShell(ctx) ? COMPANION_PKG : SOLAR_PKG;
    }

    private static String shellService(Context ctx) {
        return useCompanionShell(ctx) ? COMPANION_OVERLAY : OVERLAY_SERVICE;
    }

    /** True when Solar or companion can paint global overlays — fail-open to stock when false. */
    public static boolean canDeliverOverlay(Context ctx) {
        return isSolarInstalled(ctx) || isCompanionInstalled(ctx);
    }

    private static boolean isSolarInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getApplicationContext().getPackageManager().getApplicationInfo(SOLAR_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Phase 2a — companion APK removes Solar SPOF for power/rescue tiers. */
    public static boolean isCompanionInstalled(Context ctx) {
        if (ctx == null) return false;
        try {
            ctx.getApplicationContext().getPackageManager().getApplicationInfo(COMPANION_PKG, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static final String ACTION_OPEN_CONTEXT_MENU =
            "com.solar.launcher.action.OPEN_CONTEXT_MENU";
    public static final String EXTRA_CONTEXT_POWER_HOLD = "context_power_hold";
    /** 2026-07-20 — Mirror OverlayTriggers: Power DOWN → MainActivity arm Options spinner. */
    public static final String ACTION_CONTEXT_HOLD_ARM =
            "com.solar.launcher.action.CONTEXT_HOLD_ARM";
    /** 2026-07-20 — Mirror OverlayTriggers: Power UP before menu → clear Options spinner. */
    public static final String ACTION_CONTEXT_HOLD_CANCEL =
            "com.solar.launcher.action.CONTEXT_HOLD_CANCEL";

    /**
     * 2026-07-20 — Tell Solar Home to show status spinner during Power-hold wait.
     * Layman: same little spinner as hold-Back, right when you press Power for Options.
     * Was: spinner only after hold fired (OPEN_CONTEXT_MENU). Reversal: delete callers.
     */
    public static void armInAppContextHoldThrobber(Context ctx) {
        sendContextHoldThrobberBroadcast(ctx, ACTION_CONTEXT_HOLD_ARM);
    }

    /**
     * 2026-07-20 — Clear Options hold spinner when Power was released before the menu opened.
     * Layman: let go early → spinner stops, like cancelling a Back-hold.
     */
    public static void cancelInAppContextHoldThrobber(Context ctx) {
        sendContextHoldThrobberBroadcast(ctx, ACTION_CONTEXT_HOLD_CANCEL);
    }

    /** Package-scoped broadcast so only Solar MainActivity refreshes the status spinner. */
    private static void sendContextHoldThrobberBroadcast(Context ctx, String action) {
        if (ctx == null || action == null) return;
        try {
            Intent i = new Intent(action);
            i.setPackage(SOLAR_PKG);
            ctx.sendBroadcast(i);
            SolarContextBridge.log("contextHoldThrobber " + action);
        } catch (Throwable t) {
            SolarContextBridge.log("contextHoldThrobber failed: "
                    + t.getClass().getSimpleName());
        }
    }

    /**
     * 2026-07-14 — HOLD BACK outside Solar jumps to Solar MainActivity (no global quick menu).
     * Layman: hold Back in any Android app to come home to Solar.
     * Was: showPowerOverlay WM shell with a Home chip. Reversal: call showPowerOverlay(ctx).
     */
    public static boolean launchSolarHome(Context ctx) {
        if (ctx == null) return false;
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setComponent(new ComponentName(SOLAR_PKG, MAIN_ACTIVITY));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            ctx.startActivity(i);
            SolarContextBridge.log("launchSolarHome → MainActivity");
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("via", "startActivity");
                PowerMenuDebugLog.event("SolarOverlayClient.launchSolarHome",
                        "Solar Home launched", "c54726-H3", d);
            } catch (Throwable ignored) {}
            // #endregion
            return true;
        } catch (Throwable t) {
            try {
                Runtime.getRuntime().exec(new String[]{
                        "sh", "-c",
                        "am start -n " + SOLAR_PKG + "/" + MAIN_ACTIVITY
                                + " -f 0x34000000 2>/dev/null"
                });
                SolarContextBridge.log("launchSolarHome shell fallback");
                return true;
            } catch (Throwable t2) {
                SolarContextBridge.log("launchSolarHome failed: "
                        + t2.getClass().getSimpleName());
                return false;
            }
        }
    }

    /**
     * 2026-07-14 — Solar Home POWER/BACK-hold opens in-app ThemedContextMenu (focused-row options).
     * Layman: holding Back/Power inside Solar shows Home's own menu — not the system companion.
     * Was: showPowerOverlay (companion) — dead wheel / hard to dismiss over MainActivity.
     * Reversal: call showPowerOverlay(ctx) again.
     */
    public static void showInAppPowerMenu(Context ctx) {
        if (ctx == null) return;
        // Never stack Home's sheet over a floating Chip/Solar WM menu.
        SystemServerHooks.dismissAnyOverlay(ctx);
        try {
            Intent i = new Intent(ACTION_OPEN_CONTEXT_MENU);
            i.setClassName(SOLAR_PKG, MAIN_ACTIVITY);
            i.putExtra(EXTRA_CONTEXT_POWER_HOLD, true);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            ctx.startActivity(i);
            SolarContextBridge.log("showInAppPowerMenu → MainActivity OPEN_CONTEXT_MENU");
        } catch (Throwable t) {
            SolarContextBridge.log("showInAppPowerMenu failed — companion fallback");
            showPowerOverlay(ctx);
        }
    }

    /** Y1 BACK-long / Y2 power-hold or BACK-long — startService first (works when app is stopped). */
    public static void showPowerOverlay(Context ctx) {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("ctxPkg", ctx != null ? ctx.getPackageName() : "");
            PowerMenuDebugLog.event("SolarOverlayClient.showPowerOverlay", "enter", "H4", d);
        } catch (Throwable ignored) {}
        // #endregion
        com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
        // Drop the other shell first so Chip + Solar never share the screen.
        dismissPeerOverlayShell(ctx);
        if (OverlayKeyForwarder.isOverlayActiveOrOpening()) {
            SolarContextBridge.log("showPowerOverlay skipped — already active/opening");
            return;
        }
        setOverlayOpeningEarly(true);
        warmOverlayProcess(ctx);
        boolean svcOk = startPowerOverlayService(ctx);
        // Belt-and-suspenders — cold :overlay over Rockbox HOME may miss startService from system_server.
        if (!svcOk) {
            deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_POWER, OPEN_RECEIVER);
            startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_POWER);
        }
        if (svcOk) {
            // #region agent log
            PowerMenuDebugLog.event("SolarOverlayClient.showPowerOverlay", "startService ok", "H4", null);
            // #endregion
        } else {
            // #region agent log
            PowerMenuDebugLog.event("SolarOverlayClient.showPowerOverlay", "broadcast fallback", "H4", null);
            // #endregion
        }
    }

    /**
     * 2026-07-14 — DISMISS the package that is NOT about to paint.
     * Layman: close the spare menu before opening the real one.
     */
    private static void dismissPeerOverlayShell(Context ctx) {
        if (ctx == null) return;
        try {
            Intent dismiss = new Intent(ACTION_DISMISS_OVERLAY);
            if (useCompanionShell(ctx)) {
                dismiss.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
            } else {
                dismiss.setComponent(new ComponentName(COMPANION_PKG, COMPANION_OVERLAY));
            }
            ctx.startService(dismiss);
        } catch (Throwable ignored) {}
    }

    /**
     * 2026-07-08 — Warm the one shell (companion primary).
     * Was: always warm Solar :overlay + companion secondary.
     */
    public static void warmOverlayProcess(Context ctx) {
        if (ctx == null) return;
        Intent keep = new Intent("com.solar.launcher.action.OVERLAY_KEEPALIVE");
        keep.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        try {
            ctx.startService(keep);
        } catch (Throwable ignored) {}
    }

    /**
     * 2026-07-14 — Open power tier on Solar ThemedContextMenu (Chip only if companion_shell=1).
     * Was: companion Chip primary. Reversal: companion_shell=1.
     */
    private static boolean startPowerOverlayService(Context ctx) {
        if (ctx == null) return false;
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_POWER);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        try {
            ctx.startService(svc);
            SolarContextBridge.log("shell startService " + ACTION_SHOW_OVERLAY_POWER
                    + " pkg=" + shellPkg(ctx));
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("shell startService failed: " + t.getClass().getSimpleName());
        }
        // Fail-open Solar only when Chip primary failed — never paint Solar+Chip together.
        // Was: also start companion when Solar primary failed → dual chrome. Reversal: restore that branch.
        if (useCompanionShell(ctx) && isSolarInstalled(ctx)) {
            return startOverlayService(ctx, ACTION_SHOW_OVERLAY_POWER);
        }
        return false;
    }

    public static void showVolumeOverlay(Context ctx) {
        // Passive volume HUD must not set overlay-active — that swallows HW keys in PWM without
        // a handler to apply MediaVolumeControl (keys consumed, level never changes).
        if (startOverlayService(ctx, ACTION_SHOW_OVERLAY_VOLUME)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_VOLUME, OPEN_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_VOLUME);
    }

    /**
     * 2026-07-07 — Replace stock Toast with passive themed hint overlay.
     * Layman: brief message bar like volume HUD — no buttons, no key capture.
     * Technical: fail-open false → ToastHooks leaves stock Toast visible.
     */
    public static boolean showToastOverlay(Context ctx, String text, long durationMs) {
        if (ctx == null || text == null || text.length() == 0) return false;
        if (!canDeliverOverlay(ctx)) return false;
        // Skip when native ANR/crash tier owns interactive overlay — never drop user feedback.
        if (shouldDeferUsbForNativeErrorTier()) {
            SolarContextBridge.log("toast deferred — native_error tier");
            return false;
        }
        if (startToastOverlayService(ctx, text, durationMs)) return true;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_TOAST);
        intent.setComponent(new ComponentName(SOLAR_PKG, TRIGGER_RECEIVER));
        intent.putExtra(EXTRA_TOAST_TEXT, text);
        intent.putExtra(EXTRA_TOAST_DURATION_MS, durationMs);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("toast broadcast len=" + text.length());
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("toast broadcast failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean startToastOverlayService(Context ctx, String text, long durationMs) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_TOAST);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        svc.putExtra(EXTRA_TOAST_TEXT, text);
        svc.putExtra(EXTRA_TOAST_DURATION_MS, durationMs);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("toast startService len=" + text.length());
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("toast startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** HOME resolver replacement — wheel-friendly picker; global modal uses {@link #showPowerOverlay}. */
    public static void showLauncherPickerOverlay(Context ctx) {
        if (ctx == null) return;
        warmOverlayProcess(ctx);
        if (startOverlayService(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER)) return;
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER, LAUNCHER_PICKER_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_PICKER);
        startHelperLauncherPicker(ctx);
    }

    /**
     * 2026-07-07 — Crash-loop recovery — wheel picker with fallback HOME + keep-Solar row.
     * Layman: after 3 crashes in 2 min, offer Rockbox/JJ without scary stock dialog.
     */
    public static void showLauncherRecoveryOverlay(Context ctx, String processName) {
        if (ctx == null) return;
        warmOverlayProcess(ctx);
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        if (processName != null) {
            svc.putExtra(EXTRA_RECOVERY_PROCESS, processName);
        }
        try {
            ctx.startService(svc);
            SolarContextBridge.log("launcherRecovery startService proc="
                    + (processName != null ? processName : "?"));
            return;
        } catch (Throwable t) {
            SolarContextBridge.log("launcherRecovery startService failed: "
                    + t.getClass().getSimpleName());
        }
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY, LAUNCHER_RECOVERY_RECEIVER);
        startOverlayFallback(ctx, ACTION_SHOW_OVERLAY_LAUNCHER_RECOVERY);
    }

    private static final String HELPER_PKG = "com.solar.launcher.homehelper";
    private static final String ACTION_HELPER_SHOW_LAUNCHER_PICKER =
            "com.solar.launcher.homehelper.action.SHOW_LAUNCHER_PICKER";

    /** Last resort when Solar overlay service and broadcast miss — helper wheel UI. */
    private static void startHelperLauncherPicker(Context ctx) {
        if (ctx == null) return;
        try {
            Intent intent = new Intent(ACTION_HELPER_SHOW_LAUNCHER_PICKER);
            intent.setComponent(new ComponentName(HELPER_PKG,
                    HELPER_PKG + ".HelperShowLauncherPickerReceiver"));
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("helper launcher picker broadcast");
        } catch (Throwable t) {
            SolarContextBridge.log("helper picker failed: " + t.getClass().getSimpleName());
        }
    }

    /** Replace stock text context menu — startService with extras (broadcast can lag on JB). */
    public static boolean showAppMenu(Context ctx, String title, String[] itemTitles,
            String sessionId, String callerPackage) {
        return showAppMenu(ctx, title, itemTitles, null, sessionId, callerPackage);
    }

    public static boolean showAppMenu(Context ctx, String title, String[] itemTitles,
            boolean[] hasSubmenu, String sessionId, String callerPackage) {
        if (ctx == null || itemTitles == null || itemTitles.length == 0) return false;
        if (sessionId == null || sessionId.length() == 0) return false;
        if (!canDeliverOverlay(ctx)) return false;
        if (startAppMenuService(ctx, title, itemTitles, hasSubmenu, sessionId, callerPackage)) return true;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_APP_MENU);
        intent.setComponent(new ComponentName(SOLAR_PKG, APP_MENU_RECEIVER));
        intent.putExtra(EXTRA_MENU_TITLES, itemTitles);
        intent.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (hasSubmenu != null) intent.putExtra(EXTRA_MENU_HAS_SUBMENU, hasSubmenu);
        if (callerPackage != null) intent.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) intent.putExtra(EXTRA_MENU_TITLE, title);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("appMenu broadcast items=" + itemTitles.length);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("appMenu broadcast failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 2026-07-08 — App-menu start on companion shell (Solar Home + third-party menus).
     * Was: always SolarOverlayService.
     */
    private static boolean startAppMenuService(Context ctx, String title, String[] itemTitles,
            boolean[] hasSubmenu, String sessionId, String callerPackage) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_APP_MENU);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        svc.putExtra(EXTRA_MENU_TITLES, itemTitles);
        svc.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (hasSubmenu != null) svc.putExtra(EXTRA_MENU_HAS_SUBMENU, hasSubmenu);
        if (callerPackage != null) svc.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) svc.putExtra(EXTRA_MENU_TITLE, title);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("appMenu startService items=" + itemTitles.length);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("appMenu startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** Replace stock AlertDialog — scrollable detail body + wheel-friendly OK/Cancel rows. */
    public static boolean showNativeDialog(Context ctx, String title, String message, String[] buttons,
            String sessionId, String callerPackage) {
        if (ctx == null || sessionId == null || sessionId.length() == 0) return false;
        if (buttons == null || buttons.length == 0) return false;
        if (!canDeliverOverlay(ctx)) return false;
        // 2026-07-06 — Reserve native tier before startService — USB concierge defers during paint race.
        reserveNativeErrorTierEarly();
        // 2026-07-06 — Warm :overlay before AMS IPC; ANR/crash must paint fast on MT6572.
        warmOverlayProcess(ctx);
        setOverlayOpeningEarly(true);
        if (startNativeDialogService(ctx, title, message, buttons, sessionId, callerPackage)) {
            return true;
        }
        // 2026-07-07 — Fail-open: never skip stock Holo when :overlay startService misses (ANR dead zone).
        SolarContextBridge.log("nativeDialog fail-open — stock Holo + AnrKeyForwarder");
        return false;
    }

    /**
     * Enable prompt → Solar MainActivity (July-2 monlith).
     * 2026-07-10 — Companion no longer owns USB; broadcast + MainActivity handoff.
     */
    public static void showUsbStoragePrompt(Context ctx) {
        if (ctx == null) return;
        if (shouldDeferUsbForNativeErrorTier()) {
            queuePendingUsbPromptEarly();
            SolarContextBridge.log("usbStorage deferred — native_error tier active");
            return;
        }
        deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_USB_STORAGE, USB_STORAGE_RECEIVER);
        launchSolarUsbHandoff(ctx, true, false);
    }

    /**
     * 2026-07-19 — No-op: stock SystemUI owns USB UI; never wake Solar from this path.
     * Was: finish UsbStorageActivity + Solar handoff. Reversal: restore body from git.
     */
    public static void routeUsbConcierge(Context ctx, boolean umsExported) {
        if (ctx == null) return;
        SolarContextBridge.log("usbConcierge skip — stock USB UI (hooks off)");
    }

    /**
     * Start Solar MainActivity with USB handoff extras (July-2 monlith).
     * enable+lock → auto-connect; lock only → eject screen; enable only → enable prompt.
     */
    public static void launchSolarUsbHandoff(Context ctx, boolean enableStorage, boolean lockOnly) {
        if (ctx == null) return;
        try {
            Intent home = new Intent();
            home.setComponent(new ComponentName(SOLAR_PKG, MAIN_ACTIVITY));
            home.setAction(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            if (lockOnly) {
                home.putExtra(EXTRA_USB_OVERLAY_LOCK, true);
            }
            if (enableStorage) {
                home.putExtra(EXTRA_USB_OVERLAY_ENABLE, true);
            }
            ctx.startActivity(home);
            SolarContextBridge.log("usbHandoff MainActivity enable=" + enableStorage
                    + " lock=" + lockOnly);
            if (lockOnly) {
                deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK, USB_STORAGE_RECEIVER);
            } else {
                deliverOpenBroadcast(ctx, ACTION_SHOW_OVERLAY_USB_STORAGE, USB_STORAGE_RECEIVER);
            }
        } catch (Throwable t) {
            SolarContextBridge.log("usbHandoff failed: " + t.getClass().getSimpleName());
            deliverOpenBroadcast(ctx,
                    lockOnly ? ACTION_SHOW_OVERLAY_USB_STORAGE_LOCK : ACTION_SHOW_OVERLAY_USB_STORAGE,
                    USB_STORAGE_RECEIVER);
        }
    }

    /** Xposed hook fired — Java USB_STATE helpers defer to this sysprop (2026-07-06). */
    private static void markUsbConciergeHandled() {
        setSysProp(SYSPROP_USB_CONCIERGE, "1");
        setSysProp(SYSPROP_USB_CONCIERGE_AT, String.valueOf(SystemClock.elapsedRealtime()));
    }

    private static void setSysProp(String key, String val) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set", key, val);
        } catch (Throwable ignored) {}
    }

    /** 2026-07-06 — Native ANR/crash tier active — USB must queue, not compete for overlay paint. */
    private static boolean shouldDeferUsbForNativeErrorTier() {
        return TIER_NATIVE_ERROR.equals(readSysProp(SYSPROP_OVERLAY_TIER, "none"));
    }

    private static void queuePendingUsbPromptEarly() {
        setSysProp(SYSPROP_PENDING_USB, "1");
    }

    /** Reserve native_error before :overlay paints — blocks USB cold-start race with ANR hook. */
    private static void reserveNativeErrorTierEarly() {
        setSysProp(SYSPROP_OVERLAY_TIER, TIER_NATIVE_ERROR);
    }

    private static String readSysProp(String key, String def) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            Object v = de.robv.android.xposed.XposedHelpers.callStaticMethod(
                    sp, "get", key, def);
            return v != null ? v.toString() : def;
        } catch (Throwable ignored) {
            return def;
        }
    }

    /** Replace stock Bluetooth pairing dialog — PIN keyboard or passkey match overlay. */
    public static boolean showBluetoothPairing(Context ctx, int mode, String address,
            String name, int passkey, String pinPrefill) {
        if (ctx == null || address == null || address.length() == 0) return false;
        if (!canDeliverOverlay(ctx)) return false;
        if (startBluetoothPairingService(ctx, mode, address, name, passkey, pinPrefill)) return true;
        Intent intent = new Intent(ACTION_SHOW_OVERLAY_BT_PAIRING);
        intent.setComponent(new ComponentName(SOLAR_PKG, BT_PAIRING_RECEIVER));
        intent.putExtra(EXTRA_BT_PAIRING_MODE, mode);
        intent.putExtra(EXTRA_BT_PAIRING_ADDRESS, address);
        intent.putExtra(EXTRA_BT_PAIRING_NAME, name != null ? name : "");
        intent.putExtra(EXTRA_BT_PAIRING_PASSKEY, passkey);
        if (pinPrefill != null) intent.putExtra(EXTRA_BT_PAIRING_PIN_PREFILL, pinPrefill);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("btPairing broadcast mode=" + mode);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("btPairing broadcast failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /** UMS already exported — Solar STATE_USB_STORAGE eject screen (July-2 monlith). */
    public static void bringSolarToUsbLockScreen(Context ctx) {
        routeUsbConcierge(ctx, true);
    }

    /**
     * @deprecated USB no longer uses companion/overlay service paint.
     * Kept so older call sites compile; routes to MainActivity handoff.
     */
    private static boolean startUsbStorageService(Context ctx, boolean lockOnly) {
        launchSolarUsbHandoff(ctx, !lockOnly, lockOnly);
        return true;
    }

    /** @deprecated use {@link #launchSolarUsbHandoff}(ctx, false, true). */
    private static boolean startUsbStorageLockService(Context ctx) {
        launchSolarUsbHandoff(ctx, false, true);
        return true;
    }

    /** 2026-07-08 — BT pairing on the one shell. */
    private static boolean startBluetoothPairingService(Context ctx, int mode, String address,
            String name, int passkey, String pinPrefill) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_BT_PAIRING);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        svc.putExtra(EXTRA_BT_PAIRING_MODE, mode);
        svc.putExtra(EXTRA_BT_PAIRING_ADDRESS, address);
        svc.putExtra(EXTRA_BT_PAIRING_NAME, name != null ? name : "");
        svc.putExtra(EXTRA_BT_PAIRING_PASSKEY, passkey);
        if (pinPrefill != null) svc.putExtra(EXTRA_BT_PAIRING_PIN_PREFILL, pinPrefill);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("btPairing startService mode=" + mode);
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("btPairing startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 2026-07-08 — ANR/crash dialog on the one shell (companion primary).
     * Was: Solar first + wrong-package companion fallback. Now: shellPkg routing only.
     */
    private static boolean startNativeDialogService(Context ctx, String title, String message,
            String[] buttons, String sessionId, String callerPackage) {
        Intent svc = new Intent(ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        svc.putExtra(EXTRA_DIALOG_MESSAGE, message != null ? message : "");
        svc.putExtra(EXTRA_DIALOG_BUTTONS, buttons);
        svc.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
        if (callerPackage != null) svc.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
        if (title != null) svc.putExtra(EXTRA_MENU_TITLE, title);
        try {
            ctx.startService(svc);
            SolarContextBridge.log("nativeDialog startService buttons=" + buttons.length
                    + " → " + shellPkg(ctx));
            return true;
        } catch (Throwable primary) {
            SolarContextBridge.log("nativeDialog startService failed: "
                    + primary.getClass().getSimpleName());
            // One-shot alternate host if primary package missing.
            String altPkg = useCompanionShell(ctx) ? SOLAR_PKG : COMPANION_PKG;
            String altSvc = useCompanionShell(ctx) ? OVERLAY_SERVICE : COMPANION_OVERLAY;
            Intent fallback = new Intent(ACTION_SHOW_OVERLAY_NATIVE_DIALOG);
            fallback.setComponent(new ComponentName(altPkg, altSvc));
            fallback.putExtra(EXTRA_DIALOG_MESSAGE, message != null ? message : "");
            fallback.putExtra(EXTRA_DIALOG_BUTTONS, buttons);
            fallback.putExtra(EXTRA_MENU_SESSION_ID, sessionId);
            if (callerPackage != null) fallback.putExtra(EXTRA_MENU_CALLER_PACKAGE, callerPackage);
            if (title != null) fallback.putExtra(EXTRA_MENU_TITLE, title);
            try {
                ctx.startService(fallback);
                SolarContextBridge.log("nativeDialog alt shell ok → " + altPkg);
                return true;
            } catch (Throwable fallbackEx) {
                SolarContextBridge.log("nativeDialog alt failed: "
                        + fallbackEx.getClass().getSimpleName());
                return false;
            }
        }
    }

    /**
     * Set opening prop before startService so duplicate Xposed/daemon triggers coalesce.
     * 2026-07-11 — Do NOT stamp shell_visible here. Early shell_visible=1 armed key capture
     * with no WM window (frozen wheel) and defeated StaleOverlayGate heal.
     * Companion arms shell_visible at real addView. Reversal: restore provisional shell_visible.
     */
    private static void setOverlayOpeningEarly(boolean opening) {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set",
                    OverlayKeyForwarder.OPENING_PROPERTY, opening ? "1" : "0");
            if (opening) {
                de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set",
                        OverlayKeyForwarder.OPENING_AT_PROPERTY,
                        String.valueOf(SystemClock.elapsedRealtime()));
            } else {
                de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set",
                        OverlayKeyForwarder.OPENING_AT_PROPERTY, "0");
            }
        } catch (Throwable t) {
            SolarContextBridge.log("overlay opening prop failed: " + t.getClass().getSimpleName());
        }
    }

    /** Prop on dismiss only — never set active=1 before :overlay arms a key handler (freezes UI). */
    private static void setOverlayActiveEarly(boolean active) {
        if (active) return;
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            de.robv.android.xposed.XposedHelpers.callStaticMethod(sp, "set",
                    OverlayKeyForwarder.ACTIVE_PROPERTY, "0");
            SolarContextBridge.log("overlay prop early=0");
        } catch (Throwable t) {
            SolarContextBridge.log("overlay prop early failed: " + t.getClass().getSimpleName());
        }
    }

    /**
     * 2026-07-08 — Direct startService to the one shell (companion unless legacy).
     * Layman: wakes the system menu host even if Solar Home was force-stopped.
     */
    private static boolean startOverlayService(Context ctx, String action) {
        if (ctx == null || action == null) return false;
        Intent svc = new Intent(action);
        svc.setComponent(new ComponentName(shellPkg(ctx), shellService(ctx)));
        try {
            ctx.startService(svc);
            SolarContextBridge.log("startService " + action + " → " + shellPkg(ctx));
            return true;
        } catch (Throwable t) {
            SolarContextBridge.log("startService failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    private static void deliverOpenBroadcast(Context ctx, String action, String receiverClass) {
        if (ctx == null || action == null || receiverClass == null) return;
        Intent open = new Intent(action);
        open.setComponent(new ComponentName(SOLAR_PKG, receiverClass));
        open.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(open);
            SolarContextBridge.log("open broadcast " + action);
        } catch (Throwable t) {
            SolarContextBridge.log("open broadcast failed: " + t.getClass().getSimpleName());
        }
    }

    private static void startOverlayFallback(Context ctx, String action) {
        if (ctx == null || action == null) return;
        Intent broadcast = new Intent(action);
        broadcast.setComponent(new ComponentName(SOLAR_PKG, TRIGGER_RECEIVER));
        deliver(ctx, broadcast);
    }

    private static void deliver(Context ctx, Intent intent) {
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent, PERMISSION);
            SolarContextBridge.log("broadcast " + intent.getAction());
            return;
        } catch (Throwable t) {
            SolarContextBridge.log("permission broadcast failed: " + t.getClass().getSimpleName());
        }
        try {
            ctx.sendBroadcast(intent);
            SolarContextBridge.log("broadcast (open) " + intent.getAction());
        } catch (Throwable t) {
            SolarContextBridge.log("broadcast failed: " + t.getClass().getSimpleName());
        }
    }
}
