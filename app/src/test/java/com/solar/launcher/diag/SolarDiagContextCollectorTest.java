package com.solar.launcher.diag;

import org.junit.Test;

/**
 * 2026-07-20 — Env dump must not call BluetoothDevice.getType on API 17 (Y1).
 * That method is API 18+; NoSuchMethodError used to abort the whole report ship.
 */
public class SolarDiagContextCollectorTest {

    @Test
    public void bluetoothDeviceTypeUnsupportedOnApi17() {
        if (SolarDiagContextCollector.supportsBluetoothDeviceType(17)) {
            throw new AssertionError("getType must be skipped on API 17");
        }
    }

    @Test
    public void bluetoothDeviceTypeSupportedOnApi18() {
        if (!SolarDiagContextCollector.supportsBluetoothDeviceType(18)) {
            throw new AssertionError("getType should be used on API 18+");
        }
    }

    @Test
    public void bluetoothDeviceTypeSupportedOnApi19() {
        if (!SolarDiagContextCollector.supportsBluetoothDeviceType(19)) {
            throw new AssertionError("getType should be used on API 19 (Y2)");
        }
    }

    @Test
    public void formatBluetoothDeviceTypeOmitsWhenUnsupported() {
        String s = SolarDiagContextCollector.formatBluetoothDeviceType(17, 1);
        if (s != null) {
            throw new AssertionError("API 17 must omit type field, got: " + s);
        }
    }

    @Test
    public void formatBluetoothDeviceTypeIncludesWhenSupported() {
        String s = SolarDiagContextCollector.formatBluetoothDeviceType(18, 1);
        if (!"1".equals(s)) {
            throw new AssertionError("expected type=1 string, got: " + s);
        }
    }

    @Test
    public void formatBluetoothDeviceTypeOmitsNullType() {
        String s = SolarDiagContextCollector.formatBluetoothDeviceType(19, null);
        if (s != null) {
            throw new AssertionError("null type (call failed) must omit field");
        }
    }
}
