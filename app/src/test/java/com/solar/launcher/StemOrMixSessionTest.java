package com.solar.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

/** Stem/Mix exclusive gate — jam blocks IME arm. 2026-07-19 */
public class StemOrMixSessionTest {

    @After
    public void tearDown() {
        StemOrMixSession.resetActiveForTest();
        StemOrMixSession.setActive(false);
    }

    @Test
    public void stemMixBlocksImeArm() {
        StemOrMixSession.setActiveForTest(true);
        assertTrue(StemOrMixSession.isActive());
        assertFalse(SolarImeRouteArbiter.canArm());
        StemOrMixSession.resetActiveForTest();
        StemOrMixSession.setActive(false);
    }

    @Test
    public void activePropertyConstant() {
        assertTrue(StemOrMixSession.ACTIVE_PROPERTY.contains("stemmix"));
    }

    @Test
    public void activeFlag() {
        StemOrMixSession.setActive(false);
        assertFalse(StemOrMixSession.isActive());
        StemOrMixSession.setActive(true);
        assertTrue(StemOrMixSession.isActive());
        StemOrMixSession.setActive(false);
        assertFalse(StemOrMixSession.isActive());
    }

    @Test
    public void sanitizePlayerReturnRejectsStemAndMix() {
        // 2026-07-19 — Stem=33 Mix=34 Menu=0 stand-ins match MainActivity constants in spirit.
        assertEquals(0, StemOrMixSession.sanitizePlayerReturnScreen(33, 33, 34, 0));
        assertEquals(0, StemOrMixSession.sanitizePlayerReturnScreen(34, 33, 34, 0));
        assertEquals(5, StemOrMixSession.sanitizePlayerReturnScreen(5, 33, 34, 0));
    }
}
