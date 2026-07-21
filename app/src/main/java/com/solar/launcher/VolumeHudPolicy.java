package com.solar.launcher;

/**
 * 2026-07-15 — When the compact volume HUD should paint on Y2/A5.
 * Layman: on Now Playing / video, the progress bar already shows volume — skip the pop-up unless a
 * context menu is already open (then swap that menu for the small volume bar).
 * Technical: gate for MainActivity Solar volume HUD; modal-open still shows/replaces volume-only.
 * Reversal: always return true (old A5/emulator always-on HUD).
 */
public final class VolumeHudPolicy {

    private VolumeHudPolicy() {}

    /**
     * 2026-07-20 — Devices that route volume keys through Solar’s HUD / inline pulse.
     * Layman: Y2 and A5 have real volume buttons — Solar paints (or pulses) volume itself.
     * Was: MainActivity only checked A5/emulator + family prop (Y2 missed when prop unset).
     * Reversal: drop y2Device arg; require family prop for Y2 again.
     */
    public static boolean shouldUseSolarVolumeHud(boolean y2Device, boolean a5Device,
            boolean emulator, String deviceFamilyProp) {
        if (y2Device || a5Device || emulator) return true;
        if (deviceFamilyProp == null) return false;
        return "y1".equals(deviceFamilyProp) || "y2".equals(deviceFamilyProp)
                || "a5".equals(deviceFamilyProp);
    }

    /**
     * @param hardwareVolumeDevice Y2 / A5 / emulator (devices with HW volume keys using Solar HUD)
     * @param nowPlayingOrVideo {@code STATE_PLAYER} or video player showing transport volume pulse
     * @param contextModalOpen in-app ThemedContextMenu or global chip overlay is painted
     */
    public static boolean shouldShowCompactVolumeHud(boolean hardwareVolumeDevice,
            boolean nowPlayingOrVideo, boolean contextModalOpen) {
        if (!hardwareVolumeDevice) return true;
        if (contextModalOpen) return true;
        return !nowPlayingOrVideo;
    }

    /** Screen ids that pulse volume on the transport bar (music/radio/podcast NP + video). */
    public static boolean isInlineVolumeScreen(int screenState, int playerState, int videoPlayerState) {
        return screenState == playerState || screenState == videoPlayerState;
    }

    /**
     * Stem / Mix own pad gains — skip compact volume HUD (same idea as NP inline).
     * 2026-07-19
     */
    public static boolean isInlineVolumeScreen(int screenState, int playerState,
            int videoPlayerState, int stemPlayerState, int mixPlayerState) {
        return screenState == playerState || screenState == videoPlayerState
                || screenState == stemPlayerState || screenState == mixPlayerState;
    }
}
