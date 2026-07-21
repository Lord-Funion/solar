package com.solar.launcher;

import android.view.KeyEvent;

/**
 * 2026-07-20 — Shared rules for Y1/Y2 scrollwheel notches vs ghost backlog.
 * Layman: every real dial click should move the highlight; leftover ticks after you stop must not.
 * Technical: stale = old event + idle gap; repeats count only while key held (EV_KEY value=2).
 * Reversal: delete class; restore age-only stale + drop-all-repeats in MainActivity/Y1InputKeys.
 */
public final class WheelNavPolicy {

    /**
     * 2026-07-20 — Event age above this may be stale — but only when the finger looks parked.
     * Was: age-only 90 ms hard-stop mid-spin dropped live queued notches → ~1 row/rev.
     */
    public static final long STALE_EVENT_MS = 90L;

    /**
     * 2026-07-20 — Recent live offer means “still spinning” (match coalescer idle clear).
     * Layman: if a click landed in the last blink, late siblings are lag, not after-lift junk.
     */
    public static final long LIVE_SPIN_MS = 110L;

    private WheelNavPolicy() {}

    /**
     * 2026-07-20 — True when a queued KEY is too old and the dial looks stopped.
     * Layman: throw away leftover clicks only after you pause — not while the UI is catching up.
     * Technical: age &gt; {@link #STALE_EVENT_MS} and sinceLastLiveOffer ≥ {@link #LIVE_SPIN_MS} (or none).
     */
    public static boolean isStaleEvent(long eventAgeMs, long sinceLastLiveOfferMs) {
        if (eventAgeMs <= STALE_EVENT_MS) return false;
        if (sinceLastLiveOfferMs >= 0L && sinceLastLiveOfferMs < LIVE_SPIN_MS) return false;
        return true;
    }

    /**
     * 2026-07-20 — Whether this KEY should move focus/list (not KEY_UP; repeats only if held).
     * Layman: first click and further clicks while your finger keeps the dial turning count.
     * Technical: ACTION_DOWN; repeatCount==0 always; repeatCount&gt;0 requires {@code keyHeld}.
     */
    public static boolean acceptNotch(int action, int repeatCount, boolean keyHeld) {
        if (action != KeyEvent.ACTION_DOWN) return false;
        if (repeatCount == 0) return true;
        return keyHeld;
    }

    /**
     * 2026-07-20 — Next held flag after this event.
     * Layman: finger down on first click; finger up clears so ghost repeats die.
     * Technical: DOWN+repeat0 → held; UP → clear; other DOWN repeats keep prior held.
     */
    public static boolean heldAfter(int action, int repeatCount, boolean wasHeld) {
        if (action == KeyEvent.ACTION_UP) return false;
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) return true;
        return wasHeld;
    }
}
