package com.solar.launcher.flow;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowViewReflectionTest {

    @Test
    public void reflectionsOnUnlessDebugPref() {
        assertFalse(FlowView.shouldSkipCoverReflection(false));
        assertTrue(FlowView.shouldSkipCoverReflection(true));
    }

    @Test
    public void sideCoverReflectionsOnByDefault() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false));
    }

    @Test
    public void reflectionBaseAlphaIsUniformLowOpacity() {
        float base = FlowView.flowReflectionBaseAlpha();
        assertEquals(base, FlowView.reflectionAlphaForSlot(1f, true), 0.001f);
        assertEquals(base, FlowView.reflectionAlphaForSlot(1f, false), 0.001f);
        assertEquals(base * 0.5f, FlowView.reflectionAlphaForSlot(0.5f, false), 0.001f);
    }

    @Test
    public void reflectionDrawAlphaIgnoresCoverDim() {
        float base = FlowView.flowReflectionBaseAlpha();
        assertEquals(base, FlowView.reflectionAlphaForDraw(1f, 1f, true), 0.001f);
        assertEquals(base, FlowView.reflectionAlphaForDraw(0.25f, 1f, false), 0.001f);
        assertEquals(base, FlowView.reflectionAlphaForDraw(0.47f, 1f, false), 0.001f);
    }

    @Test
    public void reflectionDrawAlphaUniformAcrossSlots() {
        float center = FlowView.reflectionAlphaForDraw(1f, 1f, true);
        float sideLowCover = FlowView.reflectionAlphaForDraw(0.47f, 1f, false);
        float sideMidCover = FlowView.reflectionAlphaForDraw(0.6f, 1f, false);
        assertEquals(center, sideLowCover, 0.001f);
        assertEquals(center, sideMidCover, 0.001f);
    }

    /** 2026-07-20 — Settled carousel: center and side same band count (Y1 matched Y2 gloss). */
    @Test
    public void settledReflectionBandsMatchCenterAndSide() {
        int center = FlowView.reflectionBandCountForSlot(true, false);
        int side = FlowView.reflectionBandCountForSlot(false, false);
        assertEquals(center, side);
        assertEquals(FlowView.CENTER_REFLECTION_MAX_BANDS, center);
    }

    /** 2026-07-20 — Scroll can coarsen every slot together; never center-only fine vs side coarse. */
    @Test
    public void scrollingReflectionBandsUniformAcrossSlots() {
        int center = FlowView.reflectionBandCountForSlot(true, true);
        int side = FlowView.reflectionBandCountForSlot(false, true);
        assertEquals(center, side);
        assertEquals(FlowView.SIDE_REFLECTION_MAX_BANDS, center);
    }

    /** 2026-07-20 — Y1/Y2 share one floor gloss strength (was Y1 half → uneven with coarse sides). */
    @Test
    public void reflectionBaseAlphaMatchesY2Gloss() {
        assertEquals(0.50f, FlowView.flowReflectionBaseAlpha(), 0.001f);
    }

    @Test
    public void scrollingDoesNotSuppressReflections() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, true, true));
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, true, false));
    }

    @Test
    public void handoffDoesNotSuppressReflections() {
        assertFalse(FlowView.shouldSkipCoverReflection(false, true, false, true));
        assertFalse(FlowView.shouldSkipCoverReflection(false, true, false, false));
        assertFalse(FlowView.shouldSkipCoverReflection(false, false, false, true, 0.5f));
    }

    @Test
    public void noReflectionsOverridesAllCovers() {
        assertTrue(FlowView.shouldSkipCoverReflection(true, false, false, true));
        assertTrue(FlowView.shouldSkipCoverReflection(true, false, true, false));
    }
}
