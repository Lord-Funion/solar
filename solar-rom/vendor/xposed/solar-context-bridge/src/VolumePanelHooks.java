package com.solar.launcher.xposed.bridge;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.KeyEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Replace stock volume panel with Solar overlay — {@link AudioManager#STREAM_MUSIC} only.
 * MTK KitKat keeps {@link android.view.VolumePanel} in framework.jar (not SystemUI).
 */
final class VolumePanelHooks {

    private static final int FLAG_SHOW_UI = 1;
    /** Matches {@code com.solar.launcher.SolarUiState#PROP_NOW_PLAYING_SCREEN}. */
    private static final String PROP_NOW_PLAYING_SCREEN = "persist.solar.ui.now_playing";
    /** Matches {@code com.solar.launcher.HearingSafetyVolume#PROP_INTERNAL_ADJUST}. */
    private static final String PROP_INTERNAL_ADJUST = "persist.solar.volume.internal";
    /** Matches {@code com.solar.launcher.HearingSafetyVolume#PROP_ENABLED}. */
    private static final String PROP_HEARING_SAFETY = "persist.solar.hearing_safety";
    /** Matches {@code com.solar.launcher.HearingSafetyVolume#PROP_TEMP_UNLOCK}. */
    private static final String PROP_HEARING_SAFETY_TEMP = "persist.solar.hearing_safety.temp";
    /** Matches {@code com.solar.launcher.HearingSafetyVolume#PROP_ABSOLUTE_MAX}. */
    private static final String PROP_ABSOLUTE_MAX = "persist.solar.volume.abs_max";
    private static final float CAP_RATIO = 0.80f;
    private static final int DEFAULT_ABS_MAX = 15;
    /** Framework volume UI on Y1/Y2 KitKat — primary suppression target. */
    private static final String FRAMEWORK_VOLUME_PANEL = "android.view.VolumePanel";
    /** AudioService class names across AOSP / MTK builds. */
    private static final String[] AUDIO_SERVICE_CLASSES = {
            "com.android.server.audio.AudioService",
            "com.android.server.AudioService"
    };
    private static Handler mainHandler;
    private static final Runnable SHOW_OVERLAY = new Runnable() {
        @Override
        public void run() {
            Context ctx = SystemServerHooks.currentContext();
            if (ctx != null) {
                SolarOverlayClient.showVolumeOverlay(ctx);
            }
        }
    };

    /** Background looper — system_server may not have MainLooper at hook time. */
    private static Handler volumeHandler() {
        if (mainHandler == null) {
            HandlerThread ht = new HandlerThread("SolarCtxVol");
            ht.start();
            mainHandler = new Handler(ht.getLooper());
        }
        return mainHandler;
    }

    private VolumePanelHooks() {}

    /** AudioService + PWM volume keys in system_server. */
    static void installSystemServer(LoadPackageParam lpparam) {
        ClassLoader cl = lpparam.classLoader;
        int n = 0;
        n += hookAudioServiceVolume(cl);
        n += installFrameworkVolumePanel(cl);
        Class<?> pwm = findPhoneWindowManager(cl);
        if (pwm != null) {
            n += hookPhoneWindowManagerVolume(pwm);
        }
        SolarContextBridge.log("AudioService/framework volume hooks=" + n);
    }

    /** AOSP 4.4+ SystemUI volume panel — skipped on MTK Y1/Y2 (framework VolumePanel in system_server). */
    static void installSystemUi(LoadPackageParam lpparam) {
        // MTK devices route volume UI through android.view.VolumePanel in system_server
        // (installSystemServer). Hooking SystemUI VolumePanel with method replacement crashed
        // system:ui ("Android System has stopped") when hooked methods return non-void types.
        SolarContextBridge.log("SystemUI VolumePanel hooks skipped (MTK uses framework panel)");
    }

    /** MTK/AOSP KitKat — {@code android.view.VolumePanel} lives in framework, runs in system_server. */
    private static int installFrameworkVolumePanel(ClassLoader cl) {
        int n = 0;
        try {
            Class<?> panel = XposedHelpers.findClass(FRAMEWORK_VOLUME_PANEL, cl);
            // Stock UI entry points — swallow and route to Solar passive overlay.
            n += hookAllSuppress(panel, "onShowVolumeChanged");
            n += hookAllSuppress(panel, "show");
            n += hookAllSuppress(panel, "postShow");
            n += hookVolumeChanged(panel);
            SolarContextBridge.log("framework VolumePanel hooks=" + n);
        } catch (Throwable t) {
            SolarContextBridge.log("framework VolumePanel missing: " + t.getClass().getSimpleName());
        }
        return n;
    }

    /** Strip FLAG_SHOW_UI on media streams; still allow level change. */
    private static int hookVolumeChanged(Class<?> panel) {
        return XposedHookKit.hookAll(panel, "onVolumeChanged", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length < 2) return;
                int stream = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                int flags = param.args[1] instanceof Integer ? (Integer) param.args[1] : 0;
                if (!MediaVolumeControlStub.isMediaStream(stream)) return;
                if ((flags & FLAG_SHOW_UI) != 0) {
                    param.args[1] = flags & ~FLAG_SHOW_UI;
                    scheduleVolumeOverlay();
                }
            }
        });
    }

    private static int hookAudioServiceVolume(ClassLoader cl) {
        int n = 0;
        for (String name : AUDIO_SERVICE_CLASSES) {
            try {
                Class<?> audioService = XposedHelpers.findClass(name, cl);
                n += hookShowVolumePanel(audioService);
                n += hookAdjustStreamVolume(audioService);
                n += hookAdjustVolume(audioService);
                n += hookSetStreamVolume(audioService);
            } catch (Throwable ignored) {}
        }
        return n;
    }

    private static Class<?> findPhoneWindowManager(ClassLoader cl) {
        try {
            return XposedHelpers.findClass(
                    "com.android.internal.policy.impl.PhoneWindowManager", cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Y2 hardware volume in Rockbox — open passive Solar slider without blocking the key. */
    private static int hookPhoneWindowManagerVolume(Class<?> pwm) {
        return             XposedHookKit.hookAll(pwm, "interceptKeyBeforeDispatching", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                long t0 = System.nanoTime();
                if (OverlayKeyForwarder.isOverlayActive()) return;
                KeyEvent event = findKeyEvent(param.args);
                if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) return;
                int code = event.getKeyCode();
                if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) {
                    return;
                }
                Context ctx = SystemServerHooks.resolveContext(param.thisObject);
                // Solar fg: MainActivity / scheduleVolumeOverlay fg-gate owns HUD — do not schedule here.
                if (SystemServerHooks.isSolarForeground(ctx)) return;
                scheduleVolumeOverlay();
                // #region agent log
                try {
                    org.json.JSONObject d = new org.json.JSONObject();
                    d.put("keyCode", code);
                    BridgeAnrDebugLog.hookTiming("VolumePanelHooks.pwmVolume", "D", t0, d);
                } catch (Throwable ignored) {}
                // #endregion
            }
        });
    }

    private static int hookShowVolumePanel(Class<?> audioService) {
        try {
            return XposedHookKit.hookAll(audioService, "showVolumePanel", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int stream = param.args.length > 0 && param.args[0] instanceof Integer
                            ? (Integer) param.args[0] : AudioManager.STREAM_MUSIC;
                    if (MediaVolumeControlStub.isMediaStream(stream)) {
                        scheduleVolumeOverlay();
                    }
                    XposedHookKit.skipMethod(param);
                }
            });
        } catch (Throwable t) {
            SolarContextBridge.log("showVolumePanel hook fail: " + t.getClass().getSimpleName());
            return 0;
        }
    }

    private static int hookAdjustStreamVolume(Class<?> audioService) {
        try {
            return XposedHookKit.hookAll(audioService, "adjustStreamVolume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1) return;
                    int stream = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                    if (!MediaVolumeControlStub.isMediaStream(stream)) return;
                    if (isInternalVolumeAdjust()) return;
                    HearingSafetyStub.clampCurrentMediaLevel(param.thisObject, stream);
                    scheduleVolumeOverlay();
                }
            });
        } catch (Throwable t) {
            SolarContextBridge.log("adjustStreamVolume hook fail: " + t.getClass().getSimpleName());
            return 0;
        }
    }

    /** MTK services.jar often exposes adjustVolume instead of adjustStreamVolume. */
    private static int hookAdjustVolume(Class<?> audioService) {
        try {
            return XposedHookKit.hookAll(audioService, "adjustVolume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1) return;
                    int stream = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                    if (!MediaVolumeControlStub.isMediaStream(stream)) return;
                    if (isInternalVolumeAdjust()) return;
                    HearingSafetyStub.clampCurrentMediaLevel(param.thisObject, stream);
                    scheduleVolumeOverlay();
                }
            });
        } catch (Throwable t) {
            SolarContextBridge.log("adjustVolume hook fail: " + t.getClass().getSimpleName());
            return 0;
        }
    }

    private static int hookSetStreamVolume(Class<?> audioService) {
        try {
            return XposedHookKit.hookAll(audioService, "setStreamVolume", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2) return;
                    int stream = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                    if (!MediaVolumeControlStub.isMediaStream(stream)) return;
                    // 2026-07-18 — Only clamp when Hearing Safety is actively capping (HS on,
                    // not temp-unlocked). Was: always min(index, abs) even with HS off — if
                    // abs_max prop was low, volume could never reach true hardware top.
                    // Speaker route: never clamp (headphone safety only).
                    if (!HearingSafetyStub.shouldClampMedia()) return;
                    int index = param.args[1] instanceof Integer ? (Integer) param.args[1] : 0;
                    int eff = HearingSafetyStub.effectiveMaxIndex();
                    if (index > eff) {
                        param.args[1] = eff;
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 2) return;
                    int stream = param.args[0] instanceof Integer ? (Integer) param.args[0] : -1;
                    if (!MediaVolumeControlStub.isMediaStream(stream)) return;
                    // 2026-07-16 — flags==0 (Solar FLAGS_NO_UI) or internal prop: never open HUD.
                    // Was: only internal prop; race on async setprop re-opened overlay and lagged NP wheel.
                    if (isInternalVolumeAdjust() || isSilentVolumeFlags(param.args)) return;
                    scheduleVolumeOverlay();
                }
            });
        } catch (Throwable t) {
            SolarContextBridge.log("setStreamVolume hook fail: " + t.getClass().getSimpleName());
            return 0;
        }
    }

    /** Solar media volume uses flags=0 — not a user-facing volume-key UI request. */
    private static boolean isSilentVolumeFlags(Object[] args) {
        if (args == null || args.length < 3) return false;
        if (!(args[2] instanceof Integer)) return false;
        int flags = (Integer) args[2];
        return (flags & FLAG_SHOW_UI) == 0;
    }

    /** Replace stock panel method — show Solar overlay instead; use type-safe default return. */
    private static int hookAllSuppress(Class<?> cls, String method) {
        return XposedHookKit.hookAll(cls, method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                scheduleVolumeOverlay();
                XposedHookKit.skipMethod(param);
            }
        });
    }

    private static long lastShowTime = 0;
    private static final int THROTTLE_MS = 200;

    /** Debounced start of passive Solar volume overlay (Rockbox / third-party apps). */
    private static void scheduleVolumeOverlay() {
        if (isInternalVolumeAdjust()) return;
        // 2026-07-15 — NP/video inline pulse; never global HUD.
        if (isSolarNowPlayingScreen()) return;
        // 2026-07-20 — Solar Activity owns volume keys (VolumeHudPolicy); skip global HUD while fg.
        // Layman: in Solar, volume pop-up is the in-app bar — not the floating overlay.
        // Was: only NP prop; browse OK but NP race still opened overlay. Reversal: drop fg check.
        try {
            Context ctx = SystemServerHooks.currentContext();
            if (ctx != null && SystemServerHooks.isSolarForeground(ctx)) return;
        } catch (Throwable ignored) {}
        long now = System.currentTimeMillis();
        volumeHandler().removeCallbacks(SHOW_OVERLAY);
        if (now - lastShowTime > THROTTLE_MS) {
            volumeHandler().post(SHOW_OVERLAY);
            lastShowTime = now;
        } else {
            volumeHandler().postDelayed(SHOW_OVERLAY, THROTTLE_MS - (now - lastShowTime));
        }
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

    /** Solar Now Playing uses inline transport pulse — never the global volume HUD. */
    private static boolean isSolarNowPlayingScreen() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_NOW_PLAYING_SCREEN, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Solar in-app slider sets this prop so hooks do not re-open the global overlay. */
    private static boolean isInternalVolumeAdjust() {
        try {
            Class<?> sp = getSystemPropertiesClass();
            if (sp == null) return false;
            Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_INTERNAL_ADJUST, "0");
            return "1".equals(String.valueOf(v));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static KeyEvent findKeyEvent(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof KeyEvent) return (KeyEvent) arg;
        }
        return null;
    }

    /** Stream filter mirrored from app {@code MediaVolumeControl} (module cannot depend on Solar APK). */
    static final class MediaVolumeControlStub {
        private static final int STREAM_FM = 10;

        static boolean isMediaStream(int streamType) {
            return streamType == AudioManager.STREAM_MUSIC || streamType == STREAM_FM;
        }
    }

    /** Hearing safety cap for hardware volume keys — reads persist props set by Solar app. */
    static final class HearingSafetyStub {
        static int effectiveMaxIndex() {
            int abs = absoluteMaxIndex();
            // 2026-07-18 — Session temp unlock (extra vol-up past ear) lifts cap without clearing pref.
            if (!isSafetyEnabled() || isTempUnlocked()) return abs;
            return Math.max(1, Math.round(abs * CAP_RATIO));
        }

        /**
         * 2026-07-18 — True when STREAM_MUSIC should be clamped to 80% HW.
         * Layman: headphones / BT only — speaker always full range.
         * Technical: HS prop on + not temp-unlocked + not speaker-only route.
         */
        static boolean shouldClampMedia() {
            if (!isSafetyEnabled() || isTempUnlocked()) return false;
            if (isSpeakerOnlyRoute()) return false;
            return true;
        }

        static boolean isSafetyEnabled() {
            try {
                Class<?> sp = getSystemPropertiesClass();
                if (sp == null) return false;
                Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_HEARING_SAFETY, "0");
                return "1".equals(String.valueOf(v));
            } catch (Throwable ignored) {
                return false;
            }
        }

        /** Matches HearingSafetyVolume.PROP_TEMP_UNLOCK — "1" = session unlock. */
        static boolean isTempUnlocked() {
            try {
                Class<?> sp = getSystemPropertiesClass();
                if (sp == null) return false;
                Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_HEARING_SAFETY_TEMP, "0");
                return "1".equals(String.valueOf(v));
            } catch (Throwable ignored) {
                return false;
            }
        }

        /** Speaker-only: no wired jack, no A2DP/SCO — matches HearingSafetyVolume.isSpeakerOnlyRoute. */
        static boolean isSpeakerOnlyRoute() {
            try {
                // Prefer AudioSystem statics (available in system_server without Context).
                Class<?> as = Class.forName("android.media.AudioSystem");
                // DEVICE_STATE_AVAILABLE = 1
                final int AVAIL = 1;
                // DEVICE_OUT_WIRED_HEADSET=0x4, HEADPHONE=0x8, BLUETOOTH_A2DP=0x80, A2DP_HEADPHONES=0x100, A2DP_SPEAKER=0x200
                int[] devices = new int[] { 0x4, 0x8, 0x80, 0x100, 0x200, 0x10 /* SCO */ };
                for (int d : devices) {
                    Object st = XposedHelpers.callStaticMethod(as, "getDeviceConnectionState", d, "");
                    if (st instanceof Integer && ((Integer) st) == AVAIL) {
                        return false;
                    }
                }
                return true;
            } catch (Throwable ignored) {
                // Fallback: AudioManager from ServiceManager context is hard — if unknown, cap
                // (safer for headphones) only when HS is on.
                return false;
            }
        }

        static int absoluteMaxIndex() {
            try {
                Class<?> sp = getSystemPropertiesClass();
                if (sp == null) return DEFAULT_ABS_MAX;
                Object v = XposedHelpers.callStaticMethod(sp, "get", PROP_ABSOLUTE_MAX, "");
                String s = String.valueOf(v);
                if (s.length() > 0) {
                    int parsed = Integer.parseInt(s.trim());
                    if (parsed > 0) return parsed;
                }
            } catch (Throwable ignored) {}
            return DEFAULT_ABS_MAX;
        }

        static void clampCurrentMediaLevel(Object audioService, int stream) {
            if (!shouldClampMedia() || audioService == null) return;
            try {
                int cur = (Integer) XposedHelpers.callMethod(audioService, "getStreamVolume", stream);
                int eff = effectiveMaxIndex();
                if (cur > eff) {
                    XposedHelpers.callMethod(audioService, "setStreamVolume", stream, eff, 0);
                }
            } catch (Throwable ignored) {}
        }
    }
}
