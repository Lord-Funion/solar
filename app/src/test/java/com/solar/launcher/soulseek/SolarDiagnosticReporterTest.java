package com.solar.launcher.soulseek;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class SolarDiagnosticReporterTest {

  @Test
  public void splitContentIntoChunks() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 25000; i++) sb.append('x');
    List<String> chunks = SolarDiagnosticReporter.splitContent(sb.toString(), 12000);
    if (chunks.size() < 3) throw new AssertionError("chunks=" + chunks.size());
  }

  @Test
  public void diagMarkerPresentInWireFormat() {
    if (!SolarDeveloperAccounts.isAutoDiagnosticText(
            SolarDeveloperAccounts.DIAG_MARKER + "user: test\nbody")) {
      throw new AssertionError("marker detect");
    }
  }

  @Test
  public void startupPriorityShipsWhenFingerprintNew() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/storage/sdcard0/solar/logs/crash.log", 999L);
    // New content fingerprint → ship even if mtime matches prior ship.
    if (!SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "100:abc")) {
      throw new AssertionError("crash.log new fingerprint");
    }
    if (SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, "/storage/sdcard0/solar/logs/crash.log", 999L,
            SolarDiagnosticReporter.ScanMode.ROUTINE)) {
      throw new AssertionError("crash.log routine skip");
    }
  }

  @Test
  public void startupPrioritySkipsUnchangedFingerprint() throws Exception {
    // 2026-07-16 — Stop re-shipping identical crash tails every boot.
    JSONObject manifest = new JSONObject();
    String path = "/storage/sdcard0/solar/logs/crash.log";
    manifest.put(path, 999L);
    manifest.put(SolarDiagnosticReporter.fpKey(path), "42:deadbeef");
    if (SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, path, 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "42:deadbeef")) {
      throw new AssertionError("same fingerprint must skip");
    }
    if (!SolarDiagnosticReporter.shouldShipSource(
            "SolarLog/crash.log", manifest, path, 999L,
            SolarDiagnosticReporter.ScanMode.STARTUP, "43:cafebabe")) {
      throw new AssertionError("changed fingerprint must ship");
    }
  }

  @Test
  public void contentFingerprintStableForSameText() {
    String a = SolarDiagnosticReporter.contentFingerprint("hello crash stack");
    String b = SolarDiagnosticReporter.contentFingerprint("hello crash stack");
    if (!a.equals(b)) throw new AssertionError("stable fp");
    String c = SolarDiagnosticReporter.contentFingerprint("hello crash stack!");
    if (a.equals(c)) throw new AssertionError("different content different fp");
  }

  @Test
  public void priorityStartupSourceLabels() {
    // 2026-07-16 — Startup priority is crash/error/storage only (performance).
    if (!SolarDiagnosticReporter.isPriorityStartupSource("SolarLog/crash.log")) {
      throw new AssertionError("crash.log");
    }
    if (!SolarDiagnosticReporter.isPriorityStartupSource("SolarLog/error.log")) {
      throw new AssertionError("error.log");
    }
    if (SolarDiagnosticReporter.isPriorityStartupSource("Android/logcat.txt")) {
      throw new AssertionError("logcat should not force startup ship");
    }
    if (SolarDiagnosticReporter.isPriorityStartupSource("Features/reach.log")) {
      throw new AssertionError("feature logs should not force startup ship");
    }
  }

  @Test
  public void supportOpenShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.SUPPORT_OPEN)) {
      throw new AssertionError("support open fresh bundle");
    }
  }

  @Test
  public void remotePullShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.REMOTE_PULL)) {
      throw new AssertionError("remote pull full bundle");
    }
  }

  @Test
  public void userReportShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("user report full bundle");
    }
  }

  @Test
  public void wifiOffShipsAllSourcesRegardlessOfManifest() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.WIFI_OFF)) {
      throw new AssertionError("wifi_off flush bundle");
    }
  }

  @Test
  public void runBeforeWifiDisableNullSafe() {
    SolarDiagnosticReporter.runBeforeWifiDisable(null, true, null);
  }

  @Test
  public void shipOnDeveloperSupportOpenNullSafe() {
    SolarDiagnosticReporter.shipOnDeveloperSupportOpen(null, null);
  }

  @Test
  public void shipUserReportNullSafe() {
    SolarDiagnosticReporter.shipUserReport(null, null, "hello", null);
  }

  @Test
  public void shipOnRemoteDiagCommandNullSafe() {
    SolarDiagnosticReporter.shipOnRemoteDiagCommand(null, null, "SolarDev", null);
  }

  @Test
  public void isEnabledAlwaysFalseForLeanDiag() {
    // 2026-07-19 — Pref auto-report stays off; lifecycle flush is separate (power / Wi‑Fi off).
    if (SolarDiagnosticReporter.isEnabled(null)) {
      throw new AssertionError("auto report must stay off");
    }
    if (!"solar_diag_auto_report".equals(SolarDiagnosticReporter.PREF_DIAG_AUTO_REPORT)) {
      throw new AssertionError("pref key drift");
    }
  }

  @Test
  public void lifecycleFlushModesAllowedToShip() {
    // 2026-07-20 — Power / Wi‑Fi-off may ship to Cloudflare when online; boot/wifi-connect stay blocked.
    if (!SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("USER_REPORT");
    }
    if (!SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.POWER_OFF)) {
      throw new AssertionError("POWER_OFF");
    }
    if (!SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.RESTART)) {
      throw new AssertionError("RESTART");
    }
    if (!SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.WIFI_OFF)) {
      throw new AssertionError("WIFI_OFF");
    }
    if (SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.STARTUP)) {
      throw new AssertionError("STARTUP must stay blocked");
    }
    if (SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.WIFI)) {
      throw new AssertionError("WIFI connect must stay blocked");
    }
    if (SolarDiagnosticReporter.allowsShipMode(SolarDiagnosticReporter.ScanMode.REMOTE_PULL)) {
      throw new AssertionError("REMOTE_PULL must stay blocked");
    }
  }

  @Test
  public void powerToastResForRestartVsShutdown() {
    // 2026-07-20 — User sees Restarting… / Shutting down… not Getting ready….
    if (SolarDiagnosticReporter.powerPrepToastRes(true)
            != com.solar.launcher.R.string.diag_restarting) {
      throw new AssertionError("restart toast");
    }
    if (SolarDiagnosticReporter.powerPrepToastRes(false)
            != com.solar.launcher.R.string.diag_shutting_down) {
      throw new AssertionError("shutdown toast");
    }
  }

  @Test
  public void userReportStillAllowedByShouldShipSource() throws Exception {
    JSONObject manifest = new JSONObject();
    manifest.put("/data/foo.txt", 123L);
    if (!SolarDiagnosticReporter.shouldShipSource(
            "other/file.txt", manifest, "/data/foo.txt", 123L,
            SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("USER_REPORT must still ship sources");
    }
  }

  @Test
  public void silentWifiWakeForPowerAndUserReportOnly() {
    // 2026-07-20 — Wake radio before shutdown / Report Issue; not before Wi‑Fi-off (already online).
    if (!SolarDiagnosticReporter.shouldAttemptSilentWifiWake(
            SolarDiagnosticReporter.ScanMode.POWER_OFF)) {
      throw new AssertionError("POWER_OFF wake");
    }
    if (!SolarDiagnosticReporter.shouldAttemptSilentWifiWake(
            SolarDiagnosticReporter.ScanMode.RESTART)) {
      throw new AssertionError("RESTART wake");
    }
    if (!SolarDiagnosticReporter.shouldAttemptSilentWifiWake(
            SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("USER_REPORT wake");
    }
    if (SolarDiagnosticReporter.shouldAttemptSilentWifiWake(
            SolarDiagnosticReporter.ScanMode.WIFI_OFF)) {
      throw new AssertionError("WIFI_OFF must not wake");
    }
    if (SolarDiagnosticReporter.shouldAttemptSilentWifiWake(
            SolarDiagnosticReporter.ScanMode.STARTUP)) {
      throw new AssertionError("STARTUP must not wake");
    }
  }

  @Test
  public void shipFailuresStaySilentForLifecycleModes() {
    // 2026-07-20 — Power / Wi‑Fi-off / user report never toast ship errors.
    if (!SolarDiagnosticReporter.shipFailsSilently(
            SolarDiagnosticReporter.ScanMode.POWER_OFF)) {
      throw new AssertionError("POWER_OFF silent");
    }
    if (!SolarDiagnosticReporter.shipFailsSilently(
            SolarDiagnosticReporter.ScanMode.WIFI_OFF)) {
      throw new AssertionError("WIFI_OFF silent");
    }
    if (!SolarDiagnosticReporter.shipFailsSilently(
            SolarDiagnosticReporter.ScanMode.USER_REPORT)) {
      throw new AssertionError("USER_REPORT silent");
    }
  }

  @Test
  public void runWithPowerDiagPrepNullSafe() {
    SolarDiagnosticReporter.runWithPowerDiagPrep(null, false, null);
    SolarDiagnosticReporter.runWithPowerDiagPrep(null, true, null);
  }
}
