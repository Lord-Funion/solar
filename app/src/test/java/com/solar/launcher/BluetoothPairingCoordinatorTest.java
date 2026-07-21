package com.solar.launcher;

import android.bluetooth.BluetoothDevice;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Unit tests for global Bluetooth pairing coordinator routing (2026-07-05 / 2026-07-19). */
public class BluetoothPairingCoordinatorTest {

    @Test
    public void testFormatPasskey() {
        assertEquals("012345", BluetoothPairingCoordinator.formatPasskey(12345));
        assertEquals("000042", BluetoothPairingCoordinator.formatPasskey(42));
        assertEquals("000000", BluetoothPairingCoordinator.formatPasskey(-1));
    }

    @Test
    public void testOverlayModeForVariant() {
        assertEquals(BluetoothPairingCoordinator.MODE_PIN,
                BluetoothPairingCoordinator.overlayModeForVariant(
                        BluetoothDevice.PAIRING_VARIANT_PIN));
        assertEquals(BluetoothPairingCoordinator.MODE_PASSKEY_DISPLAY,
                BluetoothPairingCoordinator.overlayModeForVariant(1));
        assertEquals(BluetoothPairingCoordinator.MODE_PASSKEY_CONFIRM,
                BluetoothPairingCoordinator.overlayModeForVariant(
                        BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION));
        assertEquals(BluetoothPairingCoordinator.MODE_CONSENT,
                BluetoothPairingCoordinator.overlayModeForVariant(3));
    }

    @Test
    public void testNegotiationWindowIs15s() {
        assertEquals(15_000L, BluetoothPairingCoordinator.NEGOTIATION_WINDOW_MS);
    }

    @Test
    public void testSelfCheck() {
        BluetoothPairingCoordinator.selfCheck();
        SolarWheelKeyboardController.selfCheck();
    }

    @Test
    public void testSessionClear() {
        BluetoothPairingCoordinator.clearSession();
        assertFalse(BluetoothPairingCoordinator.onPairingRequest(
                null, null, BluetoothDevice.PAIRING_VARIANT_PIN, 0, false));
    }

    @Test
    public void testPendingPinClearedOnBondedNull() {
        BluetoothPairingCoordinator.clearSession();
        BluetoothPairingCoordinator.onBonded(null);
        assertFalse(BluetoothPairingCoordinator.isPendingPinNegotiation("AA:BB:CC:DD:EE:FF"));
    }
}
