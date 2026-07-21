package com.solar.launcher.ui;

import org.junit.Test;

/**
 * 2026-07-20 — RowBusyChrome constants / contract (no Android View in unit test).
 * Layman: prove the shared spinner tag and size stay stable for adapters.
 */
public class RowBusyChromeTest {

    @Test
    public void spinTagAndSizeStable() {
        if (RowBusyChrome.TAG_SPIN == null) throw new AssertionError("tag");
        if (RowBusyChrome.SPIN_DP < 14 || RowBusyChrome.SPIN_DP > 24) {
            throw new AssertionError("spin dp out of small-chrome range: " + RowBusyChrome.SPIN_DP);
        }
    }
}
