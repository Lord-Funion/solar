package com.solar.launcher.media;

import org.junit.Test;

/**
 * 2026-07-20 — Transport tip must fit above scrub; live hold tip stays readable.
 * Layman: the “hold Back / Play” line above the progress bar must not be sliced.
 */
public class TransportHintChromePolicyTest {

    @Test
    public void crampedTransport_failsFit() {
        // Was: 48 transport − 34 scrub = 14 &lt; 20 hint min → TextView AT_MOST clips glyphs.
        if (TransportHintChromePolicy.hintFitsInTransport(48, 34, 20)) {
            throw new AssertionError("48dp bar must fail fit for 20dp tip");
        }
    }

    @Test
    public void requiredHeight_fitsHintAboveScrub() {
        int need = TransportHintChromePolicy.requiredTransportHeight(34, 32);
        if (need != 66) throw new AssertionError("34+32=66, got " + need);
        if (!TransportHintChromePolicy.hintFitsInTransport(need, 34, 32)) {
            throw new AssertionError("required height must fit");
        }
    }

    @Test
    public void hideDelay_waitsUntilMinVisible() {
        if (TransportHintChromePolicy.hideDelayMs(1000L, 1100L, 900L) != 800L) {
            throw new AssertionError("100ms shown → wait 800 more");
        }
        if (TransportHintChromePolicy.hideDelayMs(1000L, 2000L, 900L) != 0L) {
            throw new AssertionError("already past min → hide now");
        }
    }

    @Test
    public void hideDelay_clampsWeirdClock() {
        if (TransportHintChromePolicy.hideDelayMs(5000L, 1000L, 900L) != 900L) {
            throw new AssertionError("clock skew → full min");
        }
    }
}
