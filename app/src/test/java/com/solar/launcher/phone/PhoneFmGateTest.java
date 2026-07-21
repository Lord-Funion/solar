package com.solar.launcher.phone;

import org.junit.Test;

/**
 * 2026-07-20 — FM phone gate decisions.
 */
public class PhoneFmGateTest {

    @Test
    public void passthroughWhenChromeOff() {
        PhoneFmGate.Decision d = PhoneFmGate.decide(false, "goldfish", "Google");
        if (d != PhoneFmGate.Decision.PASSTHROUGH) {
            throw new AssertionError("expected PASSTHROUGH got " + d);
        }
    }

    @Test
    public void blockNonMtkPhone() {
        PhoneFmGate.Decision d = PhoneFmGate.decide(true, "qcom", "Samsung");
        if (d != PhoneFmGate.Decision.BLOCK_NON_MTK) {
            throw new AssertionError("expected BLOCK got " + d);
        }
        d = PhoneFmGate.decide(true, "goldfish", "Google");
        if (d != PhoneFmGate.Decision.BLOCK_NON_MTK) {
            throw new AssertionError("emulator BLOCK got " + d);
        }
    }

    @Test
    public void warnOnMtkPhone() {
        PhoneFmGate.Decision d = PhoneFmGate.decide(true, "mt6572", "MediaTek");
        if (d != PhoneFmGate.Decision.WARN_THEN_ALLOW) {
            throw new AssertionError("mt6572 warn got " + d);
        }
        d = PhoneFmGate.decide(true, "MT6582", "");
        if (d != PhoneFmGate.Decision.WARN_THEN_ALLOW) {
            throw new AssertionError("MT6582 warn got " + d);
        }
        if (!PhoneFmGate.looksLikeMediaTek("mt6735", "alps")) {
            throw new AssertionError("mt6735 should count");
        }
    }
}
