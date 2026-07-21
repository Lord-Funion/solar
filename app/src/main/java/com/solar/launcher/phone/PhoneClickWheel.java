package com.solar.launcher.phone;

import android.view.KeyEvent;

import com.solar.launcher.Y1InputKeys;

/**
 * 2026-07-20 — Maps click-wheel touch geometry to the same keycodes Y1 hardware uses.
 * Inject-only: callers synthesize KeyEvents; Y1InputKeys / keymap paths stay untouched.
 * Was: no on-screen wheel. Now: drag = MEDIA_PLAY/PAUSE notches; cardinals = Back/skip/PP.
 * Reversal: delete; phones lose touch dial (hardware keys still work).
 */
public final class PhoneClickWheel {

    /** Centre disc radius as a fraction of the wheel radius. */
    public static final float CENTER_FRAC = 0.32f;
    /** Cardinal hit wedge half-angle in degrees (± from N/E/S/W). */
    public static final float CARDINAL_HALF_DEG = 35f;
    /** Radians of arc that equal one wheel notch (≈ one list row). */
    public static final float NOTCH_RADIANS = 0.28f;

    /** Where a tap landed on the pad. */
    public enum Zone {
        CENTER,
        MENU,   // top → BACK
        PREV,   // left → MEDIA_PREVIOUS
        NEXT,   // right → MEDIA_NEXT
        PLAY,   // bottom → MEDIA_PLAY_PAUSE
        RING    // circumferential drag zone
    }

    private PhoneClickWheel() {}

    /**
     * 2026-07-20 — Classify a touch relative to wheel centre.
     * @param dx touchX − centreX
     * @param dy touchY − centreY (Android Y grows downward)
     * @param radius outer wheel radius in the same units
     */
    public static Zone zoneAt(float dx, float dy, float radius) {
        if (radius <= 0f) return Zone.CENTER;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= radius * CENTER_FRAC) return Zone.CENTER;
        if (dist > radius * 1.05f) return Zone.RING; // outside — still treat as ring drag
        // Angle: 0 = east, CCW; convert so 0 = north (top / MENU).
        double angleRad = Math.atan2(dy, dx); // −π..π, 0 = east
        double degFromEast = Math.toDegrees(angleRad);
        // Rotate so north (up, −Y) is 0°: north in atan2 is −90°.
        double degFromNorth = degFromEast + 90.0;
        if (degFromNorth < 0) degFromNorth += 360.0;
        if (degFromNorth >= 360.0) degFromNorth -= 360.0;
        // Cardinal wedges: N=0, E=90, S=180, W=270.
        if (nearCardinal(degFromNorth, 0)) return Zone.MENU;
        if (nearCardinal(degFromNorth, 90)) return Zone.NEXT;
        if (nearCardinal(degFromNorth, 180)) return Zone.PLAY;
        if (nearCardinal(degFromNorth, 270)) return Zone.PREV;
        return Zone.RING;
    }

    private static boolean nearCardinal(double degFromNorth, double cardinal) {
        double d = Math.abs(degFromNorth - cardinal);
        if (d > 180) d = 360 - d;
        return d <= CARDINAL_HALF_DEG;
    }

    /** Keycode for a discrete zone tap (not ring drag). */
    public static int keyCodeForZone(Zone zone) {
        if (zone == null) return Y1InputKeys.KEY_CENTER;
        switch (zone) {
            case CENTER:
                return Y1InputKeys.KEY_CENTER;
            case MENU:
                return Y1InputKeys.KEY_BACK;
            case PREV:
                return Y1InputKeys.KEY_TRACK_PREV;
            case NEXT:
                return Y1InputKeys.KEY_TRACK_NEXT;
            case PLAY:
                return Y1InputKeys.KEY_PLAY_PAUSE;
            case RING:
            default:
                return 0;
        }
    }

    /**
     * 2026-07-20 — Signed notch count from circumferential drag.
     * Positive = wheel down (MEDIA_PAUSE / list +1); negative = wheel up (MEDIA_PLAY).
     * Uses polar angle delta; ignores radial motion.
     */
    public static int notchesFromDrag(float prevDx, float prevDy, float dx, float dy,
            float accumulatedRadians) {
        // Caller tracks accumulatedRadians across MOVE events; this helper is pure per-step.
        float step = angleDeltaRadians(prevDx, prevDy, dx, dy);
        return notchesFromAccumulated(accumulatedRadians + step);
    }

    /** Angle delta in radians; positive = clockwise on screen (downward list). */
    public static float angleDeltaRadians(float prevDx, float prevDy, float dx, float dy) {
        double a0 = Math.atan2(prevDy, prevDx);
        double a1 = Math.atan2(dy, dx);
        double d = a1 - a0;
        // Wrap to −π..π
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return (float) d;
    }

    /**
     * 2026-07-20 — How many whole notches fit in accumulated radians (toward + = down).
     * Remainder stays with the caller; this returns floor toward zero of abs / notch.
     */
    public static int notchesFromAccumulated(float accumulatedRadians) {
        if (Math.abs(accumulatedRadians) < NOTCH_RADIANS) return 0;
        int n = (int) (accumulatedRadians / NOTCH_RADIANS);
        return n;
    }

    /** Wheel-up key (scroll toward previous row). */
    public static int wheelUpKeyCode() {
        return Y1InputKeys.KEY_WHEEL_UP;
    }

    /** Wheel-down key (scroll toward next row). */
    public static int wheelDownKeyCode() {
        return Y1InputKeys.KEY_WHEEL_DOWN;
    }

    /**
     * 2026-07-20 — Build a synthetic DOWN+UP pair description for tests / injectors.
     * Returns keyCode; action is caller's job via KeyEvent.
     */
    public static boolean isInjectedWheelKey(int keyCode) {
        return keyCode == Y1InputKeys.KEY_WHEEL_UP
                || keyCode == Y1InputKeys.KEY_WHEEL_DOWN
                || keyCode == Y1InputKeys.KEY_BACK
                || keyCode == Y1InputKeys.KEY_CENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == Y1InputKeys.KEY_TRACK_PREV
                || keyCode == Y1InputKeys.KEY_TRACK_NEXT
                || keyCode == Y1InputKeys.KEY_PLAY_PAUSE;
    }
}
