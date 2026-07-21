package com.solar.launcher;

import android.content.Context;

/**
 * 2026-07-19 — Disabled: must not BACK away stock {@code UsbStorageActivity}.
 * Layman: used to steal focus from Android’s USB screen; now a no-op stub.
 * Tech: {@link #ensureRunning} returns immediately. Asset script kept for ROM cleanup.
 * Reversal: restore shell loop from git (bring_solar_home on UsbStorageActivity).
 */
public final class UsbRecoveryAgent {

    private UsbRecoveryAgent() {}

    /**
     * No-op — stock Android USB UI must stay visible.
     * Call sites kept so boot/start paths still compile.
     */
    public static void ensureRunning(final Context context) {
        // Intentionally empty — do not start solar-usb-recovery-agent.sh.
    }
}
