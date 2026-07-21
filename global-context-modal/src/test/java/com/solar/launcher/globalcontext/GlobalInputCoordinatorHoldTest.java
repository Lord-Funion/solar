package com.solar.launcher.globalcontext;

import com.solar.input.policy.GlobalInputPolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-10 — Documents Y2 POWER hold arming contract for GlobalInputCoordinatorService.
 * Layman: a brand-new finger-down must always start the hold timer — not be treated as a tap.
 */
public final class GlobalInputCoordinatorHoldTest {

    @Test
    public void passthroughAtZeroMsIsTrueForUpPathOnly() {
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(0L));
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(100L));
        assertFalse(GlobalInputPolicy.shouldPassthroughPowerTap(350L));
    }

    /** 2026-07-14 — Solar-only POWER menu; 3P/Rockbox use stock GlobalActions. */
    @Test
    public void thirdPartyPackagesNotEligibleForPowerModalOnY2() {
        assertFalse(GlobalInputPolicy.shouldOfferPowerLongModal("com.android.settings", true));
        assertFalse(GlobalInputPolicy.shouldOfferPowerLongModal("org.rockbox", true));
        assertTrue(GlobalInputPolicy.shouldOfferPowerLongModal("com.solar.launcher", true));
        assertTrue(GlobalInputPolicy.shouldFailOpenPowerFg("com.android.systemui"));
    }

    @Test
    public void holdDownArmsModalTierNotPassthroughGate() {
        // Contract: passthrough is evaluated on UP at holdMs; DOWN must never skip arming.
        assertTrue(GlobalInputPolicy.shouldPassthroughPowerTap(0L));
        assertEquals(280L, GlobalInputPolicy.powerModalHoldMsForPackage("com.android.settings"));
        assertEquals(300L, GlobalInputPolicy.powerModalHoldMsForPackage("org.rockbox"));
    }
}
