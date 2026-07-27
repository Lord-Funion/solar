package com.solar.launcher;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-11 — Edge-only ensure-visible scroll math for FocusScrollHelper.
 * Layman: page only moves when the highlight would leave the screen.
 * Technical: computeEnsureVisibleScrollY + computeListSelectionTop + sticky-slot cases.
 * 2026-07-11 — Natural sticky slot (last/first fully-visible top) + no pad pre-scroll.
 * 2026-07-11 — Ticker contract: instant sticky pin (no smoothScroll highlight shunt).
 */
public class FocusScrollHelperTest {

    @Test
    public void ensureVisible_childFullyVisible_unchanged() {
        assertEquals(0, FocusScrollHelper.computeEnsureVisibleScrollY(
                0, 100, 400, 20, 50, 2));
        assertEquals(100, FocusScrollHelper.computeEnsureVisibleScrollY(
                100, 100, 400, 120, 150, 2));
    }

    @Test
    public void fullyVisible_midList_noViewportMove() {
        // 2026-07-20 — 1 tick = 1 focus; list stays put while row is on screen.
        assertTrue(FocusScrollHelper.isFullyVisibleInScroll(0, 200, 40, 80));
        assertTrue(FocusScrollHelper.isFullyVisibleInScroll(100, 200, 120, 160));
        assertTrue(!FocusScrollHelper.isFullyVisibleInScroll(0, 200, 180, 220));
        assertTrue(!FocusScrollHelper.isFullyVisibleInScroll(50, 200, 40, 80));
    }

    @Test
    public void ensureVisible_withinPadOfEdge_noPreScroll() {
        // GIF: no pre-scroll — row fully on screen but inside pad inset must stay put.
        assertEquals(0, FocusScrollHelper.computeEnsureVisibleScrollY(
                0, 100, 400, 1, 40, 2));
        assertEquals(0, FocusScrollHelper.computeEnsureVisibleScrollY(
                0, 100, 400, 60, 99, 2));
    }

    @Test
    public void ensureVisible_clipsTop_scrollsUp() {
        assertEquals(8, FocusScrollHelper.computeEnsureVisibleScrollY(
                40, 100, 400, 10, 40, 2));
    }

    @Test
    public void ensureVisible_clipsBottom_scrollsDown() {
        assertEquals(62, FocusScrollHelper.computeEnsureVisibleScrollY(
                0, 100, 400, 130, 160, 2));
    }

    @Test
    public void ensureVisible_clampsToContentBounds() {
        assertEquals(0, FocusScrollHelper.computeEnsureVisibleScrollY(
                0, 100, 400, 0, 30, 2));
        assertEquals(300, FocusScrollHelper.computeEnsureVisibleScrollY(
                250, 100, 400, 380, 410, 2));
    }

    @Test
    public void ensureVisible_zeroViewport_unchanged() {
        assertEquals(50, FocusScrollHelper.computeEnsureVisibleScrollY(
                50, 0, 400, 0, 30, 2));
    }

    @Test
    public void listSelection_fullyVisible_keepsTop() {
        // viewport 300, row at 90..135 — keep 90 (no scroll)
        assertEquals(90, FocusScrollHelper.computeListSelectionTop(
                300, 90, 135, true, false, 45, 2));
        // Flush to top edge still fully visible — keep 0 (do not re-pin to pad)
        assertEquals(0, FocusScrollHelper.computeListSelectionTop(
                300, 0, 45, true, false, 45, 2));
    }

    @Test
    public void listSelection_secondRowStillVisible_noJumpToTop() {
        // Regression test: sticky-Y used to put row 1 at Y=0; edge-only keeps Y=45
        assertEquals(45, FocusScrollHelper.computeListSelectionTop(
                300, 45, 90, true, false, 45, 2));
    }

    @Test
    public void listSelection_clipsBottom_pinsToBottomEdge() {
        // No natural slot → fallback viewport-rowH-pad = 253
        assertEquals(253, FocusScrollHelper.computeListSelectionTop(
                300, 270, 315, true, false, 45, 2));
    }

    @Test
    public void listSelection_clipsBottom_keepsNaturalSlot() {
        // Last fully-visible row sat at 240 — sticky stays 240 (GIF: no jump to 253).
        assertEquals(240, FocusScrollHelper.computeListSelectionTop(
                300, 270, 315, true, false, 45, 2, 240, true));
        // Continual down: same natural slot every tick.
        assertEquals(240, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, false, 45, 2, 240, true));
    }

    @Test
    public void listSelection_aboveWindow_pinsTop() {
        assertEquals(2, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, true, 45, 2));
    }

    @Test
    public void listSelection_aboveWindow_keepsNaturalTopSlot() {
        // First fully-visible was at 0 — stay at 0 (not pad 2) during continual up.
        assertEquals(0, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, true, 45, 2, 0, true));
    }

    @Test
    public void listSelection_belowWindow_pinsBottom() {
        assertEquals(253, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, false, 45, 2));
    }

    @Test
    public void stickyEdge_bottomSlot_stableAcrossTicks() {
        // Continual down at bottom: same Y every tick (highlight stays put).
        assertEquals(253, FocusScrollHelper.computeStickyEdgeTop(300, 45, 2, true));
        assertEquals(253, FocusScrollHelper.computeStickyEdgeTop(300, 45, 2, true));
        // Below-window path must match sticky bottom slot.
        assertEquals(253, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, false, 45, 2));
    }

    @Test
    public void stickySlot_naturalBottom_stableAcrossTicks() {
        assertEquals(240, FocusScrollHelper.computeStickySlotTop(300, 240, true, 45, 2, true));
        assertEquals(240, FocusScrollHelper.computeStickySlotTop(300, 240, true, 45, 2, true));
        assertEquals(240, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, false, 45, 2, 240, true));
    }

    @Test
    public void stickyEdge_topSlot_stableAcrossTicks() {
        // Continual up at top: pad Y every tick.
        assertEquals(2, FocusScrollHelper.computeStickyEdgeTop(300, 45, 2, false));
        assertEquals(2, FocusScrollHelper.computeStickyEdgeTop(300, 45, 2, false));
        assertEquals(2, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, true, 45, 2));
    }

    @Test
    public void stickyEdge_bottomUsesRowHeight_notFirstHintMismatch() {
        // Variable rows: pin uses actual row H (60), not a shorter hint.
        assertEquals(238, FocusScrollHelper.computeStickyEdgeTop(300, 60, 2, true));
        assertEquals(238, FocusScrollHelper.computeListSelectionTop(
                300, 280, 340, true, false, 45, 2));
    }

    @Test
    public void stickySlot_tallRow_fitsInViewport() {
        // Natural slot 240 but next row is 80px — pull up so it fits (300-80=220).
        assertEquals(220, FocusScrollHelper.computeStickySlotTop(300, 240, true, 80, 2, true));
    }

    @Test
    public void stickyEdge_zeroPad_flushBottom() {
        assertEquals(255, FocusScrollHelper.computeStickyEdgeTop(300, 45, 0, true));
        assertEquals(0, FocusScrollHelper.computeStickyEdgeTop(300, 45, 0, false));
    }

    @Test
    public void preferInstantScroll_tickerAlwaysInstant() {
        // 2026-07-11 — Ticker contract: never glide; highlight stays put via setSelectionFromTop.
        assertTrue(FocusScrollHelper.preferInstantScroll(1050L, 1000L, 110));
        assertTrue(FocusScrollHelper.preferInstantScroll(1120L, 1000L, 110));
        assertTrue(FocusScrollHelper.preferInstantScroll(1000L, 0L, 110));
    }

    @Test
    public void preferInstantScroll_noPrior_stillInstant() {
        assertTrue(FocusScrollHelper.preferInstantScroll(1000L, 0L, 110));
        assertTrue(FocusScrollHelper.preferInstantScroll(1000L, -1L, 110));
        assertTrue(FocusScrollHelper.preferInstantScroll(1000L, 900L, 0));
    }

    @Test
    public void focusScrollDuration_tickerIsInstant() {
        // 2026-07-11 — No PositionScroller / smoothScrollBy; duration is 0.
        assertEquals(0, FocusScrollHelper.focusScrollDurationMs());
        assertEquals(0, FocusScrollHelper.rapidWheelScrollMs());
    }

    @Test
    public void listWheelMidSpinSkipsFocusKeepsViewport() {
        // 2026-07-21 — Contract with ListWheelChromePolicy: spin pins viewport, idle focuses.
        // Android ListView path is integration-tested on device; policy flags gate the overload.
        ListWheelChromePolicy.PaintPlan spin = ListWheelChromePolicy.plan(true);
        assertTrue(spin.ensureVisible);
        assertFalse(spin.requestFocus);
        ListWheelChromePolicy.PaintPlan idle = ListWheelChromePolicy.plan(false);
        assertTrue(idle.ensureVisible);
        assertTrue(idle.requestFocus);
    }

    @Test
    public void stickySlot_easeIntoEdge_deltaMatchesClip() {
        // 2026-07-11 — Laid-out clip bottom: child top 270 → sticky 240 (ticker pin).
        int sticky = FocusScrollHelper.computeListSelectionTop(
                300, 270, 315, true, false, 45, 2, 240, true);
        assertEquals(240, sticky);
        assertEquals(30, 270 - sticky);
        // Continual edge: same sticky Y every tick (highlight fixed, content ticks).
        assertEquals(240, FocusScrollHelper.computeListSelectionTop(
                300, 0, 0, false, false, 45, 2, 240, true));
    }
}
