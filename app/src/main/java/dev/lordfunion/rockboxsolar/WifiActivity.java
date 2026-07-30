package dev.lordfunion.rockboxsolar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class WifiActivity extends Activity {
    private WifiManager wifi;
    private AppUi.Screen ui;
    private ArrayAdapter<String> adapter;
    private final ArrayList<ScanResult> results = new ArrayList<ScanResult>();
    private boolean receiverRegistered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reload(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ui = AppUi.screen(this, "Wi-Fi", "Scan, connect, forget, and inspect networks without leaving Rockbox Solar");
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ui.list.setAdapter(adapter);
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) toggleWifi();
                else if (position == 1) scan();
                else if (position == 2) disconnect();
                else {
                    int index = position - 3; if (index >= 0 && index < results.size()) showNetwork(results.get(index));
                }
            }
        });
        requestPermission();
        reload();
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 51);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(); filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            registerReceiver(receiver, filter); receiverRegistered = true;
        }
        reload();
    }

    @Override protected void onPause() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        super.onPause();
    }

    private void reload() {
        if (wifi == null) return;
        results.clear();
        try { List<ScanResult> found = wifi.getScanResults(); if (found != null) results.addAll(found); } catch (SecurityException ignored) { }
        Collections.sort(results, new Comparator<ScanResult>() { @Override public int compare(ScanResult a, ScanResult b) { return b.level - a.level; }});
        adapter.clear();
        adapter.add((wifi.isWifiEnabled() ? "Disable" : "Enable") + " Wi-Fi");
        adapter.add("Scan now");
        adapter.add("Disconnect current network");
        String connected = currentSsid();
        for (ScanResult result : results) {
            String security = security(result.capabilities);
            adapter.add(result.SSID + (result.SSID.equals(connected) ? " — connected" : "") + "\n" + security + " • " + result.level + " dBm");
        }
        adapter.notifyDataSetChanged();
        WifiInfo info = wifi.getConnectionInfo();
        ui.subtitle.setText("State: " + (wifi.isWifiEnabled() ? "enabled" : "disabled") + " • " + connected
                + (info == null ? "" : " • IP " + formatIp(info.getIpAddress())));
    }

    private void toggleWifi() {
        try { wifi.setWifiEnabled(!wifi.isWifiEnabled()); ui.subtitle.setText("Changing Wi-Fi state…"); }
        catch (SecurityException e) { Toast.makeText(this, "Firmware denied Wi-Fi control", Toast.LENGTH_LONG).show(); }
    }

    private void scan() {
        if (!wifi.isWifiEnabled()) wifi.setWifiEnabled(true);
        try { boolean started = wifi.startScan(); ui.subtitle.setText(started ? "Scanning…" : "Scan request was rejected"); }
        catch (SecurityException e) { Toast.makeText(this, "Location permission is required for scans", Toast.LENGTH_LONG).show(); }
    }

    private void disconnect() {
        try { wifi.disconnect(); reload(); } catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void showNetwork(final ScanResult result) {
        final String security = security(result.capabilities);
        String[] actions = {"Connect", "Forget saved configuration", "Details"};
        new AlertDialog.Builder(this).setTitle(result.SSID).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    if ("Open".equals(security)) connect(result, "");
                    else AppUi.prompt(WifiActivity.this, "Password for " + result.SSID, "Wi-Fi password", "",
                            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                            new AppUi.TextCallback() { @Override public void onText(String value) { connect(result, value); }});
                } else if (which == 1) forget(result.SSID);
                else new AlertDialog.Builder(WifiActivity.this).setTitle(result.SSID)
                        .setMessage("BSSID: " + result.BSSID + "\nSecurity: " + security + "\nSignal: " + result.level + " dBm\nFrequency: " + result.frequency + " MHz\nCapabilities: " + result.capabilities)
                        .setPositiveButton("OK", null).show();
            }
        }).show();
    }

    private void connect(ScanResult result, String password) {
        WifiConfiguration config = findConfig(result.SSID);
        if (config == null) config = new WifiConfiguration();
        config.SSID = quote(result.SSID);
        config.hiddenSSID = false;
        String caps = result.capabilities == null ? "" : result.capabilities.toUpperCase();
        if (caps.contains("WEP")) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.wepKeys[0] = isHex(password) ? password : quote(password); config.wepTxKeyIndex = 0;
        } else if (caps.contains("PSK")) {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            config.preSharedKey = password.matches("[0-9A-Fa-f]{64}") ? password : quote(password);
        } else if (caps.contains("EAP")) {
            Toast.makeText(this, "Enterprise EAP setup requires Android system credentials and is not supported by this screen yet", Toast.LENGTH_LONG).show(); return;
        } else {
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }
        try {
            int id = config.networkId >= 0 ? wifi.updateNetwork(config) : wifi.addNetwork(config);
            if (id < 0) { Toast.makeText(this, "Android rejected the network configuration", Toast.LENGTH_LONG).show(); return; }
            wifi.disconnect(); wifi.enableNetwork(id, true); wifi.reconnect(); wifi.saveConfiguration();
            ui.subtitle.setText("Connecting to " + result.SSID + "…");
        } catch (SecurityException e) { Toast.makeText(this, "Firmware denied network configuration", Toast.LENGTH_LONG).show(); }
    }

    private void forget(String ssid) {
        WifiConfiguration config = findConfig(ssid);
        if (config == null) { Toast.makeText(this, "No saved configuration", Toast.LENGTH_SHORT).show(); return; }
        try { wifi.removeNetwork(config.networkId); wifi.saveConfiguration(); reload(); }
        catch (SecurityException e) { Toast.makeText(this, "Firmware denied forgetting this network", Toast.LENGTH_LONG).show(); }
    }

    private WifiConfiguration findConfig(String ssid) {
        try {
            List<WifiConfiguration> configs = wifi.getConfiguredNetworks();
            if (configs != null) for (WifiConfiguration config : configs) if (quote(ssid).equals(config.SSID)) return config;
        } catch (SecurityException ignored) { }
        return null;
    }

    private String currentSsid() {
        try { WifiInfo info = wifi.getConnectionInfo(); String value = info == null ? null : info.getSSID();
            if (value == null || "<unknown ssid>".equalsIgnoreCase(value)) return "not connected";
            return value.replaceAll("^\"|\"$", "");
        } catch (Exception e) { return "unknown"; }
    }

    private static String security(String caps) {
        String value = caps == null ? "" : caps.toUpperCase();
        if (value.contains("EAP")) return "Enterprise"; if (value.contains("WPA3")) return "WPA3";
        if (value.contains("PSK")) return "WPA/WPA2 PSK"; if (value.contains("WEP")) return "WEP"; return "Open";
    }
    private static String quote(String value) { return "\"" + value.replace("\"", "") + "\""; }
    private static boolean isHex(String value) { return value.matches("[0-9A-Fa-f]{10}|[0-9A-Fa-f]{26}|[0-9A-Fa-f]{58}"); }
    private static String formatIp(int ip) { return (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255); }
}
