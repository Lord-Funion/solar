package com.solar.launcher;

/**
 * 2026-07-20 — What chrome to paint on one home/settings wheel detent.
 * Layman: the dial moves which row is selected; the page only shifts if that row is off-screen.
 * Technical: mid-spin = focus/styles only (no viewport chase); idle = focus/status/preview.
 * Was: ensureVisible=true mid-spin after misreading “scrolling” as viewport scroll.
 * Reversal: plan(spinning) with ensureVisible=true again.
 */
public final class MenuWheelChromePolicy {

    /** Which follow-up paints run for this detent. 2026-07-20 */
    public static final class PaintPlan {
        /**
         * Whether paint paths may call ensure-visible.
         * Mid-spin: false — KEY path owns rare edge reveal; do not chase the viewport.
         */
        public final boolean ensureVisible;
        public final boolean focusAndStatus;
        public final boolean preview;

        public PaintPlan(boolean ensureVisible, boolean focusAndStatus, boolean preview) {
            this.ensureVisible = ensureVisible;
            this.focusAndStatus = focusAndStatus;
            this.preview = preview;
        }
    }

    private MenuWheelChromePolicy() {}

    /**
     * 2026-07-20 — Mid-spin: no paint-path viewport work. Idle: full chrome.
     * Layman: while turning, only the highlight hops; covers/title wait for a pause.
     */
    public static PaintPlan plan(boolean spinning) {
        if (spinning) {
            return new PaintPlan(false, false, false);
        }
        return new PaintPlan(true, true, true);
    }

    /**
     * 2026-07-20 — Short menus must not schedule a delayed paint after KEY.
     * Layman: the row already moved on the click — no second “catch-up” redraw.
     * Technical: was menuWheelCoalescer.offerSteps after mover.move. Reversal: return true.
     */
    public static boolean pacePaintAfterKey() {
        return false;
    }
}
