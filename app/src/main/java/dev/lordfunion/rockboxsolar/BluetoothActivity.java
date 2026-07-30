package dev.lordfunion.rockboxsolar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public final class BluetoothActivity extends Activity {
    private BluetoothAdapter bluetooth;
    private AppUi.Screen ui;
    private ArrayAdapter<String> adapter;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
    private final Set<String> seen = new HashSet<String>();
    private boolean receiverRegistered;
    private BluetoothProfile a2dp;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && seen.add(safeAddress(device))) devices.add(device);
            }
            reload();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        ui = AppUi.screen(this, "Bluetooth", "Discovery, pairing, unpairing, and A2DP connection controls");
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ui.list.setAdapter(adapter);
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) toggle(); else if (position == 1) discover();
                else if (position == 2) openVisibility();
                else { int index = position - 3; if (index >= 0 && index < devices.size()) showDevice(devices.get(index)); }
            }
        });
        requestPermissionsIfNeeded();
        if (bluetooth != null) {
            try { bluetooth.getProfileProxy(this, profileListener, BluetoothProfile.A2DP); } catch (Exception ignored) { }
        }
        reload();
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override public void onServiceConnected(int profile, BluetoothProfile proxy) { if (profile == BluetoothProfile.A2DP) { a2dp = proxy; reload(); } }
        @Override public void onServiceDisconnected(int profile) { if (profile == BluetoothProfile.A2DP) a2dp = null; }
    };

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 61);
        } else if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 62);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND); filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
            registerReceiver(receiver, filter); receiverRegistered = true;
        }
        reload();
    }

    @Override protected void onPause() { if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; } super.onPause(); }

    private void reload() {
        adapter.clear(); devices.clear(); seen.clear();
        if (bluetooth == null) { adapter.add("This device has no Bluetooth adapter"); adapter.notifyDataSetChanged(); return; }
        adapter.add((bluetooth.isEnabled() ? "Disable" : "Enable") + " Bluetooth");
        adapter.add(bluetooth.isDiscovering() ? "Discovery running…" : "Scan for devices");
        adapter.add("Make player discoverable");
        try {
            Set<BluetoothDevice> bonded = bluetooth.getBondedDevices();
            if (bonded != null) for (BluetoothDevice device : bonded) { devices.add(device); seen.add(safeAddress(device)); }
        } catch (SecurityException ignored) { }
        Collections.sort(devices, new Comparator<BluetoothDevice>() {
            @Override public int compare(BluetoothDevice left, BluetoothDevice right) { return safeName(left).compareToIgnoreCase(safeName(right)); }
        });
        for (BluetoothDevice device : devices) {
            adapter.add(safeName(device) + "\n" + safeAddress(device) + " • " + bondText(device) + (isA2dpConnected(device) ? " • audio connected" : ""));
        }
        adapter.notifyDataSetChanged();
        ui.subtitle.setText("Adapter: " + (bluetooth.isEnabled() ? "enabled" : "disabled") + " • " + devices.size() + " known/discovered devices");
    }

    private void toggle() {
        if (bluetooth == null) return;
        try { if (bluetooth.isEnabled()) bluetooth.disable(); else bluetooth.enable(); }
        catch (SecurityException e) { Toast.makeText(this, "Firmware denied Bluetooth control", Toast.LENGTH_LONG).show(); }
    }

    private void discover() {
        if (bluetooth == null) return;
        try {
            if (!bluetooth.isEnabled()) bluetooth.enable();
            if (bluetooth.isDiscovering()) bluetooth.cancelDiscovery();
            boolean ok = bluetooth.startDiscovery(); ui.subtitle.setText(ok ? "Discovering nearby devices…" : "Discovery could not start");
        } catch (SecurityException e) { Toast.makeText(this, "Bluetooth/location permission is required", Toast.LENGTH_LONG).show(); }
    }

    private void openVisibility() {
        try {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120); startActivity(intent);
        } catch (Exception e) { Toast.makeText(this, "Discoverability prompt unavailable", Toast.LENGTH_LONG).show(); }
    }

    private void showDevice(final BluetoothDevice device) {
        String[] actions = device.getBondState() == BluetoothDevice.BOND_BONDED
                ? new String[]{"Connect audio", "Disconnect audio", "Unpair", "Details"}
                : new String[]{"Pair", "Details"};
        new AlertDialog.Builder(this).setTitle(safeName(device)).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    if (which == 0) connectA2dp(device, true); else if (which == 1) connectA2dp(device, false);
                    else if (which == 2) unpair(device); else details(device);
                } else { if (which == 0) pair(device); else details(device); }
            }
        }).show();
    }

    private void pair(BluetoothDevice device) {
        try {
            boolean ok;
            if (Build.VERSION.SDK_INT >= 19) ok = device.createBond();
            else ok = (Boolean) device.getClass().getMethod("createBond").invoke(device);
            ui.subtitle.setText(ok ? "Pairing with " + safeName(device) + "…" : "Pairing request rejected");
        } catch (Exception e) { Toast.makeText(this, "Pairing failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void unpair(final BluetoothDevice device) {
        AppUi.confirm(this, "Unpair device?", "Remove " + safeName(device) + " from saved Bluetooth devices?", new AppUi.ConfirmCallback() {
            @Override public void onConfirm() {
                try { Method method = device.getClass().getMethod("removeBond"); method.invoke(device); }
                catch (Exception e) { Toast.makeText(BluetoothActivity.this, "Unpair failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            }
        });
    }

    private void connectA2dp(BluetoothDevice device, boolean connect) {
        if (a2dp == null) { Toast.makeText(this, "A2DP service is not ready", Toast.LENGTH_LONG).show(); return; }
        try {
            Method method = a2dp.getClass().getMethod(connect ? "connect" : "disconnect", BluetoothDevice.class);
            Object result = method.invoke(a2dp, device);
            ui.subtitle.setText((connect ? "Connecting " : "Disconnecting ") + safeName(device) + "… " + result);
        } catch (Exception e) {
            Toast.makeText(this, "This firmware blocks direct A2DP control: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean isA2dpConnected(BluetoothDevice device) {
        try { return a2dp != null && a2dp.getConnectedDevices().contains(device); } catch (Exception ignored) { return false; }
    }

    private void details(BluetoothDevice device) {
        new AlertDialog.Builder(this).setTitle(safeName(device)).setMessage("Address: " + safeAddress(device)
                + "\nBond: " + bondText(device) + "\nType: " + (android.os.Build.VERSION.SDK_INT >= 18 ? device.getType() : 0) + "\nA2DP: " + (isA2dpConnected(device) ? "connected" : "not connected"))
                .setPositiveButton("OK", null).show();
    }

    private static String safeName(BluetoothDevice device) { try { String value = device.getName(); return value == null ? "Unnamed device" : value; } catch (Exception e) { return "Unnamed device"; } }
    private static String safeAddress(BluetoothDevice device) { try { return device.getAddress(); } catch (Exception e) { return "unknown"; } }
    private static String bondText(BluetoothDevice device) { int state = device.getBondState(); return state == BluetoothDevice.BOND_BONDED ? "paired" : state == BluetoothDevice.BOND_BONDING ? "pairing" : "not paired"; }

    @Override protected void onDestroy() {
        if (bluetooth != null && a2dp != null) try { bluetooth.closeProfileProxy(BluetoothProfile.A2DP, a2dp); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
