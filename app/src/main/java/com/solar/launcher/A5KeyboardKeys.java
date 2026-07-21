package com.solar.launcher;

import android.view.KeyEvent;

/**
 * 2026-07-14 — A5 keyboard / IME hardware map while the tray is open.
 * Layman: Volume types space/delete; side Back submits; hold Back flips Capitals/Symbols; face mid picks a letter.
 * Tech: VolUp→space, VolDown→delete; Back (scan≠158) short→Enter / long→charset; face mid stays CENTER.
 * Was: volume = loudness HUD; Back dismissed tray; charset = Play/Center long only.
 * Reversal: drop callers; restore volume HUD + Back-dismiss + Play-long charset on A5 keyboard.
 */
public final class A5KeyboardKeys {

    /** Hold side Back this long to flip charset (matches IME Play-long). */
    public static final long CHARSET_HOLD_MS = 400L;

    private A5KeyboardKeys() {}

    /** True when A5 keyboard remaps apply (in-app tray or system IME). */
    public static boolean active() {
        return DeviceFeatures.isA5();
    }

    /** Volume Up while typing → insert a space (not loudness). */
    public static boolean isSpaceKey(int keyCode) {
        return active() && Y1InputKeys.isVolumeUpKey(keyCode);
    }

    /** Volume Down while typing → delete one char (not quiet). */
    public static boolean isDeleteKey(int keyCode) {
        return active() && Y1InputKeys.isVolumeDownKey(keyCode);
    }

    /**
     * Side Back / power-as-Back for Enter or charset — never face mid (scan 158).
     * Layman: the bottom side button submits or flips letters; the front middle still picks.
     */
    public static boolean isEnterBackEvent(KeyEvent event) {
        if (!active() || event == null) return false;
        if (A5InputKeys.isFaceMiddleEvent(event)) return false;
        return Y1InputKeys.isBackKey(event.getKeyCode())
                || A5InputKeys.isSidePowerEvent(event);
    }

    /** Same gate without a KeyEvent (IME int path after remap). */
    public static boolean isEnterBackKey(int keyCode, int scanCode) {
        if (!active()) return false;
        if (A5InputKeys.isFaceMiddle(keyCode, scanCode)) return false;
        return Y1InputKeys.isBackKey(keyCode)
                || A5InputKeys.isSidePower(keyCode, scanCode);
    }

    /**
     * Remap raw A5 hardware for IME handlers (face mid→CENTER, side power→BACK).
     * Layman: translate Timmkoo buttons into the same OK / Back Solar already understands.
     * Tech: A5InputKeys.remapToSolarKeyCode menus mode; -1 means keep keyCode.
     */
    public static int remapForIme(int keyCode, int scanCode) {
        if (!active()) return keyCode;
        int mapped = A5InputKeys.remapToSolarKeyCode(null, keyCode, scanCode, false);
        return mapped >= 0 ? mapped : keyCode;
    }

    /** Unit-test guard — A5 map vs Y1 unchanged. */
    static void selfCheck() {
        DeviceFeatures.setCachedFamilyForTest("a5");
        try {
            if (!isSpaceKey(KeyEvent.KEYCODE_VOLUME_UP)) {
                throw new AssertionError("vol up space");
            }
            if (!isDeleteKey(KeyEvent.KEYCODE_VOLUME_DOWN)) {
                throw new AssertionError("vol down delete");
            }
            if (!isEnterBackKey(KeyEvent.KEYCODE_BACK, 0)) {
                throw new AssertionError("back scan0 enter");
            }
            if (!isEnterBackKey(KeyEvent.KEYCODE_MEDIA_STOP, A5InputKeys.SIDE_POWER_SCAN)) {
                throw new AssertionError("side power enter");
            }
            if (isEnterBackKey(KeyEvent.KEYCODE_BACK, A5InputKeys.FACE_MIDDLE_SCAN)) {
                throw new AssertionError("face mid must not be enter-back");
            }
            if (remapForIme(KeyEvent.KEYCODE_BACK, A5InputKeys.FACE_MIDDLE_SCAN)
                    != KeyEvent.KEYCODE_DPAD_CENTER) {
                throw new AssertionError("face mid → center");
            }
            if (remapForIme(KeyEvent.KEYCODE_MEDIA_STOP, A5InputKeys.SIDE_POWER_SCAN)
                    != KeyEvent.KEYCODE_BACK) {
                throw new AssertionError("side power → back");
            }
        } finally {
            DeviceFeatures.resetCacheForTest();
        }
        DeviceFeatures.setCachedFamilyForTest("y1");
        try {
            if (isSpaceKey(KeyEvent.KEYCODE_VOLUME_UP) || isDeleteKey(24)) {
                throw new AssertionError("y1 must not use A5 keyboard volume map");
            }
        } finally {
            DeviceFeatures.resetCacheForTest();
        }
    }
}
