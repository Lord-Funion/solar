package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Headphone hearing safety — Solar-owned 80% warning and optional cap; bypasses OS safe-volume
 * limits when disabled (default). UI always uses 0–100 where 100 = top of the allowed range.
 *
 * 2026-07-15 — OS bypass must write DISABLED (1), never ACTIVE (3); old fallback locked volume ~80%.
 * Reversal: restore catch-only settings put with state 3 (broken EU lock — do not).
 */
public final class HearingSafetyVolume {

    /** Settings → Playback — when on, cap at 80% of hardware max and show Solar warnings. */
    public static final String PREF_ENABLED = "hearing_safety_enabled";
    /** Xposed / AudioService reads this — "1" = enforce cap, "0" = bypass OS limits. */
    public static final String PROP_ENABLED = "persist.solar.hearing_safety";
    /**
     * 2026-07-18 — Session temp unlock while Hearing Safety pref stays on.
     * Layman: user kept turning volume past the ear — cap lifts until reboot / re-enable.
     * Technical: "1" = do not clamp; Xposed {@code HearingSafetyStub} must honor this.
     */
    public static final String PROP_TEMP_UNLOCK = "persist.solar.hearing_safety.temp";
    /** KitKat AudioService bypass when hearing safety is off. */
    public static final String PROP_SAFE_MEDIA_BYPASS = "audio.safemedia.bypass";

    /** Slider + transport HUD always use 0–100 display units. */
    public static final int DISPLAY_MAX = 100;
    /** Absolute hardware level (index) that triggers Solar's hearing warning. */
    private static final float WARN_ABSOLUTE_RATIO = 0.80f;
    private static final float CAP_RATIO = 0.80f;
    private static final int DEFAULT_ABSOLUTE_MAX = 15;
    private static final String PREFS_ABS = "solar_volume_abs_max";
    /** Same store as {@link MainActivity} settings toggles. */
    private static final String PREFS_SETTINGS = "SOLAR_SETTINGS";
    /**
     * AOSP {@code Settings.Global.AUDIO_SAFE_VOLUME_STATE} values.
     * 0 NOT_CONFIGURED, 1 DISABLED (full range), 2 INACTIVE (user confirmed), 3 ACTIVE (~80% EU cap).
     */
    static final int OS_SAFE_VOLUME_DISABLED = 1;
    static final int OS_SAFE_VOLUME_ACTIVE = 3;

    private static volatile int lastWarnIndex = -1;
    private static volatile int cachedAbsoluteMax = -1;
    /** 2026-07-16 — Hot-path cache; prefs only re-read when toggle changes. */
    private static volatile Boolean enabledCache;
    /** 2026-07-16 — Throttle OS EU unlock so failed up-steps do not spam root/reflection. */
    private static volatile long lastEnsureFullRangeMs;
    /**
     * 2026-07-18 — Session-only: extra volume-up at cap temporarily lifts the 80% limit.
     * Layman: keep scrolling past the ear to open more loudness for this session.
     * Technical: does not rewrite {@link #PREF_ENABLED}; cleared on setEnabled / process death.
     */
    private static volatile boolean temporarilyUnlocked;

    private HearingSafetyVolume() {}

    /** True when Solar caps volume and shows hearing warnings (default off). */
    public static boolean isEnabled(Context ctx) {
        if (ctx == null) return false;
        Boolean cached = enabledCache;
        if (cached != null) return cached.booleanValue();
        boolean on = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .getBoolean(PREF_ENABLED, false);
        enabledCache = Boolean.valueOf(on);
        return on;
    }

    /**
     * True when Hearing Safety pref is on but user temporarily unlocked past the ear.
     * Layman: the safety switch is still on, but this session may go louder.
     */
    public static boolean isTemporarilyUnlocked() {
        return temporarilyUnlocked;
    }

    /**
     * True when the volume bar should show the ear cue (at 100% while HS enforces cap).
     * Layman: ear means “this is the safe top — keep turning for more”.
     * 2026-07-18 — Ear only on wired / Bluetooth (speaker is never capped by Hearing Safety).
     */
    public static boolean shouldShowEarAtDisplayMax(Context ctx) {
        return isCapActive(ctx, AudioManager.STREAM_MUSIC);
    }

    /**
     * True when Hearing Safety actually clamps this stream right now.
     * Layman: safety is for headphones / Bluetooth, not the built-in speaker.
     * Technical: pref on + not temp-unlocked + wired headset or A2DP (or unknown route → cap, safer).
     * 2026-07-18 — Was: cap applied to all STREAM_MUSIC including speaker → speaker bar stuck ~80%.
     */
    public static boolean isCapActive(Context ctx, int streamType) {
        if (!isEnabled(ctx) || temporarilyUnlocked) return false;
        if (!MediaVolumeControl.isMediaVolumeStream(streamType)) return false;
        // Speaker-only: never apply Solar 80% cap (settings copy says “headphone volume”).
        if (isSpeakerOnlyRoute(ctx)) return false;
        return true;
    }

    /**
     * True when media is clearly going out the built-in speaker (no wired jack, no A2DP).
     * Layman: phone speaker path — full volume range always.
     */
    public static boolean isSpeakerOnlyRoute(Context ctx) {
        AudioManager am = audioManager(ctx);
        if (am == null) return false;
        try {
            if (am.isWiredHeadsetOn()) return false;
        } catch (Throwable ignored) {}
        try {
            if (am.isBluetoothA2dpOn()) return false;
        } catch (Throwable ignored) {}
        try {
            if (am.isBluetoothScoOn()) return false;
        } catch (Throwable ignored) {}
        return true;
    }

    /**
     * Session unlock — full hardware range until process death or Hearing Safety toggled.
     * Layman: open the remaining 20% loudness for now without turning the setting off permanently.
     */
    public static void setTemporarilyUnlocked(Context ctx, boolean unlocked) {
        temporarilyUnlocked = unlocked;
        // 2026-07-18 — Never sync-su on volume path (was multi-second lag). Prefer reflection; async root fallback.
        String val = unlocked ? "1" : "0";
        if (!setSystemPropertyFast(PROP_TEMP_UNLOCK, val)) {
            RootShell.runAsync("setprop " + PROP_TEMP_UNLOCK + " " + val);
        }
        if (unlocked && ctx != null) {
            ensureFullVolumeRange(ctx.getApplicationContext());
        }
    }

    /** In-process setprop when allowed (no su on volume wheel). */
    private static boolean setSystemPropertyFast(String key, String value) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            sp.getMethod("set", String.class, String.class).invoke(null, key, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Persisted hardware max index for Xposed volume hooks (system_server has no app prefs). */
    public static final String PROP_ABSOLUTE_MAX = "persist.solar.volume.abs_max";

    /** Persist toggle, sync OS bypass props, clamp level when enabling. */
    public static void setEnabled(Context ctx, boolean enabled) {
        if (ctx == null) return;
        Context app = ctx.getApplicationContext();
        app.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_ENABLED, enabled).apply();
        enabledCache = Boolean.valueOf(enabled);
        // 2026-07-18 — Permanent toggle clears session temp unlock (fresh state).
        temporarilyUnlocked = false;
        if (!setSystemPropertyFast(PROP_TEMP_UNLOCK, "0")) {
            RootShell.runAsync("setprop " + PROP_TEMP_UNLOCK + " 0");
        }
        // 2026-07-15 — Push props + clear EU cap when off; Solar 80% clamp when on.
        // Layman: Hearing Safety ON = real volume stops at 80% HW but the bar still goes to 100%.
        syncSystemBypass(app, enabled);
        if (enabled) {
            // 2026-07-18 — Only clamp headphones/BT; speaker stays full range while pref is on.
            if (isCapActive(app, AudioManager.STREAM_MUSIC)) {
                clampStream(app, AudioManager.STREAM_MUSIC);
            }
        } else {
            // 2026-07-16 — Turning safety off must unlock full hardware range immediately.
            ensureFullVolumeRange(app);
        }
    }

    /** Boot / prefs load — apply bypass without changing the stored toggle. */
    public static void syncFromPrefs(Context ctx) {
        if (ctx == null) return;
        enabledCache = null; // re-read prefs once after boot
        syncSystemBypass(ctx.getApplicationContext(), isEnabled(ctx));
    }

    /**
     * Hardware stream index maximum (full steps).
     * 2026-07-18 — Prefer live {@link AudioManager#getStreamMaxVolume}; grow cache when OS rises.
     * Was: freeze first sample and inflate any reported&lt;15 to 15 → speaker bar never hit 100%
     * when true max was lower or when OS max was honest but cache was inflated.
     * Layman: 100% means the real top step the device will accept (after any unlock).
     */
    public static int getAbsoluteMaxIndex(Context ctx, int streamType) {
        AudioManager am = audioManager(ctx);
        int reported = am != null
                ? Math.max(1, am.getStreamMaxVolume(streamType))
                : DEFAULT_ABSOLUTE_MAX;
        int stored = cachedAbsoluteMax > 0 ? cachedAbsoluteMax : readStoredAbsoluteMax(ctx, streamType);

        // Live reported is always a valid ceiling for setStreamVolume *right now*.
        // Keep a higher stored value only as “true HW after EU unlock” when OS temporarily
        // reports a reduced max (classic headphone safe-media) — never invent 15 on speaker.
        int abs;
        if (stored > reported) {
            // Headphone EU path often reports reduced max while HW is still higher after unlock.
            // Speaker-only: trust live reported (do not keep a stale inflated stored max).
            if (isSpeakerOnlyRoute(ctx)) {
                abs = reported;
            } else {
                abs = stored;
            }
        } else {
            abs = reported;
        }
        if (abs <= 0) abs = DEFAULT_ABSOLUTE_MAX;

        if (abs != cachedAbsoluteMax) {
            cachedAbsoluteMax = abs;
            storeAbsoluteMax(ctx, streamType, abs);
            // Keep Xposed abs_max in sync when we learn a new top.
            if (abs > 0) {
                if (!setSystemPropertyFast(PROP_ABSOLUTE_MAX, String.valueOf(abs))) {
                    RootShell.runAsync("setprop " + PROP_ABSOLUTE_MAX + " " + abs);
                }
            }
        }
        return abs;
    }

    /**
     * Top index Solar allows — full hardware max, or 80% cap when Hearing Safety is
     * actively clamping this route (headphones / BT).
     * 2026-07-18 — Speaker always gets full abs max even if the HS pref is on.
     */
    public static int getEffectiveMaxIndex(Context ctx, int streamType) {
        int abs = getAbsoluteMaxIndex(ctx, streamType);
        if (!isCapActive(ctx, streamType)) return abs;
        return Math.max(1, Math.round(abs * CAP_RATIO));
    }

    /** Map stream index to 0–100 display level (100 = effective max). */
    public static int indexToDisplay(int index, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        return Math.min(DISPLAY_MAX, Math.max(0, index * DISPLAY_MAX / effectiveMax));
    }

    /** Map 0–100 display level to stream index. */
    public static int displayToIndex(int display, int effectiveMax) {
        if (effectiveMax <= 0) return 0;
        int clamped = Math.max(0, Math.min(DISPLAY_MAX, display));
        return Math.max(0, Math.min(effectiveMax, clamped * effectiveMax / DISPLAY_MAX));
    }

    /**
     * Display 0–100 for the stream.
     * 2026-07-18 — If we are already at the OS-reported top and HS is not capping this route,
     * always paint 100% (fixes speaker / honest-max stranded below full bar).
     */
    public static int getDisplayVolume(Context ctx, int streamType) {
        AudioManager am = audioManager(ctx);
        if (am == null) return 0;
        int idx = am.getStreamVolume(streamType);
        int reportedMax = Math.max(1, am.getStreamMaxVolume(streamType));
        int effMax = getEffectiveMaxIndex(ctx, streamType);
        // At the live OS ceiling with no Solar cap → full bar (even if abs cache was wrong).
        if (!isCapActive(ctx, streamType) && reportedMax > 0 && idx >= reportedMax) {
            return DISPLAY_MAX;
        }
        // Prefer not exceeding live OS max for scaling when cap is off (setStreamVolume can't go higher).
        int scaleMax = effMax;
        if (!isCapActive(ctx, streamType) && reportedMax > 0) {
            scaleMax = Math.min(effMax, reportedMax);
        }
        return indexToDisplay(idx, scaleMax);
    }

    /**
     * Step volume up/down with cap + warning; returns new stream index.
     * 2026-07-18 — Extra volume-up at Hearing Safety cap → session temp unlock (ear path).
     * Layman: keep scrolling right / pressing volume up at the ear to open more loudness.
     * Technical: does not raise index on unlock tick — scale re-maps so display drops ~100→80.
     * Speaker path never temp-unlocks (no cap); stuck up-steps always try EU unlock once.
     */
    public static int adjustStreamIndex(Context ctx, int streamType, boolean up) {
        AudioManager am = audioManager(ctx);
        if (am == null || !MediaVolumeControl.isMediaVolumeStream(streamType)) return 0;
        int cur = am.getStreamVolume(streamType);
        int reportedMax = Math.max(1, am.getStreamMaxVolume(streamType));
        int effMax = getEffectiveMaxIndex(ctx, streamType);
        // Never target past what we allow; also never past live OS max without unlock attempt.
        int hardTop = Math.max(effMax, reportedMax);
        int target = cur;
        if (up && cur < effMax) {
            target = cur + 1;
        } else if (!up && cur > 0) {
            target = cur - 1;
        } else if (up && cur >= effMax) {
            // At Solar cap on headphones/BT: one more vol-up temporarily lifts the 80% ceiling.
            if (isCapActive(ctx, streamType)) {
                setTemporarilyUnlocked(ctx, true);
                // Loudness unchanged; effective max is now absolute — display will re-scale.
                return cur;
            }
            // Full top already — still try OS unlock if live max is higher than index (rare).
            if (cur < reportedMax) {
                target = cur + 1;
            } else if (cur < hardTop) {
                target = cur + 1;
            } else {
                return cur;
            }
        }
        setStreamIndex(ctx, streamType, target);
        int after = am.getStreamVolume(streamType);
        // OS EU / safe-media often freezes index below hardware top — clear brake and retry.
        // Layman: if the wheel won't go louder, knock off the phone's hidden volume lock.
        // 2026-07-18 — Also when HS pref is on but speaker route (cap inactive), so speaker hits 100%.
        if (up && target > cur && after <= cur && !isCapActive(ctx, streamType)) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastEnsureFullRangeMs >= 1500L) {
                lastEnsureFullRangeMs = now;
                ensureFullVolumeRange(ctx);
                // Drop stale inflated abs so we re-read live max after unlock.
                int newReported = Math.max(1, am.getStreamMaxVolume(streamType));
                if (newReported > cachedAbsoluteMax) {
                    cachedAbsoluteMax = newReported;
                    storeAbsoluteMax(ctx, streamType, newReported);
                }
                int newEff = getEffectiveMaxIndex(ctx, streamType);
                int retryTarget = Math.min(cur + 1, Math.max(newEff, newReported));
                if (retryTarget > cur) {
                    setStreamIndex(ctx, streamType, retryTarget);
                    after = am.getStreamVolume(streamType);
                }
            }
        }
        return after;
    }

    /** Set stream index with effective cap and hearing warning. */
    public static void setStreamIndex(Context ctx, int streamType, int index) {
        AudioManager am = audioManager(ctx);
        if (am == null || !MediaVolumeControl.isMediaVolumeStream(streamType)) return;
        int effMax = getEffectiveMaxIndex(ctx, streamType);
        int clamped = Math.max(0, Math.min(effMax, index));
        // Never ask OS past live max — setStreamVolume silently no-ops or clamps.
        try {
            int reported = Math.max(1, am.getStreamMaxVolume(streamType));
            if (clamped > reported && !isCapActive(ctx, streamType)) {
                ensureFullVolumeRange(ctx);
                reported = Math.max(1, am.getStreamMaxVolume(streamType));
            }
            if (clamped > reported) clamped = reported;
        } catch (Throwable ignored) {}
        MediaVolumeControl.markInternalVolumeAdjust();
        am.setStreamVolume(streamType, clamped, MediaVolumeControl.FLAGS_NO_UI);
        maybeShowWarning(ctx, streamType, clamped);
    }

    /**
     * Toast when crossing 80% of hardware max — only while Hearing Safety is actively capping
     * (headphones / BT), not on speaker.
     */
    public static void maybeShowWarning(Context ctx, int streamType, int newIndex) {
        if (ctx == null || !isCapActive(ctx, streamType)) return;
        int absMax = getAbsoluteMaxIndex(ctx, streamType);
        int warnIndex = Math.max(1, Math.round(absMax * WARN_ABSOLUTE_RATIO));
        if (newIndex < warnIndex) {
            lastWarnIndex = -1;
            return;
        }
        if (lastWarnIndex >= warnIndex) return;
        lastWarnIndex = newIndex;
        final Context app = ctx.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(app, app.getString(R.string.hearing_safety_warning), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Probe hardware max with OS bypass enabled — call from background bootstrap. */
    public static void probeAbsoluteMaxAsync(final Context ctx) {
        if (ctx == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                syncSystemBypass(ctx.getApplicationContext(), false);
                AudioManager am = audioManager(ctx);
                if (am == null) return;
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                if (max < DEFAULT_ABSOLUTE_MAX) max = DEFAULT_ABSOLUTE_MAX;
                cachedAbsoluteMax = max;
                storeAbsoluteMax(ctx, AudioManager.STREAM_MUSIC, max);
                if (RootShell.canRun()) {
                    RootShell.run("setprop " + PROP_ABSOLUTE_MAX + " " + max);
                }
                syncSystemBypass(ctx.getApplicationContext(), isEnabled(ctx));
            }
        }, "VolAbsProbe").start();
    }

    /** Test hook — display/index mapping without AudioManager. */
    static int indexToDisplayForTest(int index, int effectiveMax) {
        return indexToDisplay(index, effectiveMax);
    }

    static int displayToIndexForTest(int display, int effectiveMax) {
        return displayToIndex(display, effectiveMax);
    }

    static int effectiveMaxForTest(int absoluteMax, boolean safetyOn) {
        if (!safetyOn) return absoluteMax;
        return Math.max(1, Math.round(absoluteMax * CAP_RATIO));
    }

    /** Route-aware cap math without AudioManager (headset=true → may cap). */
    static int effectiveMaxForRouteTest(int absoluteMax, boolean safetyOn, boolean tempUnlocked,
            boolean speakerOnly) {
        if (!safetyOn || tempUnlocked || speakerOnly) return absoluteMax;
        return Math.max(1, Math.round(absoluteMax * CAP_RATIO));
    }

    /** Display when at live OS max and not capped → always 100. */
    static int displayAtLiveMaxForTest(int index, int reportedMax, int effectiveMax, boolean capActive) {
        if (!capActive && reportedMax > 0 && index >= reportedMax) return DISPLAY_MAX;
        int scale = effectiveMax;
        if (!capActive && reportedMax > 0) scale = Math.min(effectiveMax, reportedMax);
        return indexToDisplay(index, scale);
    }

    static void resetWarnStateForTest() {
        lastWarnIndex = -1;
        cachedAbsoluteMax = -1;
        temporarilyUnlocked = false;
        enabledCache = null;
    }

    /** Test hook — session temp unlock without SystemProperties. */
    static void setTemporarilyUnlockedForTest(boolean unlocked) {
        temporarilyUnlocked = unlocked;
    }

    private static void clampStream(Context ctx, int streamType) {
        AudioManager am = audioManager(ctx);
        if (am == null) return;
        int cur = am.getStreamVolume(streamType);
        int eff = getEffectiveMaxIndex(ctx, streamType);
        if (cur > eff) {
            setStreamIndex(ctx, streamType, eff);
        }
    }

    /**
     * Sync Xposed props and clear Android’s EU safe-media lock when Hearing Safety is off.
     * Layman: turn off the phone’s hidden “headphones too loud” brake unless the user asked for it.
     * 2026-07-15 — Always clear OS state when off (reflection alone left ACTIVE=3 on MTK).
     */
    private static void syncSystemBypass(Context ctx, boolean safetyOn) {
        // 2026-07-15 — Clear OS cap even without root when the KitKat API exists.
        if (!safetyOn) {
            disableOsSafeMediaVolume(ctx);
        }
        if (!RootShell.canRun()) return;
        RootShell.runAsync("setprop " + PROP_ENABLED + " " + (safetyOn ? "1" : "0"));
        RootShell.runAsync("setprop " + PROP_SAFE_MEDIA_BYPASS + " " + (safetyOn ? "false" : "true"));
        int abs = getAbsoluteMaxIndex(ctx, AudioManager.STREAM_MUSIC);
        if (abs > 0) {
            RootShell.runAsync("setprop " + PROP_ABSOLUTE_MAX + " " + abs);
        }
        if (!safetyOn) {
            // Belt-and-suspenders: force DISABLED — ACTIVE (3) was the old bug that capped ~80%.
            writeOsSafeVolumeState(OS_SAFE_VOLUME_DISABLED);
        }
    }

    /**
     * Ask AudioService to drop the EU headphone ceiling; then force global state DISABLED.
     * Reversal: prior catch wrote state 3 (ACTIVE) which enforced the limit — replaced 2026-07-15.
     */
    private static void disableOsSafeMediaVolume(Context ctx) {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "audio");
            Class<?> stub = Class.forName("android.media.IAudioService$Stub");
            Object svc = stub.getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
            // KitKat signature takes calling package; JB omit — try both.
            try {
                svc.getClass().getMethod("disableSafeMediaVolume", String.class)
                        .invoke(svc, ctx != null ? ctx.getPackageName() : "com.solar.launcher");
            } catch (NoSuchMethodException noPkg) {
                svc.getClass().getMethod("disableSafeMediaVolume").invoke(svc);
            }
        } catch (Exception ignored) {
            // Y1 JB / OEM stubs — settings / root put below still clears the lock.
        }
        writeOsSafeVolumeState(ctx, OS_SAFE_VOLUME_DISABLED);
    }

    /**
     * 2026-07-15 — Re-assert full range before video / music if OS still clamps ~80%.
     * Layman: phones hide a “headphones too loud” brake — knock it off again if volume sticks.
     * Call from player open and volume wheel when up-step fails.
     * 2026-07-16 — After unlock, refresh absolute max so UI 100% can map to true hardware top.
     */
    /**
     * Re-assert full range when OS still clamps below hardware top.
     * 2026-07-18 — Run whenever cap is inactive (speaker, or HS off, or temp unlock) — not only
     * when the HS pref is false. Was: early-return if pref on → speaker stuck under EU lock.
     */
    public static void ensureFullVolumeRange(Context ctx) {
        if (ctx == null) return;
        // Still apply OS unlock when pref is on but route is speaker (cap inactive).
        if (isCapActive(ctx, AudioManager.STREAM_MUSIC)) return;
        Context app = ctx.getApplicationContext();
        // Prefer full-range OS state even if HS pref is on (speaker / temp unlock).
        syncSystemBypass(app, false);
        // Re-assert HS prop if pref is still on so Xposed knows the toggle after speaker unlock.
        if (isEnabled(app) && !setSystemPropertyFast(PROP_ENABLED, "1")) {
            RootShell.runAsync("setprop " + PROP_ENABLED + " 1");
        }
        AudioManager am = audioManager(app);
        if (am == null) return;
        int reported = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        // Trust live max after unlock; grow cache, never invent a higher DEFAULT on speaker.
        int abs = reported;
        if (reported > cachedAbsoluteMax) {
            abs = reported;
        } else if (cachedAbsoluteMax > reported && !isSpeakerOnlyRoute(app)) {
            // Keep higher stored for headset after temporary OS reduction.
            abs = cachedAbsoluteMax;
        }
        if (abs > 0 && abs != cachedAbsoluteMax) {
            cachedAbsoluteMax = abs;
            storeAbsoluteMax(app, AudioManager.STREAM_MUSIC, abs);
            // 2026-07-16 — Never canRun() here (sync su); async setprop is enough for Xposed.
            if (!setSystemPropertyFast(PROP_ABSOLUTE_MAX, String.valueOf(abs))) {
                RootShell.runAsync("setprop " + PROP_ABSOLUTE_MAX + " " + abs);
            }
        }
    }

    /**
     * Write {@code audio_safe_volume_state} — prefer Settings API (no root), then su.
     * 1 = full volume, 3 = EU ~80% lock.
     */
    private static void writeOsSafeVolumeState(Context ctx, int state) {
        boolean wrote = false;
        if (ctx != null) {
            try {
                android.provider.Settings.Global.putInt(
                        ctx.getContentResolver(), "audio_safe_volume_state", state);
                wrote = true;
            } catch (Exception ignored) {}
            if (!wrote) {
                try {
                    android.provider.Settings.System.putInt(
                            ctx.getContentResolver(), "audio_safe_volume_state", state);
                    wrote = true;
                } catch (Exception ignored) {}
            }
        }
        if (!wrote && RootShell.canRun()) {
            RootShell.runAsync("settings put global audio_safe_volume_state " + state);
        }
    }

    /** Root write of {@code audio_safe_volume_state} — 1 = full volume, 3 = EU ~80% lock. */
    private static void writeOsSafeVolumeState(int state) {
        writeOsSafeVolumeState(null, state);
    }

    private static AudioManager audioManager(Context ctx) {
        if (ctx == null) return null;
        return (AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private static int readStoredAbsoluteMax(Context ctx, int streamType) {
        if (ctx == null) return -1;
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREFS_ABS, Context.MODE_PRIVATE);
        return sp.getInt("stream_" + streamType, -1);
    }

    private static void storeAbsoluteMax(Context ctx, int streamType, int max) {
        if (ctx == null) return;
        ctx.getApplicationContext().getSharedPreferences(PREFS_ABS, Context.MODE_PRIVATE)
                .edit().putInt("stream_" + streamType, max).apply();
    }
}
