package com.solar.launcher.media;

import org.junit.Test;

/**
 * 2026-07-20 — NP tip ladder gating (volume → Options → Flow → none).
 */
public class NowPlayingTipPolicyTest {

    @Test
    public void volumeFirst() {
        if (!"VOLUME_ARROWS".equals(
                NowPlayingTipPolicy.playerHintMode(false, false, false, true))) {
            throw new AssertionError("volume first");
        }
    }

    @Test
    public void optionsAfterVolume() {
        if (!"HOLD_BACK_OPTIONS".equals(
                NowPlayingTipPolicy.playerHintMode(true, false, false, true))) {
            throw new AssertionError("options");
        }
    }

    @Test
    public void flowAfterOptionsWhenEnabled() {
        if (!"HOLD_PLAY_FLOW".equals(
                NowPlayingTipPolicy.playerHintMode(true, true, false, true))) {
            throw new AssertionError("flow");
        }
        if (!"NONE".equals(
                NowPlayingTipPolicy.playerHintMode(true, true, false, false))) {
            throw new AssertionError("flow off");
        }
    }

    @Test
    public void noneWhenAllUsed() {
        if (!"NONE".equals(
                NowPlayingTipPolicy.playerHintMode(true, true, true, true))) {
            throw new AssertionError("done");
        }
    }

    @Test
    public void compatMapsOldPrefs() {
        if (!"VOLUME_ARROWS".equals(
                NowPlayingTipPolicy.playerHintModeCompat(false, false, false, true))) {
            throw new AssertionError("compat vol");
        }
        if (!"HOLD_PLAY_FLOW".equals(
                NowPlayingTipPolicy.playerHintModeCompat(true, true, false, true))) {
            throw new AssertionError("compat flow");
        }
    }
}
