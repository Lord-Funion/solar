package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — Tier-1 overlay key forward while sys.solar.overlay.active=1.
 * Mutex: stemmix > overlay — skip when Stem/Mix jam owns pads (sys.solar.stemmix.active=1).
 * Read-only consumer of OverlayKeyGate props; WM overlay is NOT_FOCUSABLE — keys flow through here.
 * When changing: OPENING_STALE_MS must match OverlayKeyGate; disarm pulse clears stale BACK holds.
 * Reversal: remove PWM hook; global quick menu keys leak to foreground app.
 */
final class OverlayKeyForwarder {

    /** Must match {@link com.solar.launcher.OverlayKeyGate#ACTIVE_PROPERTY}. */
    static final String ACTIVE_PROPERTY = "sys.solar.overlay.active";
    /** Must match {@link com.solar.launcher.StemOrMixSession#ACTIVE_PROPERTY}. 2026-07-19 */
    static final String STEMMIX_ACTIVE_PROPERTY = "sys.solar.stemmix.active";
    /** Must match {@link com.solar.launcher.OverlayKeyGate#OPENING_PROPERTY}. */
    static final String OPENING_PROPERTY = "sys.solar.overlay.opening";
    /** Elapsed-realtime ms when opening=1 was set — stale via shared StaleOverlayGate JAR. */
    static final String OPENING_AT_PROPERTY = "sys.solar.overlay.opening_at";
    /** Legacy persist prop — auto-cleared by Solar; never swallow Rockbox when this alone is set. */
    private static final String LEGACY_ACTIVE_PROPERTY = "persist.solar.overlay.active";
    static final String ACTION_OVERLAY_KEY = "com.solar.launcher.action.OVERLAY_KEY";
    static final String EXTRA_KEY_CODE = "overlay_key_code";
    static final String EXTRA_KEY_ACTION = "overlay_key_action";
    private static final String SOLAR_PKG = "com.solar.launcher";
    private static final String KEY_RECEIVER = SOLAR_PKG + ".OverlayKeyReceiver";
    private static final String OVERLAY_SERVICE = SOLAR_PKG + ".SolarOverlayService";
    /** 2026-07-14 — Solar is the one shell; Chip keys only if companion_shell=1. */
    private static final String COMPANION_PKG = "com.solar.launcher.globalcontext";
    private static final String COMPANION_OVERLAY = COMPANION_PKG + ".GlobalContextOverlayService";
    private static final String LEGACY_SHELL_PROP = "persist.solar.overlay.legacy_shell";
    /** 2026-07-08 — Debounce clock for stuck-shell DISMISS (uptime ms). */
    private static volatile long lastStuckShellDismissAt;

    private OverlayKeyForwarder() {}

    static void hookPhoneWindowManager(Class<?> pwm) {
        XC_MethodHook overlayHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleOverlayKeyIntercept(param, false);
            }
        };
        XC_MethodHook queueHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                handleOverlayKeyIntercept(param, true);
            }
        };
        try {
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", overlayHook);
            // MTK Y1/Y2 often queue wheel/back before dispatch — forward from both hooks.
            XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", queueHook);
            SolarContextBridge.log("hooked overlay key capture on PhoneWindowManager");
        } catch (Throwable t) {
            SolarContextBridge.log("overlay key hook failed: " + t);
        }
    }

    /**
     * 2026-07-08 — Ghost shell heal: short BACK dismisses stuck WM menu (gate disarmed).
     * Layman: Back closes the stuck global menu — it must not open a second one.
     * Technical: DISMISS on DOWN; consume whole BACK gesture while shell_visible=1 + capture off.
     * Was: UP-only heal that never consumed — DOWN still armed HOLD → openPowerOverlay.
     * Reversal: return false always; PWM back-long path owns short BACK again.
     *
     * @return true when BACK must be swallowed (caller setResult / return true).
     */
    static boolean maybeHealStuckShellOnBack(Context ctx, int keyCode, int action) {
        if (ctx == null) return false;
        if (!Y1InputKeysBridge.isBackKey(keyCode)) return false;
        boolean cooldown = SystemServerHooks.isPostOverlayCooldown();
        boolean shellVisible = com.solar.input.policy.StaleOverlayGate.isShellVisible();
        // Capture already known unarmed at call sites — keep false so heal can arm.
        boolean consume = com.solar.input.policy.StaleOverlayGate.shouldConsumeStuckShellBack(
                false, cooldown, shellVisible);
        if (!consume) return false;
        long now = SystemClock.uptimeMillis();
        if (com.solar.input.policy.StaleOverlayGate.shouldDismissStuckShellOnBack(
                false, cooldown, shellVisible, action, now, lastStuckShellDismissAt)) {
            lastStuckShellDismissAt = now;
            SolarContextBridge.log("edc27b stuck-shell BACK dismiss action=" + action);
            SystemServerHooks.dismissAnyOverlay(ctx);
        } else {
            SolarContextBridge.log("edc27b stuck-shell BACK swallow action=" + action);
        }
        return true;
    }

    /** Shared intercept — swallow + forward so NOT_FOCUSABLE overlay receives wheel/back/center. */
    private static void handleOverlayKeyIntercept(XC_MethodHook.MethodHookParam param, boolean queueing) {
        long t0 = System.nanoTime();
        boolean forwarded = false;
        int keyCode = 0;
        try {
        // Stem/Mix jam owns pads — do not steal keys for overlay. 2026-07-19
        if (isStemMixActive()) return;
        KeyEvent event = findKeyEvent(param.args);
        // Hardware volume always goes to AudioService first — passive HUD only refreshes via hooks.
        if (event != null && isHardwareVolumeKey(event.getKeyCode())) {
            return;
        }
        // Overlay dismisses on BACK down — swallow BACK until cooldown ends so app never navigates back.
        if (event != null && Y1InputKeysBridge.isBackKey(event.getKeyCode())
                && SystemServerHooks.isPostOverlayCooldown()) {
            SolarContextBridge.log("edc27b swallow post-dismiss BACK action=" + event.getAction());
            param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            return;
        }
        if (!isOverlayKeyCaptureArmed()) {
            if (!queueing) {
                SystemServerHooks.absorbOverlayDisarmPulse();
            }
            // 2026-07-08 — Unarmed + shell_visible: short BACK dismisses only (consume gesture).
            if (event != null) {
                Context healCtx = SystemServerHooks.resolveContext(param.thisObject);
                if (maybeHealStuckShellOnBack(healCtx, event.getKeyCode(), event.getAction())) {
                    param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
                }
            }
            return;
        }
        if (event == null) return;
        Context ctx = SystemServerHooks.resolveContext(param.thisObject);
        if (!shouldCaptureOverlayKeys(null)) {
            // #region agent log
            SolarContextBridge.log("edc27b skip capture key=" + event.getKeyCode());
            // #endregion
            return;
        }
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return;
        }
        keyCode = event.getKeyCode();
        // 2026-07-10 — Wheel: deliver once from BeforeQueueing only.
        // Dispatch path still swallows so the fg app never sees the key, but does not
        // startService again (was dual-forward + 45ms dedupe → dropped rapid scroll ticks).
        // Reversal: remove this branch if a single-hook PWM path is guaranteed.
        if (Y1InputKeysBridge.isWheelKey(keyCode) && !queueing) {
            param.setResult(KEY_CONSUMED_DISPATCH);
            return;
        }
        // Back always goes to overlay dismiss handler — except while open-gesture finger is still down.
        if (Y1InputKeysBridge.isBackKey(keyCode)) {
            if (SystemServerHooks.shouldBlockBackForwardToOverlay()) {
                // #region agent log
                SolarContextBridge.log("edc27b BACK-OPEN-HOLD swallow action=" + action
                        + " queue=" + queueing);
                // #endregion
                param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
                return;
            }
            if (ctx != null) {
                String fg = null;
                SystemServerHooks.trackOverlayRescueHold(ctx, event, fg);
                forwardKey(ctx, keyCode, action);
                forwarded = true;
            }
            param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
            return;
        }
        if (!shouldForwardOverlayKey(keyCode, action, event)) {
            return;
        }
        if (Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU) {
            if (SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
                // #region agent log
                SolarContextBridge.log("edc27b OK-OPEN-HOLD swallow action=" + action
                        + " queue=" + queueing);
                // #endregion
                param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
                return;
            }
        }
        if (ctx != null) {
            forwardKey(ctx, keyCode, action);
            forwarded = true;
        }
        param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
        } finally {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("queueing", queueing);
                d.put("forwarded", forwarded);
                d.put("keyCode", keyCode);
                d.put("overlayActive", isOverlayActive());
                BridgeAnrDebugLog.hookTiming("OverlayKeyForwarder.handleOverlayKeyIntercept", "B", t0, d);
            } catch (Throwable ignored) {}
            // #endregion
        }
    }

    /**
     * App-process hook entry — used when PWM misses keys (Dialog focus, MTK media-key routing).
     * @return true when the foreground app/dialog must not see this key.
     */
    static boolean tryForwardFromAppContext(Context ctx, KeyEvent event) {
        if (event == null) return false;
        if (!isOverlayKeyCaptureArmed()) {
            if (Y1InputKeysBridge.isBackKey(event.getKeyCode())
                    && SystemServerHooks.isPostOverlayCooldown()) {
                return true;
            }
            // 2026-07-08 — App got BACK while ghost shell up — dismiss + swallow (no new open).
            return maybeHealStuckShellOnBack(ctx, event.getKeyCode(), event.getAction());
        }
        if (Y1InputKeysBridge.isBackKey(event.getKeyCode())
                && SystemServerHooks.shouldBlockBackForwardToOverlay()) {
            return true;
        }
        if ((Y1InputKeysBridge.isCenterKey(event.getKeyCode())
                || event.getKeyCode() == KeyEvent.KEYCODE_MENU)
                && SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
            return true;
        }
        return tryForwardFromAppContext(ctx, event.getKeyCode(), event.getAction(), event.getRepeatCount());
    }

    /** Primitive keyCode/action — for onKeyDown/onKeyUp hooks without a full KeyEvent. */
    static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action) {
        return tryForwardFromAppContext(ctx, keyCode, action, 0);
    }

    /** keyCode/action with repeat count — skips OK repeat storms while overlay is up. */
    static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action, int repeatCount) {
        if (ctx == null) return false;
        if (!isOverlayKeyCaptureArmed()) {
            // Dismiss uses BACK down — block the matching up/repeat from reaching the stock Activity.
            if (Y1InputKeysBridge.isBackKey(keyCode) && SystemServerHooks.isPostOverlayCooldown()) {
                return true;
            }
            // 2026-07-08 — Unarmed app path; dismiss stuck shell and swallow short BACK.
            return maybeHealStuckShellOnBack(ctx, keyCode, action);
        }
        String pkg = null;
        try {
            pkg = ctx.getPackageName();
        } catch (Throwable ignored) {}
        if (pkg == null || pkg.length() == 0) return false;
        if (!shouldCaptureOverlayKeys(pkg)) return false;
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return false;
        }
        if (Y1InputKeysBridge.isBackKey(keyCode)
                && SystemServerHooks.shouldBlockBackForwardToOverlay()) {
            return true;
        }
        if ((Y1InputKeysBridge.isCenterKey(keyCode) || keyCode == KeyEvent.KEYCODE_MENU)
                && SystemServerHooks.shouldBlockCenterForwardToOverlay()) {
            return true;
        }
        if (action == KeyEvent.ACTION_UP && Y1InputKeysBridge.isWheelKey(keyCode)) {
            // #region agent log
            SolarContextBridge.log("edc27b app consume wheel up key=" + keyCode + " pkg=" + pkg);
            // #endregion
            return true;
        }
        if (!shouldForwardOverlayKey(keyCode, action, repeatCount)) {
            return false;
        }
        forwardKey(ctx, keyCode, action);
        // #region agent log
        SolarContextBridge.log("edc27b app forward key=" + keyCode + " action=" + action + " pkg=" + pkg);
        // #endregion
        return true;
    }

    /** Whether this key event should be forwarded to Solar overlay service. */
    private static boolean shouldForwardOverlayKey(int keyCode, int action, KeyEvent event) {
        int repeat = event != null ? event.getRepeatCount() : 0;
        return shouldForwardOverlayKey(keyCode, action, repeat);
    }

    private static boolean shouldForwardOverlayKey(int keyCode, int action, int repeatCount) {
        if (Y1InputKeysBridge.isBackKey(keyCode)) return true;
        if (action == KeyEvent.ACTION_DOWN && repeatCount > 0) {
            if (Y1InputKeysBridge.isCenterKey(keyCode) || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                    || keyCode == KeyEvent.KEYCODE_MENU) {
                return false;
            }
        }
        if (action == KeyEvent.ACTION_UP) {
            return isOverlayNavigationKeyUp(keyCode);
        }
        return isOverlayNavigationKey(keyCode);
    }

    private static boolean shouldCaptureOverlayKeys(String fg) {
        return true;
    }

    /**
     * 2026-07-11 — Capture when any live overlay signal is set.
     * Was: only active||opening — Y2 often fails app setprop for active while ui/shell stick
     * (or vice versa) after Power-hold paints companion → wheel never forwarded.
     * Now: active OR opening OR ui OR shell_visible.
     * Reversal: return isOverlayOpening() || isOverlayActive() only.
     */
    static boolean isOverlayKeyCaptureArmed() {
        clearOpeningGateIfStale();
        return isOverlayOpening() || isOverlayActive() || isOverlayUiVisible()
                || isShellVisible();
    }

    private static final String UI_PROPERTY = "sys.solar.overlay.ui";
    private static final String SHELL_VISIBLE_PROPERTY = "sys.solar.overlay.shell_visible";

    static boolean isOverlayUiVisible() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", UI_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    /** WM shell attached — companion writes this at addView even when active lags. */
    static boolean isShellVisible() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", SHELL_VISIBLE_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    private static final long KEY_CONSUMED_DISPATCH = -1L;
    private static final int KEY_CONSUMED_QUEUE = 0;

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

    static boolean isOverlayActive() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", ACTIVE_PROPERTY, "0");
            if ("1".equals(String.valueOf(v))) return true;
            // Legacy stuck persist — treat as inactive so Rockbox keeps back/OK.
            Object legacy = XposedHelpers.callStaticMethod(sp, "get", LEGACY_ACTIVE_PROPERTY, "0");
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Clear opening=1 when :overlay died mid-load — restores Y2 power-hold over Rockbox. */
    static void clearOpeningGateIfStale() {
        com.solar.input.policy.StaleOverlayGate.clearIfNeeded();
    }

    /** True while :overlay paints — duplicate power/BACK triggers must not restart load. */
    static boolean isOverlayOpening() {
        clearOpeningGateIfStale();
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", OPENING_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean isOverlayActiveOrOpening() {
        return com.solar.input.policy.StaleOverlayGate.isActiveOrOpening();
    }

    /** True while Stem/Mix jam published sys.solar.stemmix.active=1. 2026-07-19 */
    static boolean isStemMixActive() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", STEMMIX_ACTIVE_PROPERTY, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable th) {
            return false;
        }
    }

    private static boolean shouldForwardOverlayKeyFastPath(int keyCode) {
        // ponytail: wheel events are high-frequency and startService causes heavy IPC/CPU overhead; use broadcast-only.
        return Y1InputKeysBridge.isWheelKey(keyCode);
    }

    /**
     * 2026-07-14 — Forward keys to the one shell (Solar ThemedContextMenu by default).
     * Layman: wheel talks to the same themed menu as Solar Home.
     * Was: companion Chip primary unless legacy_shell=1.
     * Reversal: readLegacyShellProp default "0" + companion branch first.
     */
    private static void forwardKey(Context ctx, int keyCode, int action) {
        if (ctx == null) return;
        // companion_shell=1 → chip path; otherwise Solar OverlayKeyReceiver / SolarOverlayService.
        boolean companion = "1".equals(readCompanionShellProp())
                && !"1".equals(readLegacyShellProp());
        if (companion) {
            Intent bcast = new Intent(ACTION_OVERLAY_KEY);
            bcast.setPackage(COMPANION_PKG);
            bcast.putExtra(EXTRA_KEY_CODE, keyCode);
            bcast.putExtra(EXTRA_KEY_ACTION, action);
            bcast.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            try {
                ctx.sendBroadcast(bcast);
            } catch (Throwable t) {
                SolarContextBridge.log("edc27b companion key broadcast failed key=" + keyCode);
            }
            if (!Y1InputKeysBridge.isWheelKey(keyCode)) {
                try {
                    Intent svc = new Intent(ACTION_OVERLAY_KEY);
                    svc.setComponent(new ComponentName(COMPANION_PKG, COMPANION_OVERLAY));
                    svc.putExtra(EXTRA_KEY_CODE, keyCode);
                    svc.putExtra(EXTRA_KEY_ACTION, action);
                    ctx.startService(svc);
                } catch (Throwable ignored) {}
            }
            return;
        }
        Intent intent = new Intent(ACTION_OVERLAY_KEY);
        intent.setComponent(new ComponentName(SOLAR_PKG, KEY_RECEIVER));
        intent.putExtra(EXTRA_KEY_CODE, keyCode);
        intent.putExtra(EXTRA_KEY_ACTION, action);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        try {
            ctx.sendBroadcast(intent);
            return;
        } catch (Throwable t) {
            if (shouldForwardOverlayKeyFastPath(keyCode)) return;
            try {
                Intent svc = new Intent(ACTION_OVERLAY_KEY);
                svc.setComponent(new ComponentName(SOLAR_PKG, OVERLAY_SERVICE));
                svc.putExtra(EXTRA_KEY_CODE, keyCode);
                svc.putExtra(EXTRA_KEY_ACTION, action);
                ctx.startService(svc);
            } catch (Throwable ignored) {}
        }
    }

    /** Default "0" — match OverlayShellRouter; was "1" which blocked companion_shell opt-in. */
    private static String readLegacyShellProp() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return "0";
            Object v = XposedHelpers.callStaticMethod(sp, "get", LEGACY_SHELL_PROP, "0");
            return v != null ? String.valueOf(v) : "0";
        } catch (Throwable t) {
            return "0";
        }
    }

    private static String readCompanionShellProp() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return "0";
            Object v = XposedHelpers.callStaticMethod(sp, "get",
                    "persist.solar.overlay.companion_shell", "0");
            return v != null ? String.valueOf(v) : "0";
        } catch (Throwable t) {
            return "0";
        }
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof KeyEvent) return (KeyEvent) arg;
        }
        return null;
    }

    /** Wheel, center/OK, side skip, dpad horizontal, volume, play/pause while overlay is up. */
    private static boolean isOverlayNavigationKey(int keyCode) {
        return Y1InputKeysBridge.isWheelKey(keyCode)
                || Y1InputKeysBridge.isCenterKey(keyCode)
                || Y1InputKeysBridge.isTrackSkipKey(keyCode)
                || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    }

    /** KEY_UP for center, play/pause, and side skip — consume so foreground app does not act on release. */
    private static boolean isOverlayNavigationKeyUp(int keyCode) {
        return Y1InputKeysBridge.isCenterKey(keyCode)
                || Y1InputKeysBridge.isPlayPauseKey(keyCode)
                || Y1InputKeysBridge.isTrackSkipKey(keyCode);
    }

    /** Dedicated volume buttons — never steal from AudioService (passive HUD syncs via VolumePanelHooks). */
    private static boolean isHardwareVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == 160 || keyCode == 161;
    }
}
