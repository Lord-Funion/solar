package com.solar.launcher.xposed.bridge;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * 2026-07-05 — Tier-2 Xposed PWM: forwards wheel/OK/back to SolarInputMethodService when IME active.
 * Read-only consumer of sys.solar.ime.*; escalates on miss via xposed_miss pulse for root daemon.
 * When changing: property names must match SolarImeRouteArbiter; never write ime props from here.
 * Reversal: remove install call in SystemServerHooks; wheel keys reach foreground app during IME.
 */
final class ImeKeyForwarder {

  /** Must match {@link com.solar.launcher.SolarImeRouteArbiter#ACTIVE_PROPERTY}. */
  static final String ACTIVE_PROPERTY = "sys.solar.ime.active";
  static final String ACTION_IME_KEY = "com.solar.launcher.action.IME_KEY";
  static final String EXTRA_KEY_CODE = "overlay_key_code";
  static final String EXTRA_KEY_ACTION = "overlay_key_action";
  private static final String SOLAR_PKG = "com.solar.launcher";
  private static final String IME_SERVICE = SOLAR_PKG + ".SolarInputMethodService";

  /** Must match {@link com.solar.launcher.SolarImeRouteArbiter#XPOSED_MISS_PROPERTY}. */
  private static final String XPOSED_MISS_PROPERTY = "sys.solar.ime.xposed_miss";

  static void hookPhoneWindowManager(Class<?> pwm) {
    XC_MethodHook imeHook = new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param) {
        handleImeKeyIntercept(param, false);
      }
    };
    XC_MethodHook queueHook = new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param) {
        handleImeKeyIntercept(param, true);
      }
    };
    try {
      XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", imeHook);
      XposedHookKit.hookAll(pwm, "interceptKeyBeforeQueueing", queueHook);
      SolarContextBridge.log("hooked IME key capture on PhoneWindowManager");
    } catch (Throwable t) {
      SolarContextBridge.log("IME key hook failed: " + t);
    }
  }

  private static void handleImeKeyIntercept(XC_MethodHook.MethodHookParam param, boolean queueing) {
    // Stem/Mix jam owns pads — IME must not steal wheel/OK. 2026-07-19
    if (OverlayKeyForwarder.isStemMixActive()) return;
    if (OverlayKeyForwarder.isOverlayActiveOrOpening()) return;
    if (!isImeActive()) return;
    KeyEvent event = findKeyEvent(param.args);
    if (event == null) return;
    if (isHardwareVolumeKey(event.getKeyCode())) return;
    int action = event.getAction();
    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return;
    int keyCode = event.getKeyCode();
    if (!shouldForwardImeKey(keyCode, action, event.getRepeatCount())) return;
    Context ctx = SystemServerHooks.resolveContext(param.thisObject);
    if (ctx != null) {
      forwardKey(ctx, keyCode, action);
    } else {
      pulseXposedMiss();
    }
    param.setResult(queueing ? KEY_CONSUMED_QUEUE : KEY_CONSUMED_DISPATCH);
  }

  static boolean tryForwardFromAppContext(Context ctx, KeyEvent event) {
    if (event == null || OverlayKeyForwarder.isStemMixActive()) return false;
    if (OverlayKeyForwarder.isOverlayActiveOrOpening()) return false;
    if (!isImeActive()) return false;
    return tryForwardFromAppContext(ctx, event.getKeyCode(), event.getAction(), event.getRepeatCount());
  }

  static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action) {
    return tryForwardFromAppContext(ctx, keyCode, action, 0);
  }

  static boolean tryForwardFromAppContext(Context ctx, int keyCode, int action, int repeatCount) {
    if (ctx == null || !isImeActive()) return false;
    if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false;
    if (!shouldForwardImeKey(keyCode, action, repeatCount)) return false;
    forwardKey(ctx, keyCode, action);
    return true;
  }

  private static boolean shouldForwardImeKey(int keyCode, int action, int repeatCount) {
    if (Y1InputKeysBridge.isBackKey(keyCode)) {
      return action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP;
    }
    if (SystemServerHooks.isBackUltraLongTracking()) return false;
    if (action == KeyEvent.ACTION_DOWN && repeatCount > 0) {
      if (Y1InputKeysBridge.isCenterKey(keyCode) || Y1InputKeysBridge.isPlayPauseKey(keyCode)) {
        return false;
      }
    }
    if (action == KeyEvent.ACTION_UP) {
      return Y1InputKeysBridge.isCenterKey(keyCode)
          || Y1InputKeysBridge.isPlayPauseKey(keyCode)
          || Y1InputKeysBridge.isTrackSkipKey(keyCode);
    }
    return isImeNavigationKey(keyCode);
  }

  private static boolean isImeNavigationKey(int keyCode) {
    return Y1InputKeysBridge.isWheelKey(keyCode)
        || Y1InputKeysBridge.isCenterKey(keyCode)
        || Y1InputKeysBridge.isTrackSkipKey(keyCode)
        || Y1InputKeysBridge.isPlayPauseKey(keyCode);
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

  static boolean isImeActive() {
    try {
      Class<?> sp = getSystemPropertiesClass();
      if (sp == null) return false;
      Object v = XposedHelpers.callStaticMethod(sp, "get", ACTIVE_PROPERTY, "0");
      return "1".equals(String.valueOf(v));
    } catch (Throwable t) {
      return false;
    }
  }

  private static void forwardKey(Context ctx, int keyCode, int action) {
    try {
      Intent svc = new Intent(ACTION_IME_KEY);
      svc.setComponent(new ComponentName(SOLAR_PKG, IME_SERVICE));
      svc.putExtra(EXTRA_KEY_CODE, keyCode);
      svc.putExtra(EXTRA_KEY_ACTION, action);
      ctx.startService(svc);
    } catch (Throwable t) {
      pulseXposedMiss();
    }
  }

  private static void pulseXposedMiss() {
    try {
      Class<?> sp = getSystemPropertiesClass();
      if (sp == null) return;
      XposedHelpers.callStaticMethod(sp, "set", XPOSED_MISS_PROPERTY,
          String.valueOf(android.os.SystemClock.uptimeMillis()));
    } catch (Throwable ignored) {}
  }

  private static final long KEY_CONSUMED_DISPATCH = -1L;
  private static final int KEY_CONSUMED_QUEUE = 0;

  private static KeyEvent findKeyEvent(Object[] args) {
    if (args == null) return null;
    for (Object arg : args) {
      if (arg instanceof KeyEvent) return (KeyEvent) arg;
    }
    return null;
  }

  private static boolean isHardwareVolumeKey(int keyCode) {
    return keyCode == KeyEvent.KEYCODE_VOLUME_UP
        || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        || keyCode == 160 || keyCode == 161;
  }
}
