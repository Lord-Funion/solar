package com.solar.launcher.stem;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-20 — Normal play must not auto-start dual-pad transport layers.
 * Was: maybeEnterSoloLayerMixAfterPrepare → original + stems both audible.
 */
public class SoloLayerMixPolicyTest {

    @Test
    public void neverAutoEnterDualMixJustBecauseStemsExist() {
        assertFalse(SoloLayerMixPolicy.shouldAutoEnterAfterPrepare(true));
        assertFalse(SoloLayerMixPolicy.shouldAutoEnterAfterPrepare(false));
    }

    @Test
    public void explicitSoloMenusStillAllowedToEnter() {
        // Policy only gates auto-prepare; Play Instrumental / Acapella call trySoloLayerMix directly.
        assertTrue(SoloLayerMixPolicy.allowsExplicitSoloEntry());
    }
}
