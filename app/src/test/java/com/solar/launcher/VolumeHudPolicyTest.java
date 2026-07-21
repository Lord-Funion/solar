package com.solar.launcher;

import org.junit.Test;

/**
 * 2026-07-15 — Y2/A5 compact volume HUD vs inline NP/video pulse.
 */
public class VolumeHudPolicyTest {

    @Test
    public void y2A5_skipsHudOnNowPlayingWithoutModal() {
        if (VolumeHudPolicy.shouldShowCompactVolumeHud(true, true, false)) {
            throw new AssertionError("NP without modal should skip HUD");
        }
    }

    @Test
    public void y2A5_showsHudWhenModalOpenOnNowPlaying() {
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(true, true, true)) {
            throw new AssertionError("modal open should show/replace volume HUD");
        }
    }

    @Test
    public void y2A5_showsHudOnBrowse() {
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(true, false, false)) {
            throw new AssertionError("browse should keep compact HUD");
        }
    }

    @Test
    public void nonHwVolumeDevice_alwaysShowsWhenAsked() {
        // Y1 / non-family: policy does not suppress (caller may still gate via shouldUseSolarVolumeHud).
        if (!VolumeHudPolicy.shouldShowCompactVolumeHud(false, true, false)) {
            throw new AssertionError("non-hw device should not suppress via this policy");
        }
    }

    @Test
    public void inlineVolumeScreen_playerAndVideo() {
        if (!VolumeHudPolicy.isInlineVolumeScreen(3, 3, 21)) {
            throw new AssertionError("player");
        }
        if (!VolumeHudPolicy.isInlineVolumeScreen(21, 3, 21)) {
            throw new AssertionError("video");
        }
        if (VolumeHudPolicy.isInlineVolumeScreen(0, 3, 21)) {
            throw new AssertionError("menu");
        }
    }

    @Test
    public void inlineVolumeScreen_stemAndMix() {
        // STATE_STEM_PLAYER=33, STATE_MIX=34
        if (!VolumeHudPolicy.isInlineVolumeScreen(33, 3, 21, 33, 34)) {
            throw new AssertionError("stem");
        }
        if (!VolumeHudPolicy.isInlineVolumeScreen(34, 3, 21, 33, 34)) {
            throw new AssertionError("mix");
        }
        if (VolumeHudPolicy.isInlineVolumeScreen(2, 3, 21, 33, 34)) {
            throw new AssertionError("browser");
        }
    }

    /** 2026-07-20 — Y2/A5/emulator always own Solar volume HUD path (not family-prop-only). */
    @Test
    public void y2AndA5_useSolarVolumeHudWithoutFamilyProp() {
        if (!VolumeHudPolicy.shouldUseSolarVolumeHud(true, false, false, "")) {
            throw new AssertionError("Y2");
        }
        if (!VolumeHudPolicy.shouldUseSolarVolumeHud(false, true, false, "")) {
            throw new AssertionError("A5");
        }
        if (!VolumeHudPolicy.shouldUseSolarVolumeHud(false, false, true, "")) {
            throw new AssertionError("emulator");
        }
        if (VolumeHudPolicy.shouldUseSolarVolumeHud(false, false, false, "")) {
            throw new AssertionError("unknown device without family prop");
        }
        if (!VolumeHudPolicy.shouldUseSolarVolumeHud(false, false, false, "y2")) {
            throw new AssertionError("family y2");
        }
    }

    /** 2026-07-20 — In-app compact volume bar must not paint on NP when HW volume device. */
    @Test
    public void y2A5_mustNotShowCompactHudOnNowPlaying() {
        if (VolumeHudPolicy.shouldShowCompactVolumeHud(true, true, false)) {
            throw new AssertionError("NP compact HUD must stay off");
        }
    }
}
