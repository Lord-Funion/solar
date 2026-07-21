package com.solar.launcher.stem;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin NP host for StemMixer dual-source (origin vs pads) + multistem cook.
 * Layman: Now Playing Stems master owns one mixer — original file or four pads.
 * Technical: StemMixer + Lalal separateToMp3(premix); SolarTransport muted while pads own audio.
 * Was: SolarTransport TransportLayerPair + ensureSolo stem_separator.
 * Reversal: delete this host; restore trySoloLayerMix / startSoloEnsureJob path.
 * 2026-07-21
 */
public final class NpStemSession {

    public interface Host {
        Context appContext();
        File appCache();
        File musicRoot();
        String lalalApiKey();
        boolean isOnline();
        boolean stemsOptedIn();
        /** Current NP playhead (SolarTransport or legacy). 2026-07-21 */
        int currentPositionMs();
        boolean isPlayingNow();
        /** Pause/mute main transport while mixer takes over. 2026-07-21 */
        void releaseTransportForMixer();
        /** Resume origin on SolarTransport after Stems off (matched seek). 2026-07-21 */
        void resumeTransportOrigin(File origin, int seekMs, boolean play);
        void onStemsProgress(String detail, int pct);
        void onStemsCookFailed(String message);
        void onStemsReady();
        void toast(String msg);
        /** Confirm non-library cook; run yes/no on UI thread. 2026-07-21 */
        void promptNonLibraryCook(File track, Runnable onYes, Runnable onNo);
    }

    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobGen = new AtomicInteger(0);
    private StemMixer mixer;
    private boolean stemsMasterOn;
    private boolean wantVocals = true;
    private boolean wantInstr = true;
    private File originFile;
    private File lastWorkDir;
    private boolean persistAllowed;
    private boolean cooking;
    /** Layer prefs to apply once pads land (Play Instrumental before cook finishes). 2026-07-21 */
    private boolean pendingLayerApply;
    private boolean pendingVocals = true;
    private boolean pendingInstr = true;

    /**
     * Bind a host (MainActivity). 2026-07-21
     */
    public NpStemSession(Host host) {
        this.host = host;
    }

    public boolean isStemsMasterOn() {
        return stemsMasterOn;
    }

    public boolean isWantVocals() {
        return wantVocals;
    }

    public boolean isWantInstr() {
        return wantInstr;
    }

    public boolean isCooking() {
        return cooking;
    }

    public boolean isPadModeActive() {
        return mixer != null && mixer.isPadMode() && !mixer.isSwapBusy();
    }

    /**
     * True when StemMixer drives NP audio (Stems on + pads).
     * 2026-07-21
     */
    public boolean isMixerOwningAudio() {
        return stemsMasterOn && mixer != null && mixer.isPadMode();
    }

    public StemMixer mixer() {
        return mixer;
    }

    public File originFile() {
        return originFile;
    }

    /**
     * Clear session when leaving the song. 2026-07-21
     */
    public void clear() {
        jobGen.incrementAndGet();
        cooking = false;
        stemsMasterOn = false;
        wantVocals = true;
        wantInstr = true;
        originFile = null;
        lastWorkDir = null;
        persistAllowed = false;
        pendingLayerApply = false;
        pendingVocals = true;
        pendingInstr = true;
        releaseMixer();
    }

    public int getPositionMs() {
        return mixer != null ? mixer.getPositionMs() : 0;
    }

    public int getDurationMs() {
        return mixer != null ? mixer.getDurationMs() : 0;
    }

    public void pause() {
        if (mixer != null) mixer.pause();
    }

    public void resume() {
        if (mixer != null) mixer.resume();
    }

    public void seekTo(int ms) {
        if (mixer != null) mixer.seekTo(ms);
    }

    public boolean isPlaying() {
        return mixer != null && mixer.isPlaying();
    }

    /**
     * Toggle Stems master for the playing origin track.
     * Layman: Stems on cooks/swaps to pads; off swaps back to the real file.
     * 2026-07-21
     */
    public void setStemsMaster(final File origin, final boolean on) {
        if (origin == null || !origin.isFile()) return;
        originFile = origin;
        if (!on) {
            stemsMasterOn = false;
            turnStemsOff(origin);
            return;
        }
        stemsMasterOn = true;
        wantVocals = true;
        wantInstr = true;
        final File cache = host.appCache();
        final boolean premix = NpStemMelodyCatchAll.forcePremixForNp();
        List<LalalClient.StemFile> ready = resolveReadyPads(origin, premix, cache);
        if (ready != null && ready.size() >= 2) {
            magicalSwapToPads(origin, ready);
            return;
        }
        if (!host.stemsOptedIn()) {
            stemsMasterOn = false;
            host.toast("Enable Stem features in Settings");
            return;
        }
        if (!host.isOnline()) {
            stemsMasterOn = false;
            host.toast("Connect Wi‑Fi to create stems");
            return;
        }
        final File musicRoot = host.musicRoot();
        if (NpStemPersistGate.mustPromptBeforeCook(origin, musicRoot, cache)) {
            host.promptNonLibraryCook(origin, new Runnable() {
                @Override
                public void run() {
                    startMultistemCook(origin, /*persist*/ true);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    stemsMasterOn = false;
                }
            });
            return;
        }
        startMultistemCook(origin, /*persist*/ true);
    }

    /**
     * Remember Instrumentals/Vocals to apply when pads become ready.
     * Layman: pick “Play Instrumental” while stems are still cooking.
     * 2026-07-21
     */
    public void setPendingLayers(boolean vocals, boolean instr) {
        pendingLayerApply = true;
        pendingVocals = vocals;
        pendingInstr = instr;
        if (stemsMasterOn && mixer != null && mixer.isPadMode()) {
            setLayerToggles(vocals, instr);
            pendingLayerApply = false;
        }
    }

    /**
     * Instrumentals / Vocals while Stems on (never both off).
     * 2026-07-21
     */
    public void setLayerToggles(boolean vocals, boolean instr) {
        if (!NpStemMasterPolicy.allowLayerToggle(stemsMasterOn, vocals, instr)) {
            return;
        }
        boolean[] clamped = NpStemMasterPolicy.clampLayers(
                stemsMasterOn, vocals, instr, wantVocals, wantInstr);
        wantVocals = clamped[0];
        wantInstr = clamped[1];
        if (mixer != null && mixer.isPadMode()) {
            mixer.applyIsolationGains(wantVocals, wantInstr);
        }
    }

    /**
     * SoloMode chrome helper while Stems on.
     * 2026-07-21
     */
    public SoloMode modeForChrome() {
        if (!stemsMasterOn) return null;
        return SoloLayerGains.modeForLayers(wantVocals, wantInstr, null);
    }

    private void turnStemsOff(final File origin) {
        if (mixer == null || !mixer.isPadMode()) {
            releaseMixer();
            int pos = host.currentPositionMs();
            host.resumeTransportOrigin(origin, pos, host.isPlayingNow());
            return;
        }
        final int pos = mixer.getPositionMs();
        final boolean playing = mixer.isPlaying();
        mixer.setSwapListener(new StemMixer.SwapListener() {
            @Override
            public void onSwapComplete(StemMixer.SourceMode mode) {
                int p = mixer != null ? mixer.getPositionMs() : pos;
                releaseMixer();
                host.resumeTransportOrigin(origin, p, playing);
            }
        });
        mixer.setListener(new StemMixer.Listener() {
            @Override
            public void onReady() {}
            @Override
            public void onError(String message) {
                releaseMixer();
                host.resumeTransportOrigin(origin, pos, playing);
            }
            @Override
            public void onComplete() {
                host.resumeTransportOrigin(origin, 0, false);
            }
        });
        mixer.crossfadeToOrigin(origin, pos, playing);
    }

    private void magicalSwapToPads(final File origin, List<LalalClient.StemFile> pads) {
        File dir = pads.get(0).file != null ? pads.get(0).file.getParentFile() : null;
        final List<LalalClient.StemFile> catchAll =
                NpStemMelodyCatchAll.padsForPlayback(pads, dir);
        ensureMixer();
        final int pos = host.currentPositionMs();
        final boolean playing = host.isPlayingNow();
        host.releaseTransportForMixer();
        mixer.setListener(new StemMixer.Listener() {
            @Override
            public void onReady() {
                try {
                    mixer.seekTo(StemMixerSwapPolicy.matchedPositionMs(pos, mixer.getDurationMs()));
                    mixer.applyIsolationGains(wantVocals, wantInstr);
                    if (playing) {
                        mixer.resume();
                    }
                    if (pendingLayerApply) {
                        setLayerToggles(pendingVocals, pendingInstr);
                        pendingLayerApply = false;
                    }
                    host.onStemsReady();
                } catch (Exception e) {
                    onError(e.getMessage());
                }
            }
            @Override
            public void onError(String message) {
                host.onStemsCookFailed(message != null ? message : "Stem load error");
                stemsMasterOn = false;
                releaseMixer();
                host.resumeTransportOrigin(origin, pos, playing);
            }
            @Override
            public void onComplete() {
                releaseMixer();
                stemsMasterOn = false;
            }
        });
        try {
            mixer.loadPads(catchAll, null);
        } catch (Exception e) {
            host.onStemsCookFailed(e.getMessage());
            stemsMasterOn = false;
            releaseMixer();
            host.resumeTransportOrigin(origin, pos, playing);
        }
    }

    private void startMultistemCook(final File origin, final boolean persist) {
        if (!NpStemPersistGate.useMultistemWithMelodyPremix()) {
            host.onStemsCookFailed("Multistem required");
            stemsMasterOn = false;
            return;
        }
        cooking = true;
        persistAllowed = persist;
        final int gen = jobGen.incrementAndGet();
        final File cache = host.appCache();
        final boolean premix = true;
        final Context ctx = host.appContext();
        final File work = LalalClient.workStemDir(ctx, origin, premix);
        final File durable = LalalClient.durableStemDir(ctx, origin, premix);
        lastWorkDir = work;
        host.onStemsProgress("Separating stems…", 0);
        final String key = host.lalalApiKey();
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    LalalClient client = new LalalClient(key);
                    List<LalalClient.StemFile> pads = client.separateToMp3(
                            origin, work, durable, premix,
                            new LalalClient.Progress() {
                                @Override
                                public void onProgress(final String phase, final int percent,
                                        final String detail) {
                                    if (gen != jobGen.get()) return;
                                    main.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            String d = detail != null && detail.length() > 0
                                                    ? detail : phase;
                                            host.onStemsProgress(d != null ? d : "Separating…",
                                                    percent);
                                        }
                                    });
                                }
                            });
                    if (gen != jobGen.get()) {
                        NpStemPersistGate.cleanupFailedWork(work);
                        return;
                    }
                    if (pads == null || pads.size() < 4) {
                        throw new Exception("Incomplete stems");
                    }
                    final List<LalalClient.StemFile> catchAll =
                            NpStemMelodyCatchAll.padsForPlayback(pads, work);
                    if (persistAllowed) {
                        StemDeferredPublish.enqueueAfterPlayback(
                                ctx, origin, work, premix, cache, true);
                    }
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get()) return;
                            cooking = false;
                            magicalSwapToPads(origin, catchAll);
                        }
                    });
                } catch (final Exception e) {
                    NpStemPersistGate.cleanupFailedWork(work);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get()) return;
                            cooking = false;
                            stemsMasterOn = false;
                            host.onStemsCookFailed(e.getMessage() != null
                                    ? e.getMessage() : "Could not create stems");
                        }
                    });
                }
            }
        });
    }

    private List<LalalClient.StemFile> resolveReadyPads(File origin, boolean premix,
            File cache) {
        Context ctx = host.appContext();
        File readyDir = LalalClient.findReadyStemDir(ctx, origin, premix, cache);
        if (readyDir == null) {
            readyDir = LalalClient.findReadyStemDir(ctx, origin, false, cache);
        }
        if (readyDir != null) {
            List<LalalClient.StemFile> cached = LalalClient.loadCached(readyDir, true);
            if (cached != null && cached.size() >= 2) return cached;
            cached = LalalClient.loadStemDirFlexible(readyDir);
            if (cached != null && cached.size() >= 2) return cached;
        }
        File acap = LalalClient.findReadySoloFile(ctx, origin, SoloMode.ACAPELLA, cache);
        File instr = LalalClient.findReadySoloFile(ctx, origin, SoloMode.INSTRUMENTAL, cache);
        if (acap != null && instr != null && acap.isFile() && instr.isFile()) {
            List<LalalClient.StemFile> two = new java.util.ArrayList<LalalClient.StemFile>();
            two.add(new LalalClient.StemFile("vocals", "Vocals", acap, NpStemPadGains.ZONE_VOCALS));
            two.add(new LalalClient.StemFile("melody", "Instrumental", instr, NpStemPadGains.ZONE_MELODY));
            return two;
        }
        return null;
    }

    private void ensureMixer() {
        if (mixer != null) return;
        mixer = new StemMixer(host.appContext());
    }

    private void releaseMixer() {
        if (mixer != null) {
            try { mixer.release(); } catch (Exception ignored) {}
            mixer = null;
        }
    }
}
