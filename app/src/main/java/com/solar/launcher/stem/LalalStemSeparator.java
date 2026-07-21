package com.solar.launcher.stem;

import android.content.Context;

import java.io.File;

/**
 * 2026-07-19 — LALAL adapter for {@link StemSeparatorProvider}.
 * Layman: Solar’s first cloud service for Instrumental / Acapella / full stems.
 * Technical: wraps {@link LalalClient} solo + multistem readiness. Reversal: inline LalalClient in UI.
 */
public final class LalalStemSeparator implements StemSeparatorProvider {

    private final String licenseKey;

    public LalalStemSeparator(String licenseKey) {
        this.licenseKey = licenseKey != null ? licenseKey.trim() : "";
    }

    @Override
    public String providerId() {
        return StemFeatures.PROVIDER_LALAL;
    }

    @Override
    public File findReadySolo(Context ctx, File track, SoloMode mode, File appCache) {
        return LalalClient.findReadySoloFile(ctx, track, mode, appCache);
    }

    @Override
    public File ensureSolo(Context ctx, File track, SoloMode mode, File appCache,
            LalalClient.Progress progress) throws Exception {
        File cache = appCache != null ? appCache : (ctx != null ? ctx.getCacheDir() : null);
        File hit = LalalClient.findReadySoloFile(ctx, track, mode, cache);
        if (hit != null) return hit;

        // Prefer bake from full pads when instrumental (no network).
        if (mode == SoloMode.INSTRUMENTAL
                && LalalClient.findReadyStemDir(ctx, track, false, cache) != null) {
            File baked = LalalClient.bakeInstrumentalFromFullStems(ctx, track, cache, progress);
            SoloStemTagWriter.writeBothIfPresent(ctx, track);
            File sibling = SoloStemPaths.findReadySibling(track, mode);
            return sibling != null ? sibling : baked;
        }
        if (mode == SoloMode.INSTRUMENTAL && ctx != null) {
            try {
                android.content.SharedPreferences prefs =
                        ctx.getSharedPreferences(LalalAccount.PREFS_NAME, 0);
                if (LalalClient.findReadyStemDir(ctx, track,
                        LalalAccount.isPremixExperimental(prefs), cache) != null) {
                    File baked = LalalClient.bakeInstrumentalFromFullStems(ctx, track, cache, progress);
                    SoloStemTagWriter.writeBothIfPresent(ctx, track);
                    File sibling = SoloStemPaths.findReadySibling(track, mode);
                    return sibling != null ? sibling : baked;
                }
            } catch (Exception ignored) {}
        }
        if (mode == SoloMode.ACAPELLA) {
            File fromFull = LalalClient.findSoloFromFullStems(ctx, track, mode, cache);
            if (fromFull != null) {
                // Publish full-stem vocals into sibling when missing. 2026-07-19
                File sibling = SoloStemPaths.findReadySibling(track, SoloMode.ACAPELLA);
                if (sibling == null) {
                    SoloStemPaths.ensureSiblingDir(track, SoloMode.ACAPELLA);
                    File dest = SoloStemPaths.siblingSoloFile(track, SoloMode.ACAPELLA, "mp3");
                    if (dest != null) {
                        LalalClient.publishSoloSiblings(track, fromFull, null);
                        SoloStemTagWriter.writeFromOrigin(ctx, track, dest, SoloMode.ACAPELLA);
                        sibling = SoloStemPaths.findReadySibling(track, SoloMode.ACAPELLA);
                    }
                }
                return sibling != null ? sibling : fromFull;
            }
        }

        LalalClient client = new LalalClient(licenseKey);
        File solo = LalalClient.soloDir(cache, track);
        client.separateSoloToFiles(track, solo, progress);
        // Play from solo work immediately; sibling publish + tags off-thread. 2026-07-21
        // Was: publishSoloSiblings inside separateSoloToFiles before return. Reversal: that.
        File vocalsWork = new File(solo, "vocals.mp3");
        File instrWork = new File(solo, "instrumental.mp3");
        StemDeferredPublish.enqueueSoloAfterPlayback(ctx, track, vocalsWork, instrWork, solo, cache);
        File out = SoloStemPaths.findReadySibling(track, mode);
        if (out == null) {
            // Work files ready before sibling flush finishes. 2026-07-19 / 2026-07-21
            out = mode == SoloMode.ACAPELLA
                    ? new File(solo, "vocals.mp3")
                    : LalalClient.resolveInstrumentalFile(solo);
        }
        if (out == null || !out.isFile() || out.length() < 100) {
            throw new java.io.IOException("Solo file missing after split");
        }
        return out;
    }

    @Override
    public boolean trackFullStemsReady(Context ctx, File track, boolean premix, File appCache) {
        return LalalClient.trackStemsReady(ctx, track, premix, appCache);
    }
}
