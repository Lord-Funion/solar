package com.solar.launcher.mix;

import java.io.File;

/**
 * Assign-browse slot math — PREV/NEXT bind tracks 1–2 (DECK_COUNT).
 * Layman: pick up to two songs before the mix starts.
 * Technical: pure helpers for unit tests; no UI.
 * Was: PREV/NEXT/PLAY bind 1–3. Reversal: slot 2 + Play bind.
 * 2026-07-19 / 2026-07-21 Stems/Mix sanity
 */
public final class MixAssignSlots {
    public static final int SLOT_COUNT = MixSession.DECK_COUNT;
    /** Same hold threshold as Stem stutter / Mix start. */
    public static final long HOLD_PLAY_START_MS = 480L;

    private MixAssignSlots() {}

    /**
     * Bind file into slot (0..DECK_COUNT-1). Replaces existing. Null clears.
     * @return toast-friendly 1-based slot, or 0 if invalid
     */
    public static int bind(File[] slots, int slotIndex, File track) {
        if (slots == null || slots.length < SLOT_COUNT) return 0;
        if (slotIndex < 0 || slotIndex >= SLOT_COUNT) return 0;
        if (track != null && !track.isFile()) return 0;
        slots[slotIndex] = track;
        return track != null ? slotIndex + 1 : 0;
    }

    /** How many slots have a real file. */
    public static int filled(File[] slots) {
        if (slots == null) return 0;
        int n = 0;
        for (int i = 0; i < SLOT_COUNT && i < slots.length; i++) {
            if (slots[i] != null && slots[i].isFile()) n++;
        }
        return n;
    }

    /** True when hold-Play may start Mix (≥1 slot). */
    public static boolean canStart(File[] slots) {
        return filled(slots) >= 1;
    }

    /**
     * Scrub cursor wrap: nudge ms within [0, duration); wraps at ends.
     * 2026-07-19
     */
    public static int scrubWrap(int currentMs, int durationMs, int deltaMs) {
        if (durationMs <= 0) return Math.max(0, currentMs + deltaMs);
        int p = currentMs + deltaMs;
        while (p < 0) p += durationMs;
        while (p >= durationMs) p -= durationMs;
        return p;
    }

    /** Fade-replace: should fade before swap when gain above eps. */
    public static boolean needsFadeBeforeReplace(float gain, float eps) {
        return gain > eps;
    }
}
