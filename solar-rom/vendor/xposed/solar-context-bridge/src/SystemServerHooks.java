package com.solar.launcher.xposed.bridge;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.KeyEvent;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * system_server hooks: long BACK / OK in stock apps → Solar overlay; Y2 power menu → overlay.
 */
final class SystemServerHooks {

    private static final String PWM = "com.android.internal.policy.impl.PhoneWindowManager";
    private static final String GLOBAL_ACTIONS = "com.android.internal.policy.impl.GlobalActions";
    /** OK/center app-menu long-press — separate from BACK/POWER modal tiers. */
    private static final long CENTER_LONG_MS =
            com.solar.input.policy.GlobalInputPolicy.CENTER_MENU_HOLD_MS;
    /** Ultra-long BACK — restart Solar from Rockbox / third-party apps (Solar handles in-app). */
    private static final long BACK_RESTART_SOLAR_MS = 10000L;
    /** PWM interceptKeyBeforeDispatching: return value >= 0 means key consumed. */
    private static final long KEY_CONSUMED_DISPATCH = 0L;
    /** PWM interceptKeyBeforeQueueing: return 0 drops the key before dispatch. */
    private static final int KEY_CONSUMED_QUEUE = 0;

    /** Must match {@link com.solar.launcher.OverlayKeyGate#DISARM_PULSE_PROPERTY}. */
    private static final String DISARM_PULSE_PROPERTY = "sys.solar.overlay.disarm_pulse";
    /** Must match {@link com.solar.launcher.OverlayKeyGate#COOLDOWN_UNTIL_PROPERTY}. */
    private static final String COOLDOWN_UNTIL_PROPERTY = "sys.solar.overlay.cooldown";
    /** Must match {@link com.solar.launcher.ExternalInputHandoff#HANDOFF_ACTIVE_PROPERTY}. */
    private static final String HANDOFF_ACTIVE_PROPERTY = "sys.solar.handoff.active";

    private static Handler mainHandler;
    private static HandlerThread mainHandlerThread;

    private static boolean backLongFired;
    /** 2026-07-06 — Opening-gesture finger still down after modal fired — block dismiss-forward until UP. */
    private static boolean backOpenGestureHeld;
    private static boolean backRestartFired;
    private static boolean backTracking;
    private static boolean backUpHandled;
    /** Let synthetic short-BACK taps reach the app — do not re-enter long-press swallow. */
    private static volatile boolean ignoreBackHookForInject;
    private static Runnable backLongRunnable;
    private static Runnable backRestartRunnable;
    /** Foreground pkg captured on BACK DOWN — avoids getRunningTasks again at 4s fire. */
    private static String cachedBackLongFg;
    /** Uptime when BACK DOWN began — Rockbox passthrough timing on UP. */
    private static long backDownAt;

    /** Y2 dedicated power key — MTK may never call showGlobalActionsDialog. */
    private static boolean powerLongFired;
    private static boolean powerRestartFired;
    private static boolean powerTracking;
    private static long powerDownAt;
    /**
     * 2026-07-15 — Screen lit when POWER went down (wake vs sleep intent).
     * Layman: dark at press = wake; lit at press = sleep. UP-time isScreenOn is true after wake.
     */
    private static boolean powerScreenOnAtDown = true;
    private static Runnable powerLongRunnable;
    private static Runnable powerRestartRunnable;
    private static Runnable powerRescueArmRunnable;
    private static Runnable backRescueArmRunnable;
    private static String cachedPowerLongFg;
    /** 2026-07-06 — Rescue-only hold while sys.solar.overlay.active=1 (USB prompt stall). */
    private static boolean overlayRescueBackHeld;
    private static boolean overlayRescuePowerHeld;
    private static String overlayRescueFg;

    private static boolean centerLongFired;
    private static boolean centerTracking;
    private static boolean centerUpHandled;
    /** Swallow re-entrant PWM hooks while a synthetic OK/MENU tap is injecting (async inject loops otherwise). */
    private static volatile boolean ignoreCenterHookForInject;
    private static Runnable centerLongRunnable;
    /** InputManager.injectInputEvent mode — async; never block PWM thread (causes System ANR). */
    private static final int INJECT_MODE_ASYNC = 0;
    /** Delay before clearing inject guard — covers async inject delivery on MTK (120ms was too short). */
    private static final long INJECT_GUARD_CLEAR_MS = 450L;
    /** Gap after UP before synthetic BACK inject — lets tracking state settle on queue+dispatch hooks. */
    private static final long BACK_INJECT_DELAY_MS = 30L;

    private static Object phoneWindowManagerRef;
    private static Object[] lastDispatchArgs;
    private static long lastDisarmPulseSeen;

    /** Y2 install — power-hold may open global modal over Rockbox; BACK-long still blocked there. */
    private static boolean allowRockboxPowerLongOverlay;

    private static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    private static final String COMPANION_COORDINATOR =
            COMPANION_PKG + ".GlobalInputCoordinatorService";
    private static final String COMPANION_OVERLAY =
            COMPANION_PKG + ".GlobalContextOverlayService";
    private static final String SOLAR_OVERLAY = "com.solar.launcher.SolarOverlayService";
    private static final String ACTION_DISMISS_OVERLAY =
            "com.solar.launcher.action.DISMISS_OVERLAY";
    private static final String ACTION_HOLD_DOWN =
            "com.solar.launcher.globalcontext.action.HOLD_DOWN";
    private static final String ACTION_HOLD_UP =
            "com.solar.launcher.globalcontext.action.HOLD_UP";
    private static final String EXTRA_KEY_CODE = "key_code";
    private static final String EXTRA_FOREGROUND_PKG = "foreground_pkg";
    private static final String EXTRA_HOLD_MS = "hold_ms";
    private static final String EXTRA_Y2_DEVICE = "y2_device";
    private static final long OVERLAY_DISMISS_HOLD_MS = 150L;
    private static boolean overlayPowerDismissTracking;
    private static boolean overlayPowerDismissFired;
    private static Runnable overlayPowerDismissRunnable;

    /**
     * 2026-07-14 — Companion HOLD_DOWN FSM retired for BACK/POWER (Solar-only menus + perf).
     * Layman: holding Back/Power no longer wakes a second APK just to decide the menu.
     * Was: startService companion coordinator on every hold-down (system-wide quick menu).
     * Reversal: restore startService HOLD_DOWN body (POLICY_REV 21 path).
     */
    private static boolean forwardHoldDownToCoordinator(Context ctx, int keyCode, String fg) {
        return false;
    }

    /** 2026-07-14 — HOLD_UP companion forward also retired with HOLD_DOWN. */
    private static void forwardHoldUpToCoordinator(Context ctx) {
        // no-op — companion hold FSM unused
    }

    private SystemServerHooks() {}

    /** Y1: long BACK → Solar Home; long OK app-menu hooks; volume OSD. */
    static void installY1(LoadPackageParam lpparam) {
        Class<?> pwm = findPhoneWindowManager(lpparam);
        if (pwm == null) return;
        SolarContextBridge.log("Y1 PhoneWindowManager loaded");
        installLongPressHooks(pwm);
        VolumePanelHooks.installSystemServer(lpparam);
        AppErrorHooks.install(lpparam);
        AppAnrHooks.install(lpparam);
        AnrDialogKeyForwarder.install(pwm);
        ImeFocusHooks.installSystemServer(lpparam);
        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("target", "Y1");
            d.put("pwm", pwm.getName());
            PowerMenuDebugLog.event("SystemServerHooks.installY1", "hooks installed", "H1", d);
        } catch (Throwable ignored) {}
        // #endregion
    }

    /** Y2: Solar POWER → in-app menu; long BACK → Solar Home; stock power menu elsewhere. */
    static void installY2(LoadPackageParam lpparam) {
        allowRockboxPowerLongOverlay = true;
        Class<?> pwm = findPhoneWindowManager(lpparam);
        if (pwm == null) return;
        SolarContextBridge.log("Y2 PhoneWindowManager loaded");
        hookSuppressGlobalActions(pwm, lpparam.classLoader);
        hookY2PowerLongPress(pwm);
        installLongPressHooks(pwm);
        VolumePanelHooks.installSystemServer(lpparam);
        AppErrorHooks.install(lpparam);
        AppAnrHooks.install(lpparam);
        AnrDialogKeyForwarder.install(pwm);
        ImeFocusHooks.installSystemServer(lpparam);
        // 2026-07-05 — Y2 UMS: MountService/vold sync for Mac-native mass storage (dual volume).
        UsbMassStorageServerHooks.installY2(lpparam);
        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("target", "Y2");
            d.put("pwm", pwm.getName());
            PowerMenuDebugLog.event("SystemServerHooks.installY2", "hooks installed", "H1", d);
        } catch (Throwable ignored) {}
        // #endregion
    }

    private static void installLongPressHooks(Class<?> pwm) {
        ensureMainHandler();
        initBackLongRunnable();
        initBackRestartRunnable();
        initPowerRestartRunnable();
        initCenterLongRunnable();
        hookBackLongPress(pwm);
        hookBackLongPressQueueing(pwm);
        hookCenterLongPress(pwm);
        hookCenterBeforeQueueing(pwm);
        hookBackBeforeQueueing(pwm);
        OverlayKeyForwarder.hookPhoneWindowManager(pwm);
        ImeKeyForwarder.hookPhoneWindowManager(pwm);
    }

    /** HandlerThread — system_server may not have MainLooper when Xposed first hooks android. */
    private static void ensureMainHandler() {
        if (mainHandler != null) return;
        mainHandlerThread = new HandlerThread("SolarCtxKeys");
        mainHandlerThread.start();
        mainHandler = new Handler(mainHandlerThread.getLooper());
    }

    private static Class<?> findPhoneWindowManager(LoadPackageParam lpparam) {
        try {
            return XposedHelpers.findClass(PWM, lpparam.classLoader);
        } catch (Throwable t) {
            SolarContextBridge.log("PhoneWindowManager missing: " + t);
            return null;
        }
    }

    /** Y2 power-hold — Solar overlay in third-party apps; Rockbox allowed on Y2 power-hold only. */
    private static void hookSuppressGlobalActions(Class<?> pwm, ClassLoader cl) {
        XC_MethodHook intercept = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    phoneWindowManagerRef = param.thisObject;
                    Context ctx = resolveContext(param.thisObject);
                    String fg = foregroundPackageFromArgs(null, ctx, param.thisObject);
                    // #region agent log
                    try {
                        JSONObject d = new JSONObject();
                        d.put("fg", fg != null ? fg : "");
                        PowerMenuDebugLog.event("SystemServerHooks.GlobalActions", "intercept", "H5", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                    // 2026-07-06 — Short tap must reach stock sleep; only replace GlobalActions after long-hold fired.
                    if (!powerLongFired) {
                        return;
                    }
                    // 2026-07-14 — Suppress stock power menu only over Solar Home (in-app sheet).
                    // Outside Solar: never skipMethod — stock GlobalActions must show.
                    // Was: showPowerOverlay for 3P/Rockbox/JJ (system-wide Solar quick menu).
                    // Reversal: restore showSolarPowerOverlay + skipMethod for eligible non-Solar fg.
                    if ("com.solar.launcher".equals(fg)
                            && shouldOfferPowerLongOverlayForPackage(fg)) {
                        com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
                        if (!com.solar.input.policy.StaleOverlayGate.isActiveOrOpening()) {
                            SolarOverlayClient.showInAppPowerMenu(ctx);
                        }
                        XposedHookKit.skipMethod(param);
                        SolarContextBridge.log("suppressed GlobalActions → Solar in-app menu");
                        // #region agent log
                        try {
                            JSONObject d = new JSONObject();
                            d.put("fg", fg);
                            d.put("action", "solar_inapp");
                            PowerMenuDebugLog.event("SystemServerHooks.GlobalActions",
                                    "solar-only suppress", "c54726-H1", d);
                        } catch (Throwable ignored) {}
                        // #endregion
                        return;
                    }
                    // #region agent log
                    try {
                        JSONObject d = new JSONObject();
                        d.put("fg", fg != null ? fg : "");
                        d.put("action", "stock_passthrough");
                        PowerMenuDebugLog.event("SystemServerHooks.GlobalActions",
                                "stock power menu", "c54726-H1", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                } catch (Throwable t) {
                    SolarContextBridge.log("GlobalActions intercept error: " + t.getClass().getSimpleName());
                }
            }
        };
        int n = 0;
        n += XposedHookKit.hookAll(pwm, "showGlobalActionsDialog", intercept);
        n += XposedHookKit.hookAll(pwm, "showGlobalActionsInternal", intercept);
        try {
            Class<?> ga = XposedHelpers.findClass(GLOBAL_ACTIONS, cl);
            n += XposedHookKit.hookAll(ga, "showDialog", intercept);
            n += XposedHookKit.hookAll(ga, "handleShow", intercept);
            n += XposedHookKit.hookAll(ga, "prepareDialog", intercept);
        } catch (Throwable t) {
            SolarContextBridge.log("GlobalActions class missing: " + t.getClass().getSimpleName());
        }
        SolarContextBridge.log("GlobalActions hooks=" + n);
        // #region agent log
        try {
            JSONObject d = new JSONObject();
            d.put("hookCount", n);
            PowerMenuDebugLog.event("SystemServerHooks.hookSuppressGlobalActions", "hook attach result", "H1", d);
        } catch (Throwable ignored) {}
        // #endregion
    }

    /** Y2 power-hold — queueing/dispatch hooks; MTK often skips showGlobalActionsDialog. */
    private static void hookY2PowerLongPress(Class<?> pwm) {
        ensureMainHandler();
        initPowerLongRunnable();
        initOverlayPowerDismissRunnable();
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumePowerHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                if (handlePowerLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_QUEUE);
                }
            }
        });
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumePowerHeldAfterLongOpen(param, param.args, KEY_CONSUMED_DISPATCH)) {
                    return;
                }
                if (handlePowerLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_DISPATCH);
                }
            }
        });
        // Belt-and-suspenders when PWM calls interceptPowerKeyDown before GlobalActions.
        XposedHookKit.hookDeclared(pwm, "interceptPowerKeyDown", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (powerLongFired) return;
                if (param.args == null || param.args.length < 2) return;
                boolean hungUp = Boolean.TRUE.equals(param.args[1]);
                if (!hungUp) {
                    armPowerLongPress(param.thisObject, null, null);
                }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (powerLongFired) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
        SolarContextBridge.log("Y2 power-long hooks installed");
    }

    /** 2026-07-05 — Re-resolve fg at fire; Y2 fail-open when task probe returns systemui/null over Rockbox. */
    private static String resolvePowerLongForegroundAtFire(Context ctx) {
        String live = foregroundPackageForBackLong(null, ctx, phoneWindowManagerRef);
        if (live != null && live.length() > 0 && !isSystemShellPackage(live)) {
            return live;
        }
        String cached = cachedPowerLongFg;
        if (cached != null && cached.length() > 0 && !isSystemShellPackage(cached)) {
            return cached;
        }
        return live;
    }

    /**
     * 2026-07-06 — Re-resolve fg when BACK-long fires — SystemUI shell over stock app gets fast tier.
     * Layman: USB dialog on screen still opens quick menu at ~420ms like the concierge overlay.
     * Reversal: use cachedBackLongFg only — systemui fg may skip modal eligibility.
     */
    private static String resolveBackLongForegroundAtFire(Context ctx) {
        String live = foregroundPackageForBackLong(null, ctx, phoneWindowManagerRef);
        if (live != null && live.length() > 0 && !isSystemShellPackage(live)) {
            return live;
        }
        String cached = cachedBackLongFg;
        if (cached != null && cached.length() > 0 && !isSystemShellPackage(cached)) {
            return cached;
        }
        return live;
    }

    private static void initPowerLongRunnable() {
        if (powerLongRunnable != null) return;
        powerLongRunnable = new Runnable() {
            @Override
            public void run() {
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = resolvePowerLongForegroundAtFire(ctx);
                // Gate before powerLongFired — ineligible holds must not swallow POWER UP / stock menu.
                if (!com.solar.input.policy.GlobalInputPolicy.shouldOfferPowerLongModal(
                        fg, allowRockboxPowerLongOverlay)) {
                    SolarContextBridge.log("power-long skipped fg=" + fg);
                    // #region agent log
                    try {
                        JSONObject d = new JSONObject();
                        d.put("fg", fg != null ? fg : "");
                        d.put("action", "stock_or_noop");
                        PowerMenuDebugLog.event("SystemServerHooks.powerLongRunnable",
                                "skipped not Solar", "c54726-H2", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                    return;
                }
                powerLongFired = true;
                com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
                if (com.solar.input.policy.StaleOverlayGate.isActiveOrOpening()) {
                    SolarContextBridge.log("power-long skipped overlay opening");
                    return;
                }
                // 2026-07-14 — Solar-only: in-app ThemedContextMenu (never WM companion outside).
                SolarOverlayClient.showInAppPowerMenu(ctx);
                SolarContextBridge.log("power-long Solar → in-app context menu");
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("fg", fg != null ? fg : "");
                    d.put("action", "solar_inapp");
                    PowerMenuDebugLog.event("SystemServerHooks.powerLongRunnable",
                            "solar in-app menu", "c54726-H2", d);
                } catch (Throwable ignored) {}
                // #endregion
            }
        };
    }

    /** Swallow POWER until finger lifts after long-hold opened the overlay. */
    private static boolean consumePowerHeldAfterLongOpen(XC_MethodHook.MethodHookParam param,
            Object[] args, Object consumeResult) {
        if (!powerLongFired) return false;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_POWER) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clearPowerLongPressState();
            SolarContextBridge.log("power-long release swallowed");
        }
        param.setResult(consumeResult);
        return true;
    }

    private static void clearPowerLongPressState() {
        // 2026-07-20 — Snapshot before clear: cancel spinner only if menu never opened.
        boolean wasTracking = powerTracking;
        boolean menuOpened = powerLongFired;
        Context ctxForCancel = resolveContext(phoneWindowManagerRef);
        boolean restartWasFired = powerRestartFired;
        powerLongFired = false;
        powerRestartFired = false;
        powerTracking = false;
        powerDownAt = 0L;
        powerScreenOnAtDown = true;
        cachedPowerLongFg = null;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(powerLongRunnable);
            mainHandler.removeCallbacks(powerRestartRunnable);
            removePowerRescueArmCallback();
        }
        if (!restartWasFired) {
            SolarRescueHoldClient.disarm();
        }
        // Layman: let go of Power before Options → stop the status spinner.
        // Tech: mirror ContextHoldThrobberGate.shouldClearOnHoldCancel (bridge APK has no app class).
        // Reversal: drop cancel broadcast; spinner stays until auto-end timeout.
        if (wasTracking && !menuOpened) {
            SolarOverlayClient.cancelInAppContextHoldThrobber(ctxForCancel);
        }
    }

    private static void initOverlayPowerDismissRunnable() {
        if (overlayPowerDismissRunnable != null) return;
        overlayPowerDismissRunnable = new Runnable() {
            @Override
            public void run() {
                overlayPowerDismissFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                dismissAnyOverlay(ctx);
            }
        };
    }

    private static void armOverlayPowerDismiss() {
        ensureMainHandler();
        initOverlayPowerDismissRunnable();
        overlayPowerDismissTracking = true;
        overlayPowerDismissFired = false;
        if (mainHandler != null) {
            mainHandler.removeCallbacks(overlayPowerDismissRunnable);
            mainHandler.postDelayed(overlayPowerDismissRunnable, OVERLAY_DISMISS_HOLD_MS);
        }
    }

    private static void clearOverlayPowerDismissState() {
        overlayPowerDismissTracking = false;
        overlayPowerDismissFired = false;
        if (mainHandler != null && overlayPowerDismissRunnable != null) {
            mainHandler.removeCallbacks(overlayPowerDismissRunnable);
        }
    }

    /** Tear down companion + Solar overlay shells — package-visible for OverlayKeyForwarder. */
    static void dismissAnyOverlay(Context ctx) {
        if (ctx == null) return;
        try {
            Intent solar = new Intent(ACTION_DISMISS_OVERLAY);
            solar.setComponent(new ComponentName("com.solar.launcher", SOLAR_OVERLAY));
            ctx.startService(solar);
        } catch (Throwable ignored) {}
        try {
            Intent companion = new Intent(ACTION_DISMISS_OVERLAY);
            companion.setComponent(new ComponentName(COMPANION_PKG, COMPANION_OVERLAY));
            ctx.startService(companion);
        } catch (Throwable ignored) {}
    }

    /** Y2 hardware power — long-hold opens Solar modal; short tap passes through for sleep (RC-SLEEP). */
    private static boolean handlePowerLongPressEvent(Object pwmThis, Object[] args) {
        absorbOverlayDisarmPulse();
        com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
        if (powerLongFired) {
            return consumePowerHeldAfterLongOpenQueue(args);
        }
        phoneWindowManagerRef = pwmThis;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_POWER) return false;
        int action = event.getAction();
        Context ctx = resolveContext(pwmThis);
        String fg = foregroundPackageForBackLong(args, ctx, pwmThis);
        if (com.solar.input.policy.StaleOverlayGate.isActiveOrOpening()) {
            // 2026-07-06 — Modal already up — skip second quick menu; still allow 10s power rescue.
            trackOverlayRescueHold(ctx, event, fg);
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                armOverlayPowerDismiss();
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                forwardHoldUpToCoordinator(ctx);
                boolean consume = overlayPowerDismissTracking || overlayPowerDismissFired;
                clearOverlayPowerDismissState();
                if (OverlayKeyForwarder.isOverlayActiveOrOpening()) {
                    powerTracking = false;
                    cachedPowerLongFg = null;
                    powerDownAt = 0L;
                    return true;
                }
                if (consume) return true;
            }
            return overlayPowerDismissTracking || overlayPowerDismissFired;
        }

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && !powerTracking) {
                boolean screenOnDown = readPowerManagerScreenOn(pwmThis);
                // #region agent log
                debugD74b0d("C,D", "SystemServerHooks.handlePowerLongPressEvent:DOWN",
                        "power DOWN arm", 0L, false, screenOnDown, screenOnDown, false);
                // #endregion
                armPowerLongPress(pwmThis, fg, ctx);
            }
            return false;
        }
        if (action == KeyEvent.ACTION_UP) {
            forwardHoldUpToCoordinator(ctx);
            mainHandler.removeCallbacks(powerLongRunnable);
            mainHandler.removeCallbacks(powerRestartRunnable);
            removePowerRescueArmCallback();
            if (!powerRestartFired) {
                SolarRescueHoldClient.disarm();
            }
            if (OverlayKeyForwarder.isOverlayActiveOrOpening()) {
                powerTracking = false;
                cachedPowerLongFg = null;
                powerDownAt = 0L;
                powerScreenOnAtDown = true;
                return true;
            }
            if (powerLongFired || powerRestartFired) {
                clearPowerLongPressState();
                return true;
            }
            if (powerTracking) {
                long held = powerDownAt > 0 ? SystemClock.uptimeMillis() - powerDownAt : 0L;
                boolean screenOnNow = readPowerManagerScreenOn(pwmThis);
                boolean forceSleep = allowRockboxPowerLongOverlay
                        && com.solar.input.policy.GlobalInputPolicy.shouldForcePowerTapSleep(
                                held, powerScreenOnAtDown);
                // #region agent log
                debugD74b0d("A,B,C", "SystemServerHooks.handlePowerLongPressEvent:UP",
                        "power short-UP decision", held, forceSleep, screenOnNow,
                        powerScreenOnAtDown, forceSleep);
                // #endregion
                if (forceSleep) {
                    triggerGoToSleep(pwmThis);
                }
                // 2026-07-20 — Short Power tap: drop Options hold spinner (menu never opened).
                // Layman: quick press-and-release stops the spinner like a cancelled Back-hold.
                // Reversal: leave spinner until auto-end; only clearPowerLongPressState cancels.
                SolarOverlayClient.cancelInAppContextHoldThrobber(ctx);
                powerTracking = false;
                cachedPowerLongFg = null;
                powerDownAt = 0L;
                powerScreenOnAtDown = true;
            }
            return false;
        }
        return false;
    }

    /**
     * 2026-07-14 — Arm Y2 POWER hold: Solar in-app menu timer and/or 10s rescue only.
     * Layman: outside Solar we do not steal Power for a Solar menu — stock sleep/power owns it.
     * Was: companion HOLD_DOWN + modal for almost every fg (laggy + hid stock power menu).
     */
    private static void armPowerLongPress(Object pwmThis, String fg, Context ctx) {
        if (powerTracking) return;
        phoneWindowManagerRef = pwmThis;
        if (ctx == null) ctx = resolveContext(pwmThis);
        if (fg == null || fg.length() == 0) {
            fg = foregroundPackageForBackLong(null, ctx, pwmThis);
        }
        powerLongFired = false;
        powerRestartFired = false;
        powerTracking = true;
        powerDownAt = SystemClock.uptimeMillis();
        // 2026-07-15 — Capture lit/dark at DOWN; UP-time screenOn is true after wake (RC-WAKE).
        powerScreenOnAtDown = readPowerManagerScreenOn(pwmThis);
        cachedPowerLongFg = fg;
        ensureMainHandler();
        mainHandler.removeCallbacks(powerLongRunnable);
        mainHandler.removeCallbacks(powerRestartRunnable);
        removePowerRescueArmCallback();
        // Solar fg only — never schedule modal arms that set powerLongFired over 3P apps.
        if (com.solar.input.policy.GlobalInputPolicy.shouldOfferPowerLongModal(
                fg, allowRockboxPowerLongOverlay)) {
            // 2026-07-20 — Spinner during Power-hold wait (parity with Back KEY_DOWN arm).
            // Layman: press Power for Options and the status spinner starts right away.
            // Was: spinner only after OPEN_CONTEXT_MENU. Reversal: drop armInApp call.
            SolarOverlayClient.armInAppContextHoldThrobber(ctx);
            mainHandler.postDelayed(powerLongRunnable,
                    com.solar.input.policy.GlobalInputPolicy.powerModalHoldMsForPackage(fg));
        }
        if (shouldTrackPowerUltraLong(fg)) {
            initPowerRestartRunnable();
            initPowerRescueArmRunnable();
            mainHandler.postDelayed(powerRescueArmRunnable,
                    com.solar.input.policy.GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
            mainHandler.postDelayed(powerRestartRunnable, BACK_RESTART_SOLAR_MS);
        }
    }

    /** 2026-07-06 — BACK rescue HUD at 7s — deadline anchored to hold DOWN (10s total). */
    private static void initBackRescueArmRunnable() {
        if (backRescueArmRunnable != null) return;
        backRescueArmRunnable = new Runnable() {
            @Override
            public void run() {
                if ((!backTracking && !overlayRescueBackHeld) || backRestartFired) return;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx != null) {
                    SolarRescueHoldClient.armBackFromHoldStart(ctx, backDownAt > 0L ? backDownAt
                            : SystemClock.uptimeMillis());
                }
            }
        };
    }

    private static void removeBackRescueArmCallback() {
        if (mainHandler != null && backRescueArmRunnable != null) {
            mainHandler.removeCallbacks(backRescueArmRunnable);
        }
    }

    /** 2026-07-06 — Rescue HUD at 7s only; short tap must not startService/setprop on DOWN. */
    private static void initPowerRescueArmRunnable() {
        if (powerRescueArmRunnable != null) return;
        powerRescueArmRunnable = new Runnable() {
            @Override
            public void run() {
                if ((!powerTracking && !overlayRescuePowerHeld) || powerRestartFired) return;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx != null) SolarRescueHoldClient.armPowerFromHoldStart(ctx, powerDownAt);
            }
        };
    }

    private static void removePowerRescueArmCallback() {
        if (mainHandler != null && powerRescueArmRunnable != null) {
            mainHandler.removeCallbacks(powerRescueArmRunnable);
        }
    }

    /**
     * 2026-07-15 — Y2 short POWER → goToSleep only for sleep taps (screen was on at DOWN).
     * Was: always goToSleep on short UP (RC-SLEEP) which re-slept just-woken displays.
     */
    private static void triggerGoToSleep(Object pwmThis) {
        try {
            Object pm = XposedHelpers.getObjectField(pwmThis, "mPowerManager");
            boolean screenOn = readPowerManagerScreenOn(pwmThis);
            // #region agent log
            debugD74b0d("A,B", "SystemServerHooks.triggerGoToSleep",
                    "about to goToSleep", -1L, true, screenOn, powerScreenOnAtDown, true);
            // #endregion
            if (pm != null) {
                XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                SolarContextBridge.log("power short-tap goToSleep");
            }
        } catch (Throwable t) {
            SolarContextBridge.log("goToSleep failed: " + t.getClass().getSimpleName());
        }
    }

    /** 2026-07-15 — Probe PWM PowerManager.isScreenOn for wake/sleep (API 17/19). */
    private static boolean readPowerManagerScreenOn(Object pwmThis) {
        try {
            Object pm = XposedHelpers.getObjectField(pwmThis, "mPowerManager");
            if (pm == null) return true;
            Object on = XposedHelpers.callMethod(pm, "isScreenOn");
            return on instanceof Boolean ? ((Boolean) on).booleanValue() : true;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 2026-07-15 — Debug session d74b0d: NDJSON to logcat (+ optional tmp file).
     * Layman: breadcrumb for wake vs sleep power taps.
     */
    private static void debugD74b0d(String hypothesisId, String location, String message,
            long heldMs, boolean forceSleep, boolean screenOnNow, boolean screenWasOnAtDown,
            boolean willCallGoToSleep) {
        try {
            JSONObject data = new JSONObject();
            data.put("heldMs", heldMs);
            data.put("forceSleep", forceSleep);
            data.put("screenOnNow", screenOnNow);
            data.put("screenWasOnAtDown", screenWasOnAtDown);
            data.put("willCallGoToSleep", willCallGoToSleep);
            data.put("allowRockboxPowerLongOverlay", allowRockboxPowerLongOverlay);
            JSONObject o = new JSONObject();
            o.put("sessionId", "d74b0d");
            o.put("runId", "post-fix");
            o.put("hypothesisId", hypothesisId);
            o.put("location", location);
            o.put("message", message);
            o.put("data", data);
            o.put("timestamp", System.currentTimeMillis());
            String line = o.toString();
            android.util.Log.i("SolarDebugD74b0d", line);
            java.io.FileWriter fw = new java.io.FileWriter(
                    "/data/local/tmp/solar-debug-d74b0d.log", true);
            fw.write(line);
            fw.write('\n');
            fw.close();
        } catch (Throwable ignored) {}
    }

    /** Swallow POWER queue events after long-hold fired until finger lifts. */
    private static boolean consumePowerHeldAfterLongOpenQueue(Object[] args) {
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_POWER) return true;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clearPowerLongPressState();
            SolarContextBridge.log("power-long release swallowed");
        }
        return true;
    }

    private static void initBackLongRunnable() {
        if (backLongRunnable != null) return;
        backLongRunnable = new Runnable() {
            @Override
            public void run() {
                backLongFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = resolveBackLongForegroundAtFire(ctx);
                if (fg == null || fg.length() == 0) {
                    fg = cachedBackLongFg;
                }
                if (!shouldOfferOverlayForPackage(fg)) {
                    SolarContextBridge.log("back-long skipped fg=" + fg);
                    // #region agent log
                    try {
                        JSONObject d = new JSONObject();
                        d.put("fg", fg != null ? fg : "");
                        PowerMenuDebugLog.event("SystemServerHooks.backLongRunnable", "skipped not eligible", "H3", d);
                    } catch (Throwable ignored) {}
                    // #endregion
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActiveOrOpening()) {
                    SolarContextBridge.log("back-long skipped overlay opening");
                    return;
                }
                // 2026-07-14 — Solar fg: in-app menu; elsewhere HOLD BACK → Solar Home (no WM shell).
                // Was: warmOverlayProcess + showPowerOverlay (system-wide context modal).
                // Reversal: SolarOverlayClient.showPowerOverlay(ctx) for non-Solar.
                if ("com.solar.launcher".equals(fg)) {
                    SolarOverlayClient.showInAppPowerMenu(ctx);
                    backOpenGestureHeld = true;
                    SolarContextBridge.log("back-long Solar → in-app context menu");
                    return;
                }
                boolean launched = SolarOverlayClient.launchSolarHome(ctx);
                backOpenGestureHeld = true;
                SolarContextBridge.log("back-long → Solar Home fg=" + fg + " ok=" + launched);
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("fg", fg != null ? fg : "");
                    d.put("launched", launched);
                    d.put("action", "launch_solar_home");
                    PowerMenuDebugLog.event("SystemServerHooks.backLongRunnable",
                            "launch Solar Home", "c54726-H3", d);
                } catch (Throwable ignored) {}
                // #endregion
            }
        };
    }

    /** Schedule 10s BACK hold → SolarRescue (continued hold after 4s modal). */
    private static void initBackRestartRunnable() {
        if (backRestartRunnable != null) return;
        backRestartRunnable = new Runnable() {
            @Override
            public void run() {
                if ((!backTracking && !overlayRescueBackHeld) || backRestartFired) return;
                backRestartFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                SolarRescueHoldClient.signalRestarting();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        SolarRescueHoldClient.disarm();
                        String fg = cachedBackLongFg != null ? cachedBackLongFg
                                : (overlayRescueFg != null ? overlayRescueFg : "");
                        SolarRescueClient.execute(ctx, fg);
                        SolarContextBridge.log("back-ultra rescue Solar");
                        overlayRescueBackHeld = false;
                        overlayRescueFg = null;
                    }
                }, 400L);
            }
        };
    }

    /**
     * 2026-07-06 — Ultra-long BACK/power rescue while global overlay is armed (USB stall, hung modal).
     * Layman: hold Back or sleep button ~10s to force Solar home even when the USB prompt is on screen.
     * Technical: parallel to {@link OverlayKeyForwarder} — does not open a second quick menu.
     */
    static void trackOverlayRescueHold(Context ctx, KeyEvent event, String fg) {
        if (ctx == null || event == null) return;
        ensureMainHandler();
        initBackRestartRunnable();
        initPowerRestartRunnable();
        initPowerRescueArmRunnable();
        initBackRescueArmRunnable();
        int code = event.getKeyCode();
        int action = event.getAction();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (!shouldTrackBackUltraLong(fg)) return;
                overlayRescueBackHeld = true;
                overlayRescueFg = fg;
                cachedBackLongFg = fg;
                backRestartFired = false;
                if (backDownAt <= 0L) {
                    backDownAt = SystemClock.uptimeMillis();
                }
                mainHandler.removeCallbacks(backRestartRunnable);
                removeBackRescueArmCallback();
                mainHandler.postDelayed(backRescueArmRunnable,
                        com.solar.input.policy.GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
                mainHandler.postDelayed(backRestartRunnable, BACK_RESTART_SOLAR_MS);
            } else if (action == KeyEvent.ACTION_UP) {
                if (!overlayRescueBackHeld) return;
                overlayRescueBackHeld = false;
                mainHandler.removeCallbacks(backRestartRunnable);
                removeBackRescueArmCallback();
                if (!backRestartFired) {
                    SolarRescueHoldClient.disarm();
                }
                backRestartFired = false;
                overlayRescueFg = null;
            }
            return;
        }
        if (code == KeyEvent.KEYCODE_POWER && allowRockboxPowerLongOverlay) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                if (!shouldTrackPowerUltraLong(fg)) return;
                overlayRescuePowerHeld = true;
                overlayRescueFg = fg;
                cachedPowerLongFg = fg;
                powerRestartFired = false;
                mainHandler.removeCallbacks(powerRestartRunnable);
                removePowerRescueArmCallback();
                mainHandler.postDelayed(powerRescueArmRunnable,
                        com.solar.input.policy.GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
                mainHandler.postDelayed(powerRestartRunnable, BACK_RESTART_SOLAR_MS);
            } else if (action == KeyEvent.ACTION_UP) {
                if (!overlayRescuePowerHeld) return;
                overlayRescuePowerHeld = false;
                mainHandler.removeCallbacks(powerRestartRunnable);
                removePowerRescueArmCallback();
                if (!powerRestartFired) {
                    SolarRescueHoldClient.disarm();
                }
                powerRestartFired = false;
                overlayRescueFg = null;
            }
        }
    }

    /** Y2 — 10s power hold → same rescue path as ultra-long BACK. */
    private static void initPowerRestartRunnable() {
        if (powerRestartRunnable != null) return;
        powerRestartRunnable = new Runnable() {
            @Override
            public void run() {
                if ((!powerTracking && !overlayRescuePowerHeld) || powerRestartFired) return;
                powerRestartFired = true;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = cachedPowerLongFg != null ? cachedPowerLongFg
                        : (overlayRescueFg != null ? overlayRescueFg
                        : foregroundPackageForBackLong(null, ctx, phoneWindowManagerRef));
                SolarRescueHoldClient.signalRestarting();
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        SolarRescueHoldClient.disarm();
                        SolarRescueClient.execute(ctx, fg);
                        SolarContextBridge.log("power-ultra rescue Solar");
                        overlayRescuePowerHeld = false;
                        overlayRescueFg = null;
                    }
                }, 400L);
            }
        };
    }

    /** Short BACK while IME — dismiss tray without injecting BACK into foreground app. */
    private static void dismissImeTray(Context ctx) {
        if (ctx == null) return;
        SolarContextBridge.log("dismissImeTray IME_DISMISS broadcast");
        try {
            android.content.Intent svc = new android.content.Intent(
                    "com.solar.launcher.action.IME_DISMISS");
            svc.setComponent(new android.content.ComponentName("com.solar.launcher",
                    "com.solar.launcher.SolarInputMethodService"));
            ctx.startService(svc);
        } catch (Throwable ignored) {}
    }

    private static long centerMenuOpenGraceUntil;

    private static void initCenterLongRunnable() {
        if (centerLongRunnable != null) return;
        centerLongRunnable = new Runnable() {
            @Override
            public void run() {
                centerLongFired = true;
                centerMenuOpenGraceUntil = SystemClock.uptimeMillis() + 900L;
                Context ctx = resolveContext(phoneWindowManagerRef);
                if (ctx == null) return;
                String fg = foregroundPackageFromArgs(lastDispatchArgs, ctx, phoneWindowManagerRef);
                if (!shouldOfferOverlayForPackage(fg)) return;
                if (openOptionsMenuForForeground(ctx)) {
                    SolarContextBridge.log("center-long openOptionsMenu fg=" + fg);
                } else if (openContextMenuForFocusedView(ctx)) {
                    SolarContextBridge.log("center-long performLongClick fg=" + fg);
                } else {
                    injectKeyEvent(KeyEvent.KEYCODE_MENU);
                    SolarContextBridge.log("center-long injected MENU fg=" + fg);
                }
            }
        };
    }

    /** Swallow BACK down/repeat/up until finger lifts after long-press opened the overlay. */
    private static boolean consumeBackHeldAfterLongOpen(XC_MethodHook.MethodHookParam param,
            Object[] args, Object consumeResult) {
        absorbOverlayDisarmPulse();
        if (!backLongFired && !backRestartFired && !backOpenGestureHeld) return false;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            backOpenGestureHeld = false;
            clearBackLongPressState();
            SolarContextBridge.log("back-long release swallowed");
        }
        param.setResult(consumeResult);
        return true;
    }

    /** Swallow OK/MENU until finger lifts after long-press opened the app context menu. */
    private static boolean consumeCenterHeldAfterLongOpen(XC_MethodHook.MethodHookParam param,
            Object[] args, Object consumeResult) {
        absorbOverlayDisarmPulse();
        if (!centerLongFired) return false;
        KeyEvent event = findKeyEvent(args);
        if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            clearCenterLongPressState();
            SolarContextBridge.log("center-long release swallowed");
        }
        param.setResult(consumeResult);
        return true;
    }

    /** Swallow BACK down/repeat while detecting long-press; inject short back on quick release. */
    private static void hookBackLongPress(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_DISPATCH)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (handleBackLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_DISPATCH);
                }
            }
        });
        SolarContextBridge.log("hooked BACK long-press on interceptKeyBeforeDispatching");
    }

    /** MTK Y1 often routes BACK only through queueing — mirror long-press detection here. */
    private static void hookBackLongPressQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (handleBackLongPressEvent(param.thisObject, param.args)) {
                    param.setResult(KEY_CONSUMED_QUEUE);
                }
            }
        });
        SolarContextBridge.log("hooked BACK long-press on interceptKeyBeforeQueueing");
    }

    /** Shared BACK long-press state machine for dispatch + queueing hooks. */
    private static boolean handleBackLongPressEvent(Object pwmThis, Object[] args) {
        if (ignoreBackHookForInject) {
            return false;
        }
        absorbOverlayDisarmPulse();
        phoneWindowManagerRef = pwmThis;
        lastDispatchArgs = args;
        KeyEvent event = findKeyEvent(args);
        if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;

        int action = event.getAction();
        Context ctx = resolveContext(pwmThis);
        String fg;
        if (action == KeyEvent.ACTION_DOWN || !backTracking) {
            long fgT0 = System.nanoTime();
            fg = foregroundPackageForBackLong(args, ctx, pwmThis);
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("fg", fg != null ? fg : "");
                d.put("backTracking", backTracking);
                BridgeAnrDebugLog.hookTiming("SystemServerHooks.backLong.fgLookup", "A", fgT0, d);
            } catch (Throwable ignored) {}
            // #endregion
        } else {
            fg = cachedBackLongFg;
        }
        // Solar in-app handles BACK short/long/restart — never steal here.
        if ("com.solar.launcher".equals(fg)) {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("action", action);
                PowerMenuDebugLog.event("SystemServerHooks.handleBackLongPress", "solar fg skip bridge", "H2", d);
            } catch (Throwable ignored) {}
            // #endregion
            clearBackLongPressState();
            return false;
        }
        // Ultra-long BACK restart — Rockbox, stock, and third-party apps.
        if (!shouldTrackBackUltraLong(fg)) {
            clearBackLongPressState();
            return false;
        }
        boolean overlayEligible = shouldOfferBackLongOverlayForPackage(fg);

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0 && !backTracking) {
                backLongFired = false;
                backRestartFired = false;
                backUpHandled = false;
                backTracking = true;
                cachedBackLongFg = fg;
                backDownAt = SystemClock.uptimeMillis();
                mainHandler.removeCallbacks(backLongRunnable);
                mainHandler.removeCallbacks(backRestartRunnable);
                boolean coordinatorOwns = forwardHoldDownToCoordinator(ctx, KeyEvent.KEYCODE_BACK, fg);
                if (!coordinatorOwns) {
                    initBackRescueArmRunnable();
                    removeBackRescueArmCallback();
                    mainHandler.postDelayed(backRescueArmRunnable,
                            com.solar.input.policy.GlobalInputPolicy.HUD_COUNTDOWN_START_MS);
                    mainHandler.postDelayed(backRestartRunnable, BACK_RESTART_SOLAR_MS);
                    // 2026-07-14 — Schedule HOLD BACK → Solar Home; no warmOverlayProcess (perf).
                    if (overlayEligible) {
                        long backHoldMs = com.solar.input.policy.GlobalInputPolicy
                                .backModalHoldMsForPackage(fg);
                        mainHandler.postDelayed(backLongRunnable, backHoldMs);
                    }
                }
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("fg", fg != null ? fg : "");
                    d.put("launchSolarEligible", overlayEligible);
                    d.put("action", "arm_back_long");
                    PowerMenuDebugLog.event("SystemServerHooks.handleBackLongPress",
                            "back down tracked", "c54726-H3", d);
                } catch (Throwable ignored) {}
                // #endregion
            }
            // 2026-07-06 — Pass BACK DOWN through until modal/rescue confirms; was swallowing too early.
            return shouldConsumeBackDuringLongPress();
        }
        if (action == KeyEvent.ACTION_UP) {
            forwardHoldUpToCoordinator(ctx);
            mainHandler.removeCallbacks(backLongRunnable);
            mainHandler.removeCallbacks(backRestartRunnable);
            removeBackRescueArmCallback();
            if (!backRestartFired) {
                SolarRescueHoldClient.disarm();
            }
            if (OverlayKeyForwarder.isOverlayActiveOrOpening() && backLongFired) {
                backRestartFired = false;
                backLongFired = false;
                backOpenGestureHeld = false;
                backTracking = false;
                backDownAt = 0L;
                return true;
            }
            if (backRestartFired || backLongFired) {
                backRestartFired = false;
                backLongFired = false;
                backTracking = false;
                return true;
            }
            if (backOpenGestureHeld) {
                backOpenGestureHeld = false;
                clearBackLongPressState();
                return true;
            }
            if (backTracking && !backUpHandled) {
                backTracking = false;
                backUpHandled = true;
                long holdMs = backDownAt > 0 ? SystemClock.uptimeMillis() - backDownAt : 0L;
                backDownAt = 0L;
                // #region agent log
                try {
                    JSONObject d = new JSONObject();
                    d.put("fg", cachedBackLongFg != null ? cachedBackLongFg : "");
                    d.put("holdMs", holdMs);
                    d.put("overlayActive", OverlayKeyForwarder.isOverlayActiveOrOpening());
                    PowerMenuDebugLog.event("SystemServerHooks.handleBackLongPress",
                            "back up pass-through", "bee1b8-H-B", d);
                } catch (Throwable ignored) {}
                // #endregion
                // 2026-07-06 — Short tap: real DOWN already reached fg app; pass UP unchanged.
                if (ImeKeyForwarder.isImeActive()) {
                    dismissImeTray(ctx);
                    return true;
                }
                return false;
            }
            return shouldConsumeBackDuringLongPress();
        }
        return shouldConsumeBackDuringLongPress();
    }

    /** 2026-07-06 — Steal BACK only after modal/rescue fired or overlay mutex is armed. */
    private static boolean shouldConsumeBackDuringLongPress() {
        return backLongFired || backRestartFired || backOpenGestureHeld
                || OverlayKeyForwarder.isOverlayActiveOrOpening();
    }

    /** Long OK / center / MENU — open app options menu; AppMenuHooks redecorates the rows. */
    private static void hookCenterLongPress(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (ignoreCenterHookForInject) {
                    KeyEvent synth = findKeyEvent(param.args);
                    if (synth != null && isCenterOrMenuKey(synth.getKeyCode())) {
                        // #region agent log
                        SolarContextBridge.log("edc27b OK-SYNTH swallow action=" + synth.getAction());
                        // #endregion
                        param.setResult(KEY_CONSUMED_DISPATCH);
                        return;
                    }
                }
                if (consumeCenterHeldAfterLongOpen(param, param.args, KEY_CONSUMED_DISPATCH)) {
                    return;
                }
                absorbOverlayDisarmPulse();
                if (OverlayKeyForwarder.isOverlayActive()) return;
                // Wheel handoff injects via RootKeyInjector — pass hardware OK through unchanged.
                if (isHandoffInjectActive()) return;
                phoneWindowManagerRef = param.thisObject;
                lastDispatchArgs = param.args;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return;

                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageFromArgs(param.args, ctx, param.thisObject);
                if (shouldBypassCenterLongPressForPackage(fg)) {
                    clearCenterLongPressState();
                    return;
                }
                if (!shouldOfferOverlayForPackage(fg)) return;

                int action = event.getAction();
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        centerLongFired = false;
                        centerTracking = true;
                        centerUpHandled = false;
                        mainHandler.removeCallbacks(centerLongRunnable);
                        mainHandler.postDelayed(centerLongRunnable, CENTER_LONG_MS);
                    }
                    param.setResult(KEY_CONSUMED_DISPATCH);
                    return;
                }
                if (action == KeyEvent.ACTION_UP) {
                    mainHandler.removeCallbacks(centerLongRunnable);
                    param.setResult(KEY_CONSUMED_DISPATCH);
                    if (centerLongFired) {
                        centerLongFired = false;
                        centerTracking = false;
                        centerUpHandled = false;
                        return;
                    }
                    if (centerTracking && !centerUpHandled) {
                        centerTracking = false;
                        centerUpHandled = true;
                        injectKeyEvent(event.getKeyCode());
                    }
                }
            }
        });
        SolarContextBridge.log("hooked OK/center long-press on interceptKeyBeforeDispatching");
    }

    /** MTK Y2 queues center before dispatch — drop it while long-press detection runs. */
    private static void hookCenterBeforeQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (ignoreCenterHookForInject) {
                    KeyEvent synth = findKeyEvent(param.args);
                    if (synth != null && isCenterOrMenuKey(synth.getKeyCode())) {
                        param.setResult(KEY_CONSUMED_QUEUE);
                        return;
                    }
                }
                if (consumeCenterHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                absorbOverlayDisarmPulse();
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (isHandoffInjectActive()) return;
                if (!centerTracking && !centerLongFired) return;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || !isCenterOrMenuKey(event.getKeyCode())) return;
                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageFromArgs(param.args, ctx, param.thisObject);
                if (shouldBypassCenterLongPressForPackage(fg)) {
                    clearCenterLongPressState();
                    return;
                }
                if (!shouldOfferOverlayForPackage(fg)) {
                    clearCenterLongPressState();
                    return;
                }
                param.setResult(KEY_CONSUMED_QUEUE);
            }
        });
        SolarContextBridge.log("hooked OK/center swallow on interceptKeyBeforeQueueing");
    }

    private static volatile Class<?> sSystemPropertiesClass;
    private static Class<?> getSystemPropertiesClass() {
        Class<?> c = sSystemPropertiesClass;
        if (c == null) {
            try {
                c = XposedHelpers.findClass("android.os.SystemProperties", null);
                sSystemPropertiesClass = c;
            } catch (Throwable ignored) {}
        }
        return c;
    }

    /** True while Solar is remapping wheel/side MEDIA keys into a stock app (blocks OK re-inject). */
    private static boolean isHandoffInjectActive() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", HANDOFF_ACTIVE_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    /** MTK paths may queue BACK before dispatch — drop it while long-press detection runs. */
    private static void hookBackBeforeQueueing(Class<?> pwm) {
        XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (consumeBackHeldAfterLongOpen(param, param.args, KEY_CONSUMED_QUEUE)) {
                    return;
                }
                if (OverlayKeyForwarder.isOverlayActive()) return;
                if (ignoreBackHookForInject) return;
                // 2026-07-06 — Queue swallow only after long-press confirmed, not during detection.
                if (!backLongFired && !backRestartFired && !backOpenGestureHeld) return;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || event.getKeyCode() != KeyEvent.KEYCODE_BACK) return;
                Context ctx = resolveContext(param.thisObject);
                String fg = foregroundPackageForBackLong(param.args, ctx, param.thisObject);
                if (!shouldTrackBackUltraLong(fg)) {
                    clearBackLongPressState();
                    return;
                }
                param.setResult(KEY_CONSUMED_QUEUE);
            }
        });
        SolarContextBridge.log("hooked BACK swallow on interceptKeyBeforeQueueing");
    }

    /** Reset long-press detector when foreground leaves third-party overlay targets. */
    private static void clearBackLongPressState() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(backLongRunnable);
            mainHandler.removeCallbacks(backRestartRunnable);
            removeBackRescueArmCallback();
        }
        backLongFired = false;
        backRestartFired = false;
        backTracking = false;
        backUpHandled = false;
        backOpenGestureHeld = false;
        overlayRescueBackHeld = false;
        overlayRescuePowerHeld = false;
        overlayRescueFg = null;
        SolarRescueHoldClient.disarm();
    }

    /** Reset center/OK long-press after overlay dismiss — blocks spurious MENU inject. */
    private static void clearCenterLongPressState() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(centerLongRunnable);
        }
        centerLongFired = false;
        centerTracking = false;
        centerUpHandled = false;
    }

    /** Overlay disarm pulse from Solar — drop stale BACK/center tracking from the open gesture. */
    static void absorbOverlayDisarmPulse() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return;
            Object v = XposedHelpers.callStaticMethod(sp, "get", DISARM_PULSE_PROPERTY, "0");
            long pulse = Long.parseLong(String.valueOf(v));
            if (pulse > 0 && pulse != lastDisarmPulseSeen) {
                lastDisarmPulseSeen = pulse;
                clearBackLongPressState();
                clearCenterLongPressState();
                SolarContextBridge.log("overlay disarm pulse cleared long-press state");
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 2026-07-06 — Opening-gesture BACK still held — swallow; lift-off UP must not dismiss modal.
     * Power-hold opens without BACK tracking so this stays false for that path.
     */
    static boolean shouldBlockBackForwardToOverlay() {
        return backLongFired || backOpenGestureHeld;
    }

    /**
     * OK/MENU long-press still held after app menu opened — do not forward repeats to overlay.
     */
    static boolean shouldBlockCenterForwardToOverlay() {
        return centerLongFired
                || SystemClock.uptimeMillis() < centerMenuOpenGraceUntil;
    }

    /** True briefly after global modal dismiss — swallow BACK so stock apps never see dismiss gesture. */
    static boolean isPostOverlayCooldown() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", COOLDOWN_UNTIL_PROPERTY, "0");
            long until = Long.parseLong(String.valueOf(v));
            return until > 0 && SystemClock.uptimeMillis() < until;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isCenterOrMenuKey(int keyCode) {
        return Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU;
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof KeyEvent) return (KeyEvent) arg;
        }
        return null;
    }

    /** Post synthetic short BACK after hardware UP — avoids re-entering long-press swallow on inject. */
    private static void scheduleBackInject() {
        if (mainHandler == null) {
            injectKeyEvent(KeyEvent.KEYCODE_BACK);
            return;
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                injectKeyEvent(KeyEvent.KEYCODE_BACK);
            }
        }, BACK_INJECT_DELAY_MS);
    }

    /**
     * 2026-07-05 — inject wheel-remapped DPAD for stock Holo ANR buttons (AnrDialogKeyForwarder).
     * Reversal: remove AnrDialogKeyForwarder.install — this entry unused.
     */
    static void injectDpadTap(int dpadKeyCode) {
        injectKeyEvent(dpadKeyCode);
    }

    /** Inject a short tap so apps still get quick BACK / OK after we swallowed the real key. */
    private static void injectKeyEvent(int keyCode) {
        // Post-overlay cooldown blocks MENU/OK inject only — short BACK must still reach apps/dialogs.
        if (isPostOverlayCooldown() && keyCode != KeyEvent.KEYCODE_BACK) return;
        if (OverlayKeyForwarder.isOverlayActive() && keyCode != KeyEvent.KEYCODE_BACK) return;
        boolean centerLike = isCenterOrMenuKey(keyCode);
        boolean backLike = keyCode == KeyEvent.KEYCODE_BACK;
        if (centerLike) {
            ignoreCenterHookForInject = true;
        }
        if (backLike) {
            ignoreBackHookForInject = true;
        }
        try {
            Class<?> imClass = XposedHelpers.findClass("android.hardware.input.InputManager", null);
            Object im = XposedHelpers.callStaticMethod(imClass, "getInstance");
            long now = SystemClock.uptimeMillis();
            KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
            KeyEvent up = new KeyEvent(now, now + 10, KeyEvent.ACTION_UP, keyCode, 0);
            // #region agent log
            if (centerLike) {
                SolarContextBridge.log("edc27b OK-INJECT code=" + keyCode);
            }
            if (backLike) {
                SolarContextBridge.log("edc27b BACK-INJECT code=" + keyCode);
            }
            // #endregion
            XposedHelpers.callMethod(im, "injectInputEvent", down, INJECT_MODE_ASYNC);
            XposedHelpers.callMethod(im, "injectInputEvent", up, INJECT_MODE_ASYNC);
        } catch (Throwable t) {
            SolarContextBridge.log("injectKey failed code=" + keyCode + ": " + t.getClass().getSimpleName());
            if (centerLike) {
                ignoreCenterHookForInject = false;
            }
            if (backLike) {
                ignoreBackHookForInject = false;
            }
        }
        if (centerLike || backLike) {
            scheduleInjectGuardClear(centerLike, backLike);
        }
    }

    /** Clear inject re-entry guards after async inject — must not run on PWM thread synchronously. */
    private static void scheduleInjectGuardClear(final boolean centerLike, final boolean backLike) {
        if (mainHandler == null) {
            if (centerLike) ignoreCenterHookForInject = false;
            if (backLike) ignoreBackHookForInject = false;
            return;
        }
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (centerLike) ignoreCenterHookForInject = false;
                if (backLike) ignoreBackHookForInject = false;
            }
        }, INJECT_GUARD_CLEAR_MS);
    }

    /** Walk ActivityThread.mActivities for the resumed Activity — openOptionsMenu triggers MenuDialogHelper. */
    @SuppressWarnings("unchecked")
    private static boolean openOptionsMenuForForeground(Context ctx) {
        try {
            Class<?> atm = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object thread = XposedHelpers.callStaticMethod(atm, "currentActivityThread");
            if (thread == null) return false;
            Object mapObj = XposedHelpers.getObjectField(thread, "mActivities");
            if (!(mapObj instanceof Map)) return false;
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            for (Object record : map.values()) {
                if (record == null) continue;
                Object paused = XposedHelpers.getObjectField(record, "paused");
                if (Boolean.TRUE.equals(paused)) continue;
                Object activity = XposedHelpers.getObjectField(record, "activity");
                if (activity == null) continue;
                XposedHelpers.callMethod(activity, "openOptionsMenu");
                return true;
            }
        } catch (Throwable t) {
            SolarContextBridge.log("openOptionsMenu failed: " + t.getClass().getSimpleName());
        }
        return false;
    }

    /**
     * 2026-07-07 — OK-long on focused list row / button — performLongClick opens Holo context menu;
     * AppMenuHooks intercepts MenuPopupHelper.show and paints Solar overlay rows.
     * Reversal: delete — center-long falls back to openOptionsMenu / MENU inject only.
     */
    @SuppressWarnings("unchecked")
    private static boolean openContextMenuForFocusedView(Context ctx) {
        try {
            Class<?> atm = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object thread = XposedHelpers.callStaticMethod(atm, "currentActivityThread");
            if (thread == null) return false;
            Object mapObj = XposedHelpers.getObjectField(thread, "mActivities");
            if (!(mapObj instanceof Map)) return false;
            Map<Object, Object> map = (Map<Object, Object>) mapObj;
            for (Object record : map.values()) {
                if (record == null) continue;
                Object paused = XposedHelpers.getObjectField(record, "paused");
                if (Boolean.TRUE.equals(paused)) continue;
                Object activity = XposedHelpers.getObjectField(record, "activity");
                if (activity == null) continue;
                Object window = XposedHelpers.callMethod(activity, "getWindow");
                if (window == null) continue;
                Object decor = XposedHelpers.callMethod(window, "getDecorView");
                if (decor == null) continue;
                Object focus = XposedHelpers.callMethod(decor, "findFocus");
                if (focus == null) {
                    focus = decor;
                }
                Object result = XposedHelpers.callMethod(focus, "performLongClick");
                if (Boolean.TRUE.equals(result)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            SolarContextBridge.log("performLongClick failed: " + t.getClass().getSimpleName());
        }
        return false;
    }

    static Context resolveContext(Object hookThis) {
        if (hookThis != null) {
            try {
                Object ctx = XposedHelpers.getObjectField(hookThis, "mContext");
                if (ctx instanceof Context) return (Context) ctx;
            } catch (Throwable ignored) {}
        }
        return currentContext();
    }

    private static void showSolarPowerOverlay(Object hookThis) {
        Context ctx = resolveContext(hookThis);
        if (ctx == null) {
            ctx = currentContext();
        }
        if (ctx == null) {
            SolarContextBridge.log("showLauncherPicker: no context");
            return;
        }
        SolarContextBridge.log("showPowerOverlay ctx=" + ctx.getPackageName());
        SolarOverlayClient.showPowerOverlay(ctx);
    }

    static Context currentContext() {
        try {
            return (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Foreground for BACK-long eligibility — window focus + running tasks only.
     * No process-list fallback (returns systemui/android and breaks short BACK inject).
     * When this returns null, pass hardware BACK through; root evdev daemon handles long-press.
     */
    private static String foregroundPackageForBackLong(Object[] args, Context ctx, Object pwm) {
        if (args != null) {
            for (Object arg : args) {
                String pkg = packageFromWindowState(arg);
                if (pkg != null) return pkg;
            }
        }
        String pkg = foregroundPackageFromPwm(pwm);
        if (pkg != null) return pkg;
        return foregroundPackageFromTasks(ctx);
    }

    /** RunningTasks only — used for BACK-long; avoids noisy getRunningAppProcesses. */
    @SuppressWarnings("deprecation")
    private static String foregroundPackageFromTasks(Context ctx) {
        if (ctx == null) return null;
        try {
            Object am = ctx.getSystemService("activity");
            if (am == null) return null;
            List<?> tasks = (List<?>) XposedHelpers.callMethod(am, "getRunningTasks", 1);
            if (tasks == null || tasks.isEmpty()) return null;
            Object task = tasks.get(0);
            Object base = XposedHelpers.callMethod(task, "baseActivity");
            if (base == null) base = XposedHelpers.callMethod(task, "topActivity");
            if (base == null) return null;
            return (String) XposedHelpers.callMethod(base, "getPackageName");
        } catch (Throwable t) {
            return null;
        }
    }

    /** Prefer focused WindowState / PWM focus — fall back to tasks then foreground processes (KitKat). */
    private static String foregroundPackageFromArgs(Object[] args, Context ctx, Object pwm) {
        String pkg = foregroundPackageForBackLong(args, ctx, pwm);
        if (pkg != null) return pkg;
        return foregroundPackage(ctx);
    }

    /** Read package from a WindowState arg (dispatch hook) or mFocusedWindow (queueing hook). */
    private static String packageFromWindowState(Object winState) {
        if (winState == null) return null;
        try {
            String pkg = (String) XposedHelpers.callMethod(winState, "getOwningPackage");
            if (pkg != null && pkg.length() > 0) return pkg;
        } catch (Throwable ignored) {}
        try {
            Object attrs = XposedHelpers.getObjectField(winState, "mAttrs");
            if (attrs != null) {
                String pkg = (String) XposedHelpers.getObjectField(attrs, "packageName");
                if (pkg != null && pkg.length() > 0) return pkg;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** PhoneWindowManager focus window — queueing hook args are often KeyEvent-only. */
    private static String foregroundPackageFromPwm(Object pwm) {
        if (pwm == null) return null;
        String[] focusFields = {
                "mFocusedWindow",
                "mTopFullscreenOpaqueWindowState",
                "mTopFullscreenOpaqueOrDimmingWindowState"
        };
        for (String field : focusFields) {
            try {
                Object win = XposedHelpers.getObjectField(pwm, field);
                String pkg = packageFromWindowState(win);
                if (pkg != null) return pkg;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    static String foregroundPackage(Context ctx) {
        long t0 = System.nanoTime();
        String pkg = null;
        if (ctx == null) return null;
        try {
            Object am = ctx.getSystemService("activity");
            if (am == null) return null;
            List<?> tasks = (List<?>) XposedHelpers.callMethod(am, "getRunningTasks", 1);
            if (tasks != null && !tasks.isEmpty()) {
                Object task = tasks.get(0);
                Object base = XposedHelpers.callMethod(task, "baseActivity");
                if (base == null) base = XposedHelpers.callMethod(task, "topActivity");
                if (base != null) {
                    pkg = (String) XposedHelpers.callMethod(base, "getPackageName");
                    if (pkg != null) return pkg;
                }
            }
            // getRunningTasks often empty on KitKat from system_server — use foreground process list.
            List<?> procs = (List<?>) XposedHelpers.callMethod(am, "getRunningAppProcesses");
            if (procs != null) {
                for (Object proc : procs) {
                    if (proc == null) continue;
                    int importance = (Integer) XposedHelpers.getObjectField(proc, "importance");
                    if (importance != 100) continue; // IMPORTANCE_FOREGROUND
                    String[] pkgs = (String[]) XposedHelpers.getObjectField(proc, "pkgList");
                    if (pkgs == null) continue;
                    for (String candidate : pkgs) {
                        if (candidate != null && candidate.length() > 0
                                && !isSystemShellPackage(candidate)) {
                            pkg = candidate;
                            return pkg;
                        }
                    }
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            // #region agent log
            try {
                JSONObject d = new JSONObject();
                d.put("pkg", pkg != null ? pkg : "");
                BridgeAnrDebugLog.hookTiming("SystemServerHooks.foregroundPackage", "A", t0, d);
            } catch (Throwable ignored) {}
            // #endregion
        }
    }

    static boolean isSolarForeground(Context ctx) {
        return "com.solar.launcher".equals(foregroundPackage(ctx));
    }

    /**
     * BACK-long and center-long eligibility — Rockbox excluded on Y1 and Y2 (BACK stays in Rockbox).
     */
    /** BACK-long / app-menu long-OK — delegates to shared GlobalInputPolicy JAR. */
    static boolean shouldOfferBackLongOverlayForPackage(String pkg) {
        return com.solar.input.policy.GlobalInputPolicy.shouldOfferBackLongModal(
                pkg, allowRockboxPowerLongOverlay, ImeKeyForwarder.isImeActive(),
                isEmergencyMode());
    }

    /** 2026-07-06 — Companion sets persist.solar.emergency_mode after crash loop. */
    private static boolean isEmergencyMode() {
        try {
            Class<?> sp = de.robv.android.xposed.XposedHelpers.findClass(
                    "android.os.SystemProperties", null);
            Object v = de.robv.android.xposed.XposedHelpers.callStaticMethod(
                    sp, "get", "persist.solar.emergency_mode", "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    /** @deprecated use {@link #shouldOfferBackLongOverlayForPackage} */
    static boolean shouldOfferOverlayForPackage(String pkg) {
        return shouldOfferBackLongOverlayForPackage(pkg);
    }

    /** IME + ultra-long trackers — ImeKeyForwarder must not swallow BACK/power during hold. */
    static boolean isBackUltraLongTracking() {
        return backTracking;
    }

    /**
     * Y2 power-hold eligibility — Rockbox allowed when {@link #installY2} set the flag.
     * Mirrors {@code GlobalOverlayPolicy#shouldOfferPowerLongGlobalModalForPackage}.
     */
    static boolean shouldOfferPowerLongOverlayForPackage(String pkg) {
        return com.solar.input.policy.GlobalInputPolicy.shouldOfferPowerLongModal(
                pkg, allowRockboxPowerLongOverlay);
    }

    /** JJ owns short OK natively; overlay/system menu interception should not delay center presses there. */
    static boolean shouldBypassCenterLongPressForPackage(String pkg) {
        return com.solar.input.policy.GlobalInputPolicy.JJ_PKG.equals(pkg);
    }

    /** Ultra-long BACK restart — any fg except Solar (in-app handler); bare WM + shells OK. */
    static boolean shouldTrackBackUltraLong(String pkg) {
        if ("com.solar.launcher".equals(pkg)) return false;
        return true;
    }

    /**
     * 2026-07-05 — Y2 power 10s rescue — includes Solar foreground (MainActivity passes POWER to PWM).
     * Layman: hold sleep button in Solar also triggers restart countdown.
     */
    static boolean shouldTrackPowerUltraLong(String pkg) {
        return shouldTrackBackUltraLong(pkg) || "com.solar.launcher".equals(pkg);
    }

    /** Process-list noise — never treat as third-party app for BACK-long intercept. */
    private static boolean isSystemShellPackage(String pkg) {
        if (pkg == null || pkg.length() == 0) return true;
        if ("android".equals(pkg)) return true;
        if (pkg.startsWith("com.android.systemui")) return true;
        if (pkg.startsWith("com.android.keyguard")) return true;
        if (pkg.startsWith("com.android.inputmethod")) return true;
        return false;
    }
}
