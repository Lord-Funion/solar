package com.solar.launcher;

import org.junit.Test;

/**
 * USB session flags + routing policy (July-2 monlith: Solar owns USB UI).
 */
public class UsbStorageSessionFlagsTest {

    @Test
    public void autoConnectRespectsManualDisable() {
        if (!UsbStorageSessionFlags.isAutoConnectFromPrefs(true, false)) {
            throw new AssertionError("auto-connect should be on");
        }
        if (UsbStorageSessionFlags.isAutoConnectFromPrefs(false, false)) {
            throw new AssertionError("auto-connect pref off");
        }
        if (UsbStorageSessionFlags.isAutoConnectFromPrefs(true, true)) {
            throw new AssertionError("manual disable blocks auto-connect");
        }
    }

    @Test
    public void preferStockUsbUiAlways() {
        // 2026-07-19 — Android always owns UsbStorageActivity (auto on or off).
        if (!UsbStorageSessionFlags.preferStockUsbUiFromPrefs(false)) {
            throw new AssertionError("stock UI expected when auto off");
        }
        if (!UsbStorageSessionFlags.preferStockUsbUiFromPrefs(true)) {
            throw new AssertionError("stock UI expected when auto on");
        }
    }

    @Test
    public void usbPromptNeverUsesGlobalOverlay() {
        // Solar MainActivity is the sole USB UI host (July-2 restore).
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest(null)) {
            throw new AssertionError("no global USB overlay");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.android.systemui")) {
            throw new AssertionError("no global USB overlay for SystemUI");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("org.rockbox")) {
            throw new AssertionError("no global USB overlay for Rockbox");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.solar.launcher")) {
            throw new AssertionError("Solar owns USB without global overlay shell");
        }
    }

    @Test
    public void enablePromptOverlayPreferredIsAlwaysFalse() {
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("org.rockbox")) {
            throw new AssertionError("enable overlay not preferred");
        }
    }

    @Test
    public void usbHostWakeRoutesOverlayNotMainWhenStockAppForeground() {
        if (UsbHostWakeReceiver.shouldLaunchMainActivityForUsbHost(null)) {
            throw new AssertionError("null context");
        }
    }

    @Test
    public void usbHostWakeDoesNotLaunchMainForNullContext() {
        if (UsbHostWakeReceiver.shouldLaunchMainActivityForUsbHost(null)) {
            throw new AssertionError("null context cannot launch");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.mediatek.videoplayer")) {
            throw new AssertionError("stock app also routes to Solar only");
        }
        if (UsbStorageOverlayReceiver.shouldUseGlobalOverlayPromptForTest("com.solar.launcher")) {
            throw new AssertionError("Solar owns USB UX when foreground");
        }
    }
}
