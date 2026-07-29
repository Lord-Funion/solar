package com.solar.launcher;

import android.content.Context;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-19 — Bluetooth PIN digit keyboard inside global :overlay.
 * Layman: type the headset PIN with the wheel only when silent pairing failed.
 * Technical: SolarWheelKeyboardController digit-only; submitPinFromOverlay on enter.
 * Reversal: delete; coordinator can launch MainActivity EXTRA_PAIR_PIN_PROMPT again.
 */
final class OverlayBtPinKeyboard {

    private final Context context;
    private final ViewGroup parent;
    private final Runnable onDismissKeyboardOnly;

    private View shellRoot;
    private SolarKeyboardShellHost shellHost;
    private SolarWheelKeyboardController controller;
    private String targetAddress;
    private String deviceName;
    private String prefill;
    private long playPauseDownAt;

    OverlayBtPinKeyboard(Context context, ViewGroup parent, Runnable onDismissKeyboardOnly) {
        this.context = context.getApplicationContext();
        this.parent = parent;
        this.onDismissKeyboardOnly = onDismissKeyboardOnly;
    }

    boolean isShowing() {
        return shellRoot != null;
    }

    /** Paint digit-only keyboard for legacy PIN pairing. */
    void show(String address, String name, String pinPrefill) {
        dismiss();
        targetAddress = address;
        deviceName = name != null && name.length() > 0 ? name : address;
        prefill = BluetoothAudioRepair.normalizePairingPin(pinPrefill);
        controller = new SolarWheelKeyboardController();
        controller.setGroupedMode(WheelKeyboardLayout.isGrouped(context));
        controller.setPasswordMode(true);
        controller.setDigitOnlyMode(true);
        if (prefill != null && prefill.length() > 0) {
            controller.setBuffer(prefill);
        }
        controller.setListener(new SolarWheelKeyboardController.Listener() {
            @Override
            public void onStateChanged() {
                refreshUi();
            }

            @Override
            public void onEnterRequested() {
                String pin = controller.getBuffer();
                BluetoothPairingCoordinator.submitPinFromOverlay(context, targetAddress, pin);
                dismissKeyboardOnly();
            }
        });

        LayoutInflater inflater = LayoutInflater.from(context);
        shellRoot = inflater.inflate(R.layout.layout_solar_keyboard_shell, parent, false);
        shellHost = new SolarKeyboardShellHost(context, shellRoot,
                context.getString(R.string.keyboard_bt_pairing_pin, deviceName));
        parent.addView(shellRoot, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshUi();
        ThemeManager.ensureOverlayPaintableMinimum(context);
        SolarImeRouteArbiter.setOverlayCredentialActive(true);
    }

    boolean handleKeyDown(int keyCode) {
        if (controller == null || shellRoot == null) return false;
        if (Y1InputKeys.isBackKey(keyCode)) {
            BluetoothPairingCoordinator.cancelPairing(context, targetAddress);
            dismissKeyboardOnly();
            return true;
        }
        if (Y1InputKeys.isWheelUp(keyCode)) {
            controller.wheelUp();
            return true;
        }
        if (Y1InputKeys.isWheelDown(keyCode)) {
            controller.wheelDown();
            return true;
        }
        if (Y1InputKeys.isCenterKey(keyCode)) {
            controller.centerPress();
            return true;
        }
        if (Y1InputKeys.isPlayPauseKey(keyCode)) {
            if (playPauseDownAt == 0L) playPauseDownAt = SystemClock.uptimeMillis();
            return true;
        }
        if (Y1InputKeys.isTrackPreviousKey(keyCode)) {
            controller.mediaDelete();
            return true;
        }
        if (Y1InputKeys.isTrackNextKey(keyCode)) {
            return true;
        }
        return false;
    }

    boolean handleKeyUp(int keyCode) {
        if (!isShowing()) return false;
        if (Y1InputKeys.isPlayPauseKey(keyCode)) {
            playPauseDownAt = 0L;
            controller.requestEnter();
            return true;
        }
        return Y1InputKeys.isCenterKey(keyCode) || Y1InputKeys.isBackKey(keyCode)
                || Y1InputKeys.isWheelKey(keyCode)
                || Y1InputKeys.isTrackPreviousKey(keyCode)
                || Y1InputKeys.isTrackNextKey(keyCode);
    }

    void dismiss() {
        SolarImeRouteArbiter.setOverlayCredentialActive(false);
        if (shellRoot != null && parent != null) {
            try {
                parent.removeView(shellRoot);
            } catch (Exception ignored) {}
        }
        shellRoot = null;
        shellHost = null;
        controller = null;
        targetAddress = null;
        deviceName = null;
        prefill = null;
        playPauseDownAt = 0L;
    }

    private void dismissKeyboardOnly() {
        dismiss();
        if (onDismissKeyboardOnly != null) {
            onDismissKeyboardOnly.run();
        }
    }

    private void refreshUi() {
        if (shellHost == null || controller == null) return;
        // 2026-07-20 — Status = clock; no PIN subtitle; default 0000 only as empty-field hint.
        shellHost.applyShellTheme("", true);
        String buffer = controller.getBuffer();
        boolean empty = buffer == null || buffer.length() == 0;
        String input = empty ? "0000" : controller.renderBuffer(true);
        shellHost.getKeyboardUi().refresh(controller, null, input, empty);
    }
}
