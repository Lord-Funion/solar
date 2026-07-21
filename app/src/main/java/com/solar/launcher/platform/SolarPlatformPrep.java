package com.solar.launcher.platform;

import android.content.Context;

import org.json.JSONObject;

import com.solar.launcher.GraphicsPerformancePolicy;
import com.solar.launcher.LargeFontAccessibilitySuppressor;
import com.solar.launcher.RootShell;
import com.solar.launcher.SolarLog;
import com.solar.launcher.XposedModuleEnsurer;
import com.solar.launcher.XposedModuleStore;

/**
 * 2026-07-05 — Blocking platform prep wizard ladder; closes OTA gaps when root is available.
 * APK/ROM parity: APK-side mirror of ROM bake (99XposedInit.sh runs earlier at boot via init.d).
 * Ladder order (do not reorder): 1 deprecation cleanup 2 switch scripts 2b Rockbox install
 *   3 init files 4 framework 5 module install 6 ensurer enable. Fail-soft → PARTIAL/LIMITED; reboot → REBOOT_PENDING.
 * When changing: sync-platform-assets.sh manifest + prepVersion; XposedModuleRegistry catalog.
 * Reversal: delete; Y1RomPrep + XposedModuleEnsurer run independently again without wizard.
 */
public final class SolarPlatformPrep {

    private static final String TAG = "SolarPlatformPrep";

    /** Step result for ladder accounting. */
    public enum StepStatus {
        OK, SKIPPED, FAILED, REBOOT_REQUIRED
    }

    /** Final wizard outcome. */
    public enum PrepOutcome {
        COMPLETE, PARTIAL, LIMITED, REBOOT_PENDING
    }

    /** UI + logging hook for wizard progress. */
    public interface ProgressListener {
        void onProgress(int percent, String message);
        void onLogLine(String line);
    }

    /** Aggregate result of one prep run. */
    public static final class PrepResult {
        public PrepOutcome outcome = PrepOutcome.COMPLETE;
        public boolean rebootRequired;
        public final java.util.List<String> degradedReasons = new java.util.ArrayList<String>();

        public boolean isSuccess() {
            return outcome == PrepOutcome.COMPLETE || outcome == PrepOutcome.REBOOT_PENDING;
        }
    }

    private SolarPlatformPrep() {}

    /** True when blocking wizard should run before MainActivity. */
    public static boolean isWizardRequired(Context ctx) {
        return false;
    }

    /** Silent background prep when bundled prepVersion is ahead of device. */
    public static void ensureAsync(final Context ctx) {
        if (ctx == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PlatformPrepManifest manifest = PlatformPrepManifest.load(ctx);
                    PlatformProbe.Report probe = PlatformProbe.probe(manifest);
                    boolean versionAhead = PlatformPrepState.needsSilentPrep(ctx, manifest);
                    if (!versionAhead && !probe.hasRequiredGaps()) return;
                    if (!versionAhead && probe.hasRequiredGaps() && !RootShell.canRun()) return;
                    // #region agent log
                    JSONObject start = new JSONObject();
                    try {
                        start.put("prepVersion", manifest.prepVersion);
                        start.put("applied", PlatformPrepState.getAppliedVersion(ctx));
                    } catch (Exception ignored) {}
                    PlatformPrepDebugLog.log(ctx, "SolarPlatformPrep.ensureAsync",
                            "silent prep start", "H1", start);
                    // #endregion
                    PrepResult result = SolarPlatformPrep.run(ctx, null);
                    if (result.rebootRequired) {
                        PlatformPrepState.setRebootPending(ctx, true);
                        scheduleSilentReboot(ctx);
                    }
                } catch (Exception e) {
                    SolarLog.w(TAG, "silent prep failed: " + e.getMessage());
                }
            }
        }, "PlatformPrepSilent").start();
    }

    private static void scheduleSilentReboot(final Context ctx) {
        // 2026-07-06 — User must confirm restart — no silent reboot after module repair.
        PlatformPrepLauncher.launchRebootWizard(ctx);
    }

    /** @deprecated Gap-based gate caused loops — use {@link #ensureAsync}. */
    public static boolean isWizardRequiredLegacy(Context ctx) {
        if (ctx == null) return false;
        try {
            PlatformPrepManifest manifest = PlatformPrepManifest.load(ctx);
            PlatformProbe.Report report = PlatformProbe.probe(manifest);
            return PlatformPrepState.isPrepRequired(ctx, manifest, report);
        } catch (Exception e) {
            SolarLog.w(TAG, "prep gate failed open: " + e.getMessage());
            return false;
        }
    }

    /** Run full prep ladder — call off main thread. */
    public static PrepResult run(Context ctx, ProgressListener listener) {
        PrepResult result = new PrepResult();
        if (ctx == null) {
            result.outcome = PrepOutcome.LIMITED;
            return result;
        }
        PlatformPrepManifest manifest;
        try {
            manifest = PlatformPrepManifest.load(ctx);
        } catch (Exception e) {
            notify(listener, 100, "Manifest missing");
            result.outcome = PrepOutcome.LIMITED;
            result.degradedReasons.add("manifest");
            return result;
        }

        notify(listener, 5, "Checking root access…");
        XposedModuleStore.bindResolveContext(ctx);
        PlatformProbe.Report probe = PlatformProbe.probe(manifest);
        if (!probe.rootAvailable) {
            notify(listener, 100, "Limited mode: no root");
            result.outcome = PrepOutcome.LIMITED;
            PlatformPrepState.markApplied(ctx, manifest.prepVersion, PlatformPrepState.Outcome.LIMITED);
            com.solar.launcher.SolarRecoveryCoordinator.setPlatformDegraded(ctx, true);
            return result;
        }

        notify(listener, 10, "Removing deprecated components…");
        // 2026-07-05 — Ladder step 1: wrong-family / legacy /system APK cleanup per manifest deprecated[].
        PlatformDeprecationCleaner.removeDeprecatedArtifacts(ctx, manifest, listener);

        // 2026-07-16 — Co-install alongside Rockbox/switch scripts removed from onboarding setup.
        // Solar-first onboarding: modders can manually incorporate Rockbox or switch scripts if desired.

        // 2026-07-20 — Lab Solar-only (persist.solar.lab.noxposed=1): skip Xposed ladder entirely.
        // Layman: after uninstall-xposed-adb.sh, prep must not put hooks back.
        // Reversal: setprop persist.solar.lab.noxposed 0; re-run install-xposed-adb.sh.
        final boolean labNoXposed = XposedModuleEnsurer.isLabNoXposed();
        if (labNoXposed) {
            notify(listener, 80, "Lab: Xposed skipped (Solar-only)…");
            notifyLog(listener, "persist.solar.lab.noxposed=1: framework/modules not staged");
        } else {
            notify(listener, 25, "Staging init hooks…");
            // 2026-07-05 — Ladder step 3: init.d files (99XposedInit.sh) from manifest files[].
            stageManifestFiles(ctx, manifest, probe.remountWritable, result);

            notify(listener, 45, "Installing Xposed framework…");
            // 2026-07-05 — Ladder step 4: api17/api19 vendor tree; ETXTBUSY may stage app_process.
            XposedFrameworkInstaller.Result fw = XposedFrameworkInstaller.ensureFramework(ctx, manifest);
            if (fw.error != null) {
                result.degradedReasons.add("framework: " + fw.error);
            }
            if (fw.rebootRequired) {
                result.rebootRequired = true;
            }

            notify(listener, 65, "Installing hook modules…");
            // 2026-07-05 — Ladder step 5: pm install -r + /system/app copy for required modules.
            int installed = XposedModuleInstaller.installRequiredModules(ctx, manifest, probe.remountWritable);
            notifyLog(listener, "Modules installed/verified: " + installed);
            if (installed == 0 && hasMissingModules(manifest)) {
                result.degradedReasons.add("modules");
            }

            notify(listener, 80, "Enabling modules…");
            // 2026-07-05 — Ladder step 6: modules.list + enabled_modules.xml repair (parity with 99XposedInit.sh).
            if (XposedModuleEnsurer.repairModulesBlocking(ctx)) {
                result.rebootRequired = true;
            }
        }

        notify(listener, 92, "Applying display settings…");
        // 2026-07-05 — Ladder step 7: GPU rendering + disable overlays + large-font reset (ROM parity).
        LargeFontAccessibilitySuppressor.applySync(ctx);
        GraphicsPerformancePolicy.applySync(ctx);

        notify(listener, 94, "Applying Bluetooth pairing conf…");
        // 2026-07-19 — Ladder step 8: Koensayr audio/auto_pairing/blacklist + CoD props.
        BluetoothPairingConfInstaller.Result btConf = BluetoothPairingConfInstaller.apply(ctx);
        if (btConf.applied && btConf.rebootSuggested) {
            result.rebootRequired = true;
            notifyLog(listener, "Bluetooth pairing conf updated (reboot suggested)");
        } else if (btConf.skipped) {
            notifyLog(listener, "Bluetooth pairing conf skipped: " + btConf.detail);
        }

        notify(listener, 96, "Applying AirPods audio fix…");
        // 2026-07-19 — Ladder step 9: Y1 RTP proxy when stock MD5 matches.
        AirpodsRtpProxyInstaller.Result rtp = AirpodsRtpProxyInstaller.apply(ctx);
        if (rtp.applied && rtp.rebootRequired) {
            result.rebootRequired = true;
            notifyLog(listener, "AirPods RTP proxy installed (reboot required)");
        } else if (rtp.skipped) {
            notifyLog(listener, "AirPods RTP proxy skipped: " + rtp.detail);
        }

        notify(listener, 98, "Finishing…");
        if (result.rebootRequired) {
            result.outcome = PrepOutcome.REBOOT_PENDING;
            PlatformPrepState.markApplied(ctx, manifest.prepVersion, PlatformPrepState.Outcome.COMPLETE);
        } else if (!result.degradedReasons.isEmpty()) {
            result.outcome = PrepOutcome.PARTIAL;
            PlatformPrepState.markApplied(ctx, manifest.prepVersion, PlatformPrepState.Outcome.PARTIAL);
            com.solar.launcher.SolarRecoveryCoordinator.setPlatformDegraded(ctx, true);
        } else {
            result.outcome = PrepOutcome.COMPLETE;
            PlatformPrepState.markApplied(ctx, manifest.prepVersion, PlatformPrepState.Outcome.COMPLETE);
            com.solar.launcher.SolarRecoveryCoordinator.setPlatformDegraded(ctx, false);
        }
        PlatformPrepState.clearRerun(ctx);
        // #region agent log
        JSONObject done = new JSONObject();
        try {
            done.put("outcome", result.outcome.name());
            done.put("rebootRequired", result.rebootRequired);
            done.put("degraded", result.degradedReasons.toString());
            done.put("appliedNow", PlatformPrepState.getAppliedVersion(ctx));
        } catch (Exception ignored) {}
        PlatformPrepDebugLog.log(ctx, "SolarPlatformPrep.run", "prep finished", "H4", done);
        // #endregion
        notify(listener, 100, "Done");
        return result;
    }

    private static void stageManifestFiles(Context ctx, PlatformPrepManifest manifest,
            boolean copyToSystem, PrepResult result) {
        if (!copyToSystem) return;
        for (PlatformPrepManifest.FileEntry f : manifest.files) {
            if (!XposedFrameworkInstaller.copyAssetToSystem(ctx, f.asset, f.dest, f.mode)) {
                result.degradedReasons.add("file:" + f.dest);
            }
        }
    }

    private static boolean hasMissingModules(PlatformPrepManifest manifest) {
        for (PlatformPrepManifest.ModuleEntry m : manifest.requiredModulesForDevice(
                com.solar.launcher.DeviceFeatures.isY2())) {
            if (!PlatformProbe.packageRegisteredInPm(m.pkg)) return true;
        }
        return false;
    }

    private static void notify(ProgressListener listener, int pct, String msg) {
        if (listener != null) listener.onProgress(pct, msg);
    }

    private static void notifyLog(ProgressListener listener, String line) {
        if (listener != null) listener.onLogLine(line);
    }
}
