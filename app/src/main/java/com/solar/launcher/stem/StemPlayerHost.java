package com.solar.launcher.stem;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.solar.launcher.PlayQueue;
import com.solar.launcher.ui.RowBusyChrome;
import com.solar.launcher.ui.UiBusy;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stem Player — puck (single) / StemFM bubbles (mashup) + Lalal prep + pad-local loop + beat roll.
 * Layman: one song = classic four pads; two songs = bubbles + repress crossfades that stem layer.
 * Technical: StemMixer[] always running; repress = timed gain fade between songs (not swapPadStem).
 * Mashup: hold any pad key → Options for that pad’s song; dual Prev+Next → session Options.
 * Short taps never open menus (focus/blend only). Song-end → Next-up when overflow; else pair-repeat.
 * Was: Prev/Next → song 0/1; Back/Play → session −1. Reversal: that mapping + STEM_TRANSITION_HOLD_MS.
 * 2026-07-19 / 2026-07-20 / 2026-07-21
 */
public final class StemPlayerHost {
    private static final long EXIT_HOLD_MS = 500L;

    /** True while Stem Player UI is attached — also mirrored on StemOrMixSession. 2026-07-19 */
    private static volatile boolean sessionActive;

    public interface HostCallbacks {
        SharedPreferences prefs();
        /** App context — internal cache + preferred storage for stem staging. 2026-07-19 */
        android.content.Context appContext();
        File appCacheDir();
        void setStatusTitle(String title);
        void onExitStemPlayer();
        void pauseMainMusic();
        void stopCompetingAudio();
        void toast(String msg);
        /** Force STREAM_MUSIC to max so pad gains own loudness. 2026-07-19 */
        void onStemSessionVolumeEnter();
        /** Restore STREAM_MUSIC saved on enter. 2026-07-20 */
        void onStemSessionVolumeExit();
        /**
         * Slot Replace → pick another track (queue or library). Other side keeps playing.
         * Was: TRANSITION reassign only. Reversal: toast stub / no mid-jam swap.
         * 2026-07-20 / 2026-07-21
         */
        void onRequestStemSongReassign(int songIndex);
        /** Theme primary text colour for mashup focus halo (fail-open yellow). 2026-07-20 */
        int stemFocusHaloColor();
        /**
         * Unified play queue for StemFM pair advance / Next-up (nullable → survivor handoff).
         * 2026-07-21
         */
        com.solar.launcher.PlayQueue stemMixPlayQueue();
        /** Persist + refresh after queue reorder (advance / hold-replace). 2026-07-21 */
        void onStemMixQueueMutated();
        /** Dual-hold / Play → open existing play-queue editor (footer Add included). 2026-07-21 */
        void openStemMixPlayQueue();
        /**
         * Open Solar ThemedContextMenu for jam (Play) or slot (hold Prev/Next).
         * Layman: same options menu as the rest of Solar — Home chip exits.
         * Was: in-face transitionPanel. Reversal: openSessionContextMenu / openSlotContextMenu.
         * slotSongIndex &lt; 0 → session rows; else Track N slot rows.
         * Must NOT call stopCompetingAudio / pauseMainMusic.
         * 2026-07-21
         */
        void openStemMixContextMenu(int slotSongIndex);

        /**
         * Close jam Options if a short tap raced the hold timer.
         * Layman: put away the accidental menu so the pad focus tap still wins.
         * Was: no dismiss — spurious Options stuck until Back. Reversal: empty method.
         * 2026-07-21
         */
        void dismissStemMixContextMenu();

        /** Apply pair-advance swap on unified queue; return next file for softReplace. 2026-07-21 */
        File applyStemPairAdvance(int finishedLiveSlot);
    }

    private final HostCallbacks host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicInteger jobGen = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    /**
     * Work leaves that still need durable publish after detach.
     * Layman: sticky notes — copy these scratch stems when Stem Player closes.
     * Was: separateToMp3 published immediately. Reversal: clear list; no flush.
     * 2026-07-21
     */
    private final java.util.ArrayList<StemDeferredPublish.Pending> pendingPublishes =
            new java.util.ArrayList<StemDeferredPublish.Pending>();

    private FrameLayout root;
    /** Single-track silicone puck. Mashup uses {@link #mashupFace}. 2026-07-20 */
    private StemFaceView face;
    /** Dual-bubble StemFM face when session.isMulti(). Was: StemFaceView for mashup. 2026-07-20 */
    private StemMashupFaceView mashupFace;
    /** Embedded covers matched byte-for-byte → letter+tint even if album tags differ. 2026-07-20 */
    private boolean mashupSameArtBytes;
    /** Mid-jam one-side stem prep — other song must keep playing. −1 = none. 2026-07-20 */
    private int replacingSongIndex = -1;
    /** Guard against double song-end handoff. 2026-07-20 */
    private boolean mashupHandoffBusy;
    /**
     * Soft-restart: skip mashDrift yank on this song until uptime ms (unequal lengths).
     * Layman: don’t snap the restarted track back to the other song’s playhead.
     * Was: mashDrift seekAllPlaying fought pair-repeat → mid-jam stutter. Reversal: −1 always.
     * 2026-07-21
     */
    private int softRestartSkipDriftSong = -1;
    private long softRestartSkipDriftUntilMs;
    /** Coalesce pad face paints during fades (one invalidate per looper pass / throttled to max 30Hz). 2026-07-21 */
    private boolean faceInvalidatePending;
    private long lastFaceInvalidateTimeMs;
    private final Runnable faceInvalidateRunnable = new Runnable() {
        @Override
        public void run() {
            faceInvalidatePending = false;
            lastFaceInvalidateTimeMs = android.os.SystemClock.uptimeMillis();
            refreshFaceNow();
        }
    };
    private TextView titleView;
    private TextView hintView;
    private TextView statusView;
    /**
     * Context overlay — slot (Replace/Scrub) or session (queue/TRANSITION/Exit).
     * Was: TRANSITION-only panel (6 rows). Reversal: openTransitionMenu rows.
     * 2026-07-20 / 2026-07-21
     */
    private LinearLayout transitionPanel;
    private int transitionFocus = 0;
    /** 0=slot context · 1=session context · 2=soft scrub. 2026-07-21 */
    private int contextMode = 0;
    private static final int CTX_SLOT = 0;
    private static final int CTX_SESSION = 1;
    private static final int CTX_SCRUB = 2;
    private int slotContextSong = 0;
    private int contextRowCount = StemMixContextRows.SESSION_ROW_COUNT;
    /** Soft-scrub dual timeline (same song, new seek). 2026-07-21 */
    private int scrubSong = -1;
    private int scrubCursorMs;
    private int scrubDurationMs;
    private TextView scrubStatusView;
    private View scrubBarView;
    private ProgressBar scrubBar;
    private StemMixer scrubGhostMixer;
    private boolean scrubBlendBusy;
    /** Last prep toast phase/percent for milestone throttle. 2026-07-21 */
    private String prepToastPhase;
    private int prepToastPercent = -1;
    private File prepToastFile;
    /**
     * Hold-OK circular face scrub — preview on focused pad; seek is whole song mixer.
     * Layman: lit pad picks which track; confirm jumps vocals+drums+bass+melody together.
     * Was: scrub only via full-screen ProgressBar overlay. Reversal: faceScrubArmed=false always.
     * Sibling song mixers never touched. 2026-07-21
     */
    private boolean faceScrubArmed;
    /** Center hold armed face scrub this press (release keeps scrub; later OK commits). 2026-07-21 */
    private boolean centerScrubHoldFired;
    private final Runnable centerScrubHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (!centerDown || !ready || faceScrubArmed) return;
            if (transitionPanel != null) return;
            if (!StemControls.centerHoldArmsPadScrub(ready, activeZone)) return;
            // Focused pad → song seat; seek will hit that StemMixer entirely. 2026-07-21
            armFaceScrub(focusedSongIndex());
            centerScrubHoldFired = true;
            notePadInteraction();
        }
    };
    /**
     * 2s quiet → clear pad focus + shrink face (anti-accident).
     * Layman: hands off long enough and the bubbles go small / unlit.
     * First pad press after idle only re-focuses (session activeZone −1 → no cycle).
     * Audio path unchanged. Was: focus forever. Reversal: never post this runnable.
     * 2026-07-21
     */
    private boolean padsIdle;
    private long lastPadInteractUptimeMs;
    private final Runnable padIdleDefocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ready || loading || padsIdle) return;
            if (transitionPanel != null || faceScrubArmed) return;
            // Finger still down — wait for release before sleeping. 2026-07-21
            if (prevDown || nextDown || backDown || playDown || centerDown) return;
            if (!StemControls.padIdleShouldDefocus(lastPadInteractUptimeMs,
                    android.os.SystemClock.uptimeMillis())) {
                return;
            }
            applyPadIdleDefocus();
        }
    };
    /** Active mashup crossfade duration (TRANSITION preset). 2026-07-20 */
    private long transitionMs = StemControls.TRANSITION_DEFAULT_MS;
    private int transitionPreset = StemControls.TRANSITION_PRESET_LONG;
    /** Zone gain crossfade between two song mixers. 2026-07-20 */
    private int xfadeZone = -1;
    private int xfadeFromSong = -1;
    private int xfadeToSong = -1;
    private float xfadeFromGain;
    private float xfadeToStartGain;
    private float xfadeToTargetGain;
    private int xfadeStep;
    private int xfadeSteps;
    /** Preset for in-flight pad xfade (WAVE for repress; song TRANSITION for replace). 2026-07-21 */
    private int xfadePreset = StemControls.TRANSITION_PRESET_WAVE;
    private boolean transitionHoldFired;
    /** 2026-07-20 — Host for status text + inline prep spinner (RowBusyChrome). */
    private LinearLayout statusBusyHost;
    /** One StemMixer per song — all timelines run; gains audition stems. 2026-07-19 */
    private StemMixer[] mixers;
    private final StemSession session = new StemSession();
    /** Soft cross-song playhead nudge (song0 lead). 2026-07-19 */
    private final Runnable mashDriftRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ready || mixers == null || mixers.length < 2) return;
            // Closed pair / soft-restart: skip lockstep — unequal lengths must not yank. 2026-07-21
            // Was: always seek slaves to lead → stutter after song-end restart.
            int qSize = stemQueueSize();
            if (StemMixQueuePolicy.shouldPairRepeat(qSize)) {
                main.postDelayed(this, 3000);
                return;
            }
            if (loading || session == null || !session.isMulti()) {
                main.postDelayed(this, 3000);
                return;
            }
            StemMixer lead = mixers[0];
            if (lead == null) return;
            int pos = lead.getPositionMs();
            if (pos < 0) {
                main.postDelayed(this, 3000);
                return;
            }
            long now = android.os.SystemClock.uptimeMillis();
            for (int i = 1; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                // Don't yank a song that is in a pad-local loop. 2026-07-19
                if (m == null || m.isLooping()) continue;
                // Soft-restart grace: leave this mixer at its new 0 until fade settles. 2026-07-21
                if (i == softRestartSkipDriftSong && now < softRestartSkipDriftUntilMs) continue;
                try {
                    // Scale by tempo rate — SoundTouch slaves run media clock ≠ wall. 2026-07-20
                    float rate = m.getTargetRate();
                    StemSession.SongState st = session.song(i);
                    if (st != null && st.tempoRate > 0.1f) rate = st.tempoRate;
                    int expect = StemTempoSync.expectedSlavePosMs(pos, rate);
                    int d = Math.abs(m.getPositionMs() - expect);
                    if (d > 400) m.seekAllPlaying(expect);
                } catch (Exception ignored) {}
            }
            main.postDelayed(this, 3000);
        }
    };
    private File track;
    private java.util.ArrayList<File> tracks = new java.util.ArrayList<File>();
    private int activeZone;
    private boolean loading;
    private boolean ready;
    private boolean armed;
    /** True when wheel adjusts loop bars (Center toggled loop-edit). Volume is the default. */
    private boolean wheelLoopMode;
    /**
     * Per-stem: joined loop-control while A–B is playing (active song).
     * 2026-07-19
     */
    private final boolean[] zoneLoopCtrl = new boolean[StemMixer.STEM_COUNT];
    /** Chop step for hold beat-roll slice (StemBpm.CHOP_FRAC). 2026-07-19 / 2026-07-20 */
    private int stutterChopStep = StemControls.DEFAULT_STUTTER_CHOP_STEP;
    /** Ladder position while in loop-edit (may be none before A–B arms). 2026-07-19 */
    private float editLoopBars = StemControls.LOOP_BARS_NONE;
    private float savedLoopBars = StemControls.DEFAULT_LOOP_BARS;
    private boolean songFinished;

    private boolean prevDown;
    private boolean nextDown;
    /** Local uptime when pad Options key went down (MTK KeyEvent times lie). 2026-07-21 */
    private long padOptionsDownUptimeMs;
    private boolean exitHoldFired;
    /** Stem key hold → chop/screw (zone while held). 2026-07-19 */
    private int stemHoldZone = -1;
    private boolean stemStutterArmed;
    /** Prior gain when mute-boost armed; <0 means no temp boost. 2026-07-19 */
    private float chopSavedGain = -1f;
    private int chopFadeZone = -1;
    private float chopFadeFrom;
    private float chopFadeTo;
    private int chopFadeStep;
    private static final int CHOP_FADE_STEPS = 8;
    /** Rate before hold screw; restored on release. 2026-07-19 */
    private float chopSavedRate = 1f;
    /**
     * Center held while beat roll armed → wheel nudges screw (peek), not roll size.
     * Layman: keep Center down during a roll to twist the slowdown dial.
     * Was: Center always toggled loop on UP. Reversal: ignore centerDown in onWheel.
     * 2026-07-20
     */
    private boolean centerDown;
    /** True if Center was held at any time while beat roll was armed (skip loop tap on UP). 2026-07-20 */
    private boolean centerScrewPeeked;
    /** Mashup: hold Back → jam session Options. 2026-07-21 */
    private boolean backDown;
    private boolean backContextHoldFired;
    /** Mashup: hold Play → jam Options; short UP = Melody pad. 2026-07-21 */
    private boolean playDown;
    private boolean playContextHoldFired;
    /**
     * Song-end while a prior soft-restart/replace fade is running — bit per seat.
     * Layman: if both tracks end close together, restart each when its turn comes.
     * Was: single pendingCompleteSong (second seat could be dropped). Reversal: one int −1.
     * 2026-07-21
     */
    private int pendingCompleteMask;

    /**
     * User has pressed OK to start the jam (Centre = Play until then, then shuffle).
     * Layman: set levels / scrub first; tap OK when ready to hear it.
     * Was: auto-play on mixer ready. Reversal: playbackStarted=true in onAllMixersReady.
     * 2026-07-21
     */
    private boolean playbackStarted;

    private final Runnable exitHoldRunnable = new Runnable() {
        @Override
        public void run() {
            // Single-track: dual side-key exit. Mashup dual → session context. 2026-07-21
            // Was: mashup dual no-op here. Reversal: if (session.isMulti()) return;
            if (session.isMulti()) {
                if (!StemControls.stemSessionContextBothSidesHeld(prevDown, nextDown)) return;
                exitHoldFired = true;
                transitionHoldFired = true;
                stopStemStutter();
                // Dual-hold → jam context (Home chip exits). Was: openSessionContextMenu panel.
                // 2026-07-21
                try {
                    host.openStemMixContextMenu(-1);
                } catch (Exception e) {
                    openSessionContextMenu();
                }
                return;
            }
            if (!StemControls.stemExitBothSidesHeld(prevDown, nextDown)) return;
            exitHoldFired = true;
            stopStemStutter();
            requestExit();
        }
    };

    /**
     * Mashup: hold Prev OR Next alone → Options for that pad’s live song.
     * Layman: keep Drums/Bass down to swap the track on that bubble.
     * Was: one-side → hardcoded song 0/1. Reversal: song = prevDown ? 0 : 1.
     * 2026-07-20 / 2026-07-21
     */
    private final Runnable transitionHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (!session.isMulti()) return;
            if (!StemControls.stemSlotHoldOneSide(prevDown, nextDown)) return;
            transitionHoldFired = true;
            stopStemStutter();
            // Prev=Drums(1), Next=Bass(2) → song currently on that pad. 2026-07-21
            int zone = StemControls.padZoneForOptionsHoldKey(false, prevDown, nextDown, false);
            openPadSongOptions(zone);
        }
    };

    /**
     * Hold Back/top → Options for Vocals pad’s song (short tap still flips Vocals).
     * Layman: keep Back down to replace the track feeding the top bubble.
     * Was: Back hold → session Options (−1). Reversal: openStemMixContextMenu(-1).
     * 2026-07-21
     */
    private final Runnable backContextHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (!backDown) return;
            if (!StemControls.mashupPadHoldOpensSlotContext(session.isMulti())) return;
            backContextHoldFired = true;
            openPadSongOptions(0);
        }
    };

    /**
     * Hold Play → Options for Melody pad’s song; short Play stays Melody focus/blend.
     * Layman: keep Play down to replace the bottom bubble’s track; tap still arms Melody.
     * Was: Play hold → session Options (−1). Reversal: openStemMixContextMenu(-1).
     * 2026-07-21
     */
    private final Runnable playContextHoldRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playDown) return;
            if (!StemControls.mashupPlayHoldOpensContext(session.isMulti())) return;
            playContextHoldFired = true;
            openPadSongOptions(3);
        }
    };

    /**
     * Open jam Options for the song feeding a pad zone (ThemedContextMenu preferred).
     * Layman: light that pad first, then show Replace/Scrub for its track.
     * Dual Prev+Next still uses session rows via exitHoldRunnable (−1).
     * Was: menu without focusing the pad (title/focus lagged). Reversal: drop focusPadForOptions.
     * 2026-07-21
     */
    private void openPadSongOptions(int zone) {
        if (zone < 0 || !session.isMulti()) return;
        focusPadForOptions(zone);
        int song = session.songIndexForZone(zone);
        song = StemControls.clampSongIndex(song, session.songCount());
        try {
            host.openStemMixContextMenu(song);
        } catch (Exception e) {
            openSlotContextMenu(song);
        }
    }

    /**
     * Focus a pad before Options so title + queue OK track the held bubble.
     * Layman: holding a button also selects that pad’s song.
     * 2026-07-21
     */
    private void focusPadForOptions(int zone) {
        if (zone < 0) return;
        session.setActiveZone(zone);
        activeZone = zone;
        armed = true;
        if (face != null) face.setActiveZone(zone);
        if (mashupFace != null) mashupFace.setActiveZone(zone);
        notePadInteraction();
        syncHostFromActiveSong();
        updateInteractedTrackTitle();
        updateStatusLine();
        scheduleFaceInvalidate();
    }

    /**
     * Mark pad/wheel/scrub activity — grow pads + restart 2s idle timer.
     * Layman: any real poke wakes the bubbles and starts a fresh quiet countdown.
     * 2026-07-21
     */
    private void notePadInteraction() {
        lastPadInteractUptimeMs = android.os.SystemClock.uptimeMillis();
        main.removeCallbacks(padIdleDefocusRunnable);
        if (padsIdle) {
            padsIdle = false;
            if (mashupFace != null) mashupFace.setPadsIdle(false);
            scheduleFaceInvalidate();
        }
        if (!ready || loading) return;
        // Don’t arm idle while Options / scrub own the face. 2026-07-21
        if (transitionPanel != null || faceScrubArmed) return;
        main.postDelayed(padIdleDefocusRunnable, StemControls.PAD_IDLE_DEFOCUS_MS);
    }

    /**
     * Clear focus + shrink pads after idle — next short pad key focuses only (no replace).
     * Layman: put the bubbles to sleep so bumps don’t flip songs.
     * Technical: session.clearActiveZone so stemKeyShouldCycleSong is false on first press.
     * Audio unchanged. Reversal: drop call site / never clear.
     * 2026-07-21
     */
    private void applyPadIdleDefocus() {
        if (padsIdle) return;
        session.clearActiveZone();
        activeZone = -1;
        padsIdle = true;
        if (face != null) face.setActiveZone(-1);
        if (mashupFace != null) {
            mashupFace.setPadsIdle(true);
            mashupFace.clearPadScrub();
        }
        refreshFace();
        updateStatusLine();
    }

    /** Cancel idle timer (detach / leave). 2026-07-21 */
    private void cancelPadIdleTimer() {
        main.removeCallbacks(padIdleDefocusRunnable);
        padsIdle = false;
        lastPadInteractUptimeMs = 0L;
    }

    /**
     * Short tap wins over a raced Options timer — dismiss spurious menu; return intentional hold.
     * Layman: if Options popped on a quick press, close it and let the pad focus run.
     * Technical: physical KeyEvent hold &lt; mashupOptionsHoldMs → undo; else keep menu.
     * Was: holdFired alone skipped onStemKey (short press stuck in Options). Reversal: that gate.
     * @return true when Options should stay (real long-hold)
     * 2026-07-21
     */
    private boolean finishPadOptionsHoldIfSpurious(boolean holdFired, boolean exitFired,
            KeyEvent event) {
        if (exitFired) return true;
        long held = StemControls.bestPhysicalHoldMs(
                padOptionsDownUptimeMs,
                android.os.SystemClock.uptimeMillis(),
                event != null ? event.getDownTime() : 0L,
                event != null ? event.getEventTime() : 0L);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("holdFired", holdFired);
            d.put("heldMs", held);
            d.put("localDown", padOptionsDownUptimeMs);
            d.put("keep", StemControls.isIntentionalPadOptionsHold(holdFired, held));
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.finishPadOptionsHoldIfSpurious", "options hold gate", "G", d);
        } catch (Exception ignored) {}
        // #endregion
        if (StemControls.isIntentionalPadOptionsHold(holdFired, held)) {
            return true;
        }
        if (holdFired) {
            // Timer already opened ThemedContextMenu — put it away. 2026-07-21
            try {
                host.dismissStemMixContextMenu();
            } catch (Exception ignored) {}
            closeTransitionMenu();
        }
        return false;
    }

    /** Timed gain crossfade for one pad zone between two song mixers. 2026-07-20 */
    private final Runnable zoneCrossfadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (xfadeZone < 0 || xfadeFromSong < 0 || xfadeToSong < 0) return;
            xfadeStep++;
            float t = xfadeSteps < 1 ? 1f : (xfadeStep / (float) xfadeSteps);
            // Equal-power + stem stagger (vocals lag). Was: linear fadeGainStep. 2026-07-21
            boolean eq = StemBlendGains.useEqualPowerForPreset(xfadePreset);
            boolean bassSnap = eq;
            float gFrom;
            float gTo;
            if (eq) {
                gFrom = StemBlendGains.staggeredOutGain(xfadeFromGain, t, xfadeZone, bassSnap);
                gTo = StemBlendGains.staggeredInGain(xfadeToTargetGain, t, xfadeZone, bassSnap);
                // Preserve start offset on the incoming side. 2026-07-21
                if (xfadeToStartGain > 0.001f && xfadeToTargetGain > 0.001f) {
                    float rise = gTo / xfadeToTargetGain;
                    gTo = xfadeToStartGain + (xfadeToTargetGain - xfadeToStartGain) * rise;
                }
            } else {
                gFrom = StemControls.fadeGainStep(xfadeFromGain, 0f, xfadeStep, xfadeSteps);
                gTo = StemControls.fadeGainStep(
                        xfadeToStartGain, xfadeToTargetGain, xfadeStep, xfadeSteps);
            }
            applySongZoneGain(xfadeFromSong, xfadeZone, gFrom);
            applySongZoneGain(xfadeToSong, xfadeZone, gTo);
            scheduleFaceInvalidate();
            if (xfadeStep >= xfadeSteps) {
                // Hard solo: outgoing zone mute — never leave both songs on one pad. 2026-07-21
                // Was: clear xfadeZone only (stagger lag could leave residual). Reversal: that.
                finalizePadZoneSolo(xfadeFromSong, xfadeToSong, xfadeZone, xfadeToTargetGain);
                xfadeZone = -1;
                xfadeFromSong = -1;
                xfadeToSong = -1;
                updateStatusLine();
                return;
            }
            main.postDelayed(this, StemControls.TRANSITION_TICK_MS);
        }
    };

    private final Runnable stemStutterHoldRunnable = new Runnable() {
        @Override
        public void run() {
            StemMixer m = activeMixer();
            if (stemHoldZone < 0 || !ready || m == null) return;
            if (prevDown && nextDown) return; // exit hold wins
            stemStutterArmed = true;
            // Focus pad for chop without cycling (UP will skip onStemKey). 2026-07-19
            activeZone = stemHoldZone;
            session.setActiveZone(stemHoldZone);
            armed = true;
            StemSession.SongState st = session.activeSongState();
            if (st != null && st.chopStep > 0) {
                stutterChopStep = StemBpm.clampChopStep(st.chopStep);
            }
            float bpm = m.getBpm();
            int slice = StemBpm.chopSliceMs(bpm, stutterChopStep);
            if (slice <= 0) {
                stutterChopStep = StemControls.DEFAULT_STUTTER_CHOP_STEP;
                slice = StemBpm.chopSliceMs(bpm, stutterChopStep);
            }
            if (slice <= 0) slice = StemMixer.DEFAULT_STUTTER_MS;
            // Mute pad → temp raise so beat roll is audible; fade back on release. 2026-07-19
            float g = m.getGain(stemHoldZone);
            chopSavedGain = -1f;
            if (StemControls.needsTempRollGain(g)) {
                chopSavedGain = g;
                m.setGain(stemHoldZone, StemControls.TEMP_CHOP_GAIN);
                if (st != null) st.gains[stemHoldZone] = StemControls.TEMP_CHOP_GAIN;
            }
            // Companion screw slowdown while held (IJK pads); compose with tempo bus. 2026-07-20
            chopSavedRate = m.getTargetRate();
            float screw = (st != null && st.screwRate > 0.1f && st.screwRate < 0.99f)
                    ? st.screwRate : StemControls.DEFAULT_HOLD_SCREW_RATE;
            float tempo = chopSavedRate > 0.1f ? chopSavedRate : 1f;
            if (st != null && st.tempoRate > 0.1f) tempo = st.tempoRate;
            m.setHoldScrewRate(StemTempoSync.composePadRate(tempo, screw));
            m.startBeatRoll(stemHoldZone, slice);
            if (st != null) {
                st.chopOn = true;
                st.chopStep = stutterChopStep;
            }
            // Center already down when chop arms → treat as screw peek. 2026-07-20
            if (centerDown) centerScrewPeeked = true;
            refreshFace();
            updateStatusLine();
        }
    };

    /** Smooth fade after mute-boost chop release. 2026-07-19 */
    private final Runnable chopGainFadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (chopFadeZone < 0) return;
            StemMixer m = mixerAt(session.songIndexForZone(chopFadeZone));
            chopFadeStep++;
            float g = StemControls.fadeGainStep(chopFadeFrom, chopFadeTo, chopFadeStep, CHOP_FADE_STEPS);
            if (m != null) m.setGain(chopFadeZone, g);
            StemSession.SongState st = session.song(session.songIndexForZone(chopFadeZone));
            if (st != null) st.gains[chopFadeZone] = g;
            refreshFace();
            if (chopFadeStep >= CHOP_FADE_STEPS) {
                chopFadeZone = -1;
                updateStatusLine();
                return;
            }
            main.postDelayed(this, StemControls.TEMP_GAIN_FADE_MS / CHOP_FADE_STEPS);
        }
    };

    public StemPlayerHost(HostCallbacks host) {
        this.host = host;
    }

    /**
     * True while Stem Player UI is attached.
     * Prefer {@link com.solar.launcher.StemOrMixSession#isActive()} for Stem+Mix shared gates.
     * 2026-07-19
     */
    public static boolean isSessionActive() {
        return sessionActive;
    }

    /**
     * Live song index under the focused pad (0..songCount-1) for queue OK soft-replace.
     * Layman: which mashup track the lit pad is playing — that seat gets the queue pick.
     * Was: no public focus API. Reversal: callers hardcode 0.
     * 2026-07-21 Stems/Mix sanity
     */
    public int focusedSongIndex() {
        if (session == null || session.songCount() < 1) return 0;
        int z = session.activeZone();
        if (z < 0) z = 0;
        return session.songIndexForZone(z);
    }

    /**
     * How many live mashup tracks are loaded (1–2).
     * Layman: one or two songs on the Stem face right now.
     * 2026-07-21
     */
    public int liveSongCount() {
        if (session == null) return 0;
        return session.songCount();
    }

    public boolean isDuplicateTrack(int targetSongIndex, File track) {
        return session != null && session.isDuplicateTrack(targetSongIndex, track);
    }


    /** Build full-screen stem UI into parent (single track). */
    public View attach(Context ctx, ViewGroup parent, File trackFile) {
        java.util.ArrayList<File> one = new java.util.ArrayList<File>();
        if (trackFile != null) one.add(trackFile);
        return attach(ctx, parent, one);
    }

    /**
     * Build stem UI for 1–2 tracks (mashup). Gains start at 0 — jam from silence.
     * Was: 1–3 tracks. Reversal: MAX_SONGS=3 comment only.
     * 2026-07-19 / 2026-07-20
     */
    public View attach(Context ctx, ViewGroup parent, List<File> trackFiles) {
        detach();
        sessionActive = true;
        com.solar.launcher.StemOrMixSession.setActive(true);
        try {
            host.onStemSessionVolumeEnter();
        } catch (Exception ignored) {}
        cancelled.set(false);
        tracks.clear();
        if (trackFiles != null) {
            for (int i = 0; i < trackFiles.size() && tracks.size() < StemSession.MAX_SONGS; i++) {
                File f = trackFiles.get(i);
                if (f != null && f.isFile()) tracks.add(f);
            }
        }
        session.bindTracks(tracks);
        this.track = tracks.isEmpty() ? null : tracks.get(0);
        // No pad focused yet — first stem key focuses only (must match session −1). 2026-07-19
        this.activeZone = -1;
        this.loading = true;
        this.ready = false;
        this.armed = false;
        this.wheelLoopMode = false;
        this.stutterChopStep = StemControls.DEFAULT_STUTTER_CHOP_STEP;
        this.editLoopBars = StemControls.LOOP_BARS_NONE;
        clearZoneLoopCtrl();
        stopStemStutter();
        this.songFinished = false;
        this.savedLoopBars = StemControls.DEFAULT_LOOP_BARS;
        this.transitionMs = StemControls.TRANSITION_DEFAULT_MS;
        this.transitionHoldFired = false;
        cancelZoneCrossfade();

        root = new FrameLayout(ctx);
        root.setBackgroundColor(0xFF0A0A0C);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams colLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        col.setLayoutParams(colLp);
        col.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 6));

        // Title shows interacted song (marquee for long names on 480×360). 2026-07-19
        String title = track != null ? stripTrackExt(track.getName()) : "Stem Player";
        if (tracks.size() > 1) title = tracks.size() + " tracks · Stem mashup";
        titleView = label(ctx, title, 15, true);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        titleView.setMarqueeRepeatLimit(-1);
        titleView.setHorizontallyScrolling(true);
        titleView.setSelected(true);
        col.addView(titleView);

        // 2026-07-20 — Status line + small spinner while stems prepare.
        // Was: status TextView alone. Reversal: col.addView(statusView) without RowBusyChrome.
        statusView = label(ctx, "Preparing…", 12, false);
        statusView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        statusView.setTextColor(0xFFB0B0B8);
        statusBusyHost = new LinearLayout(ctx);
        statusBusyHost.setOrientation(LinearLayout.HORIZONTAL);
        statusBusyHost.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusBusyHost.setLayoutParams(statusLp);
        LinearLayout.LayoutParams statusTextLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        statusView.setLayoutParams(statusTextLp);
        statusBusyHost.addView(statusView);
        ProgressBar statusSpin = RowBusyChrome.newSmallSpinner(ctx);
        statusSpin.setVisibility(View.VISIBLE);
        statusBusyHost.addView(statusSpin);
        col.addView(statusBusyHost);
        syncPrepBusyChrome();
        // Journey tip is ContextFeatureTip modal (MainActivity) — not status wall. 2026-07-21
        // Was: statusView pageProse + markQueueJourneySeen here. Reversal: restore that block.

        // Always StemMashupFaceView + corner dials — never legacy StemFaceView lights.
        // Was: single-track StemFaceView puck; mashup only when isMulti().
        // Reversal: if (session.isMulti()) mashup else StemFaceView block.
        // Context-menu Stem Player + pick jam share this face (prep dial during cook). 2026-07-21
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        faceLp.topMargin = dp(ctx, 2);
        mashupFace = new StemMashupFaceView(ctx);
        mashupFace.setLayoutParams(faceLp);
        col.addView(mashupFace);
        face = null;

        // No jam-face hint walls — context-modal tips teach input. 2026-07-21
        // Was: single-track Center=loop hintView + mashup hold-glyph cue overlays.
        // Reversal: restore hintView / cuePrev / cueNext block.
        hintView = null;
        if (titleView != null) {
            titleView.setVisibility(View.GONE);
        }
        root.addView(col);
        parent.removeAllViews();
        parent.addView(root);
        host.setStatusTitle("Stem Player");
        try {
            host.stopCompetingAudio();
        } catch (Exception ignored) {
            host.pauseMainMusic();
        }
        refreshFace();
        startJob();
        return root;
    }

    public void detach() {
        cancelled.set(true);
        jobGen.incrementAndGet();
        boolean wasActive = sessionActive;
        sessionActive = false;
        // Mix may still own the exclusive gate — only clear if Mix is not up. 2026-07-19
        if (!com.solar.launcher.mix.MixPlayerHost.isSessionActive()) {
            com.solar.launcher.StemOrMixSession.setActive(false);
        }
        if (wasActive) {
            try {
                host.onStemSessionVolumeExit();
            } catch (Exception ignored) {}
        }
        main.removeCallbacks(exitHoldRunnable);
        main.removeCallbacks(transitionHoldRunnable);
        main.removeCallbacks(backContextHoldRunnable);
        main.removeCallbacks(playContextHoldRunnable);
        playDown = false;
        playContextHoldFired = false;
        main.removeCallbacks(stemStutterHoldRunnable);
        main.removeCallbacks(chopGainFadeRunnable);
        main.removeCallbacks(zoneCrossfadeRunnable);
        main.removeCallbacks(mashDriftRunnable);
        main.removeCallbacks(faceInvalidateRunnable);
        faceInvalidatePending = false;
        softRestartSkipDriftSong = -1;
        mashupHandoffBusy = false;
        pendingCompleteMask = 0;
        softReplaceJob = null;
        playbackStarted = false;
        closeTransitionMenu();
        cancelZoneCrossfade();
        stopStemStutter();
        cancelFaceScrub(false);
        centerDown = false;
        centerScrewPeeked = false;
        centerScrubHoldFired = false;
        main.removeCallbacks(centerScrubHoldRunnable);
        cancelPadIdleTimer();
        releaseMixers();
        // After mixers stop — copy work stems to durable (or discard temps). 2026-07-21
        flushPendingStemPublishes();
        ready = false;
        armed = false;
        wheelLoopMode = false;
        editLoopBars = StemControls.LOOP_BARS_NONE;
        clearZoneLoopCtrl();
        if (root != null) {
            ViewGroup p = (ViewGroup) root.getParent();
            if (p != null) p.removeView(root);
            root = null;
        }
        face = null;
        mashupFace = null;
        transitionPanel = null;
        statusBusyHost = null;
        statusView = null;
        // 2026-07-20 — Drop stem prep throbber when leaving Stem Player.
        UiBusy.clear(UiBusy.REASON_STEM_PREPARE);
    }

    /**
     * Mixer for the song currently steered by a pad (wheel / loop / stutter).
     * Layman: which always-on song deck this pad’s knobs turn.
     * 2026-07-19
     */
    private StemMixer mixerAt(int songIndex) {
        if (mixers == null || songIndex < 0 || songIndex >= mixers.length) return null;
        return mixers[songIndex];
    }

    /** Mixer for the focused pad’s control song (song 0 if no focus). 2026-07-19 */
    private StemMixer activeMixer() {
        if (activeZone < 0) return mixerAt(0);
        return mixerAt(session.songIndexForZone(activeZone));
    }

    private boolean hasMixers() {
        return mixers != null && mixers.length > 0 && mixers[0] != null;
    }

    /** Release every song mixer — stop mash drift first. 2026-07-19 */
    private void releaseMixers() {
        main.removeCallbacks(mashDriftRunnable);
        if (mixers == null) return;
        for (int i = 0; i < mixers.length; i++) {
            StemMixer m = mixers[i];
            mixers[i] = null;
            releaseMixerAsync(m);
        }
        mixers = null;
    }

    private void releaseMixerAsync(final StemMixer m) {
        if (m == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try { m.release(); } catch (Exception ignored) {}
            }
        }, "StemHost-Release").start();
    }

    /**
     * Remember a work leaf for durable publish on detach (coalesced by path).
     * Layman: note “save these stems when we leave the player”.
     * 2026-07-21
     */
    private void rememberPendingPublish(File track, File workDir, boolean premix) {
        if (workDir == null) return;
        boolean persist = StemDeferredPublish.shouldPersistStems(track, host.appCacheDir());
        StemDeferredPublish.remember(pendingPublishes,
                new StemDeferredPublish.Pending(track, workDir, premix, persist));
    }

    /**
     * Drain pending work→durable copies onto the background executor.
     * Layman: Stem Player closed — quietly file the stem folders away.
     * 2026-07-21
     */
    private void flushPendingStemPublishes() {
        StemDeferredPublish.flushAll(host.appContext(), host.appCacheDir(), pendingPublishes);
    }

    /**
     * True when a ready dir is under app {@code lalal_work} (still needs durable publish).
     * Layman: these stems are still in the scratch pile, not the permanent shelf.
     * 2026-07-21
     */
    private static boolean isLalalWorkDir(android.content.Context ctx, File dir) {
        if (ctx == null || dir == null) return false;
        File workRoot = new File(ctx.getCacheDir(), "lalal_work");
        String root = workRoot.getAbsolutePath();
        String path = dir.getAbsolutePath();
        return path.equals(root) || path.startsWith(root + File.separator);
    }

    private void stopStemStutter() {
        main.removeCallbacks(stemStutterHoldRunnable);
        boolean wasArmed = stemStutterArmed;
        int zone = stemHoldZone >= 0 ? stemHoldZone
                : (wasArmed && activeZone >= 0 ? activeZone : -1);
        StemMixer m = zone >= 0 ? mixerAt(session.songIndexForZone(zone)) : activeMixer();
        if (m != null) {
            m.stopBeatRoll();
            if (wasArmed) {
                try {
                    m.setTargetRate(chopSavedRate > 0.1f ? chopSavedRate : 1f);
                } catch (Exception ignored) {}
            }
        }
        StemSession.SongState st = session.activeSongState();
        if (st != null) st.chopOn = false;
        if (wasArmed && chopSavedGain >= 0f && zone >= 0) {
            StemMixer fadeM = mixerAt(session.songIndexForZone(zone));
            float from = fadeM != null ? fadeM.getGain(zone) : StemControls.TEMP_CHOP_GAIN;
            chopFadeZone = zone;
            chopFadeFrom = from;
            chopFadeTo = chopSavedGain;
            chopFadeStep = 0;
            chopSavedGain = -1f;
            main.removeCallbacks(chopGainFadeRunnable);
            main.post(chopGainFadeRunnable);
        } else {
            chopSavedGain = -1f;
        }
        stemHoldZone = -1;
        stemStutterArmed = false;
    }

    private void syncLoopCtrlToMixer() {
        StemMixer m = activeMixer();
        if (m != null) m.setLoopCtrlMask(zoneLoopCtrl);
    }

    public void shutdown() {
        detach();
        io.shutdownNow();
    }

    /**
     * Hardware keys while Stem Player is open (DOWN + UP for holds).
     * @return true if consumed
     */
    public boolean onKey(int keyCode, KeyEvent event) {
        if (event == null) return false;
        int action = event.getAction();
        // #region agent log
        final long keyT0 = android.os.SystemClock.uptimeMillis();
        if (action == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("ready", ready);
                d.put("loading", loading);
                d.put("hasMixer", hasMixers());
                d.put("activeZone", activeZone);
                d.put("songs", mixers != null ? mixers.length : 0);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.onKey", "key UP while stem open", "A", d);
                // Sparse input latency probe for 3-song lag. 2026-07-19
                if (mixers != null && mixers.length >= 2) {
                    com.solar.launcher.Debug8b0481Log.log(
                            "StemPlayerHost.onKey", "key UP multi", "H2", d);
                }
            } catch (Exception ignored) {}
        }
        // #endregion

        // Hold PREV / NEXT:
        // — Mashup: one side → slot Replace/Scrub; dual → session context.
        // — Single: dual-hold exit; solo hold = beat roll on drums/bass.
        // Was: mashup one-side TRANSITION; dual unused. Reversal: that branch.
        // 2026-07-19 / 2026-07-20 / 2026-07-21
        if (isPrevKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                prevDown = true;
                padOptionsDownUptimeMs = android.os.SystemClock.uptimeMillis();
                exitHoldFired = false;
                transitionHoldFired = false;
                main.removeCallbacks(exitHoldRunnable);
                main.removeCallbacks(transitionHoldRunnable);
                if (session.isMulti()) {
                    stopStemStutter();
                    if (StemControls.stemSessionContextBothSidesHeld(prevDown, nextDown)) {
                        main.removeCallbacks(transitionHoldRunnable);
                        main.postDelayed(exitHoldRunnable, StemControls.STEM_TRANSITION_HOLD_MS);
                    } else if (StemControls.stemSlotHoldOneSide(prevDown, nextDown)) {
                        // Rapid pad Options (Prev=Drums / Next=Bass). 2026-07-21
                        main.postDelayed(transitionHoldRunnable, StemControls.mashupOptionsHoldMs());
                    }
                } else if (prevDown && nextDown) {
                    stopStemStutter();
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginStemHold(1);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                prevDown = false;
                main.removeCallbacks(exitHoldRunnable);
                main.removeCallbacks(transitionHoldRunnable);
                if (session.isMulti()) {
                    // Short tap = focus pad only; undo Options if hold timer raced UP. 2026-07-21
                    boolean intentional = finishPadOptionsHoldIfSpurious(
                            transitionHoldFired, exitHoldFired, event);
                    if (!intentional && !exitHoldFired && !nextDown) {
                        onStemKey(1);
                    } else {
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("pad", "prev/drums");
                            d.put("intentional", intentional);
                            d.put("exitHold", exitHoldFired);
                            d.put("holdFired", transitionHoldFired);
                            d.put("heldMs", StemControls.physicalKeyHoldMs(
                                    event.getDownTime(), event.getEventTime()));
                            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                                    "StemPlayerHost.prevUP", "skipped onStemKey", "G", d);
                        } catch (Exception ignored) {}
                        // #endregion
                    }
                    // Other side still down alone → arm pad Options for Next. 2026-07-21
                    if (nextDown && !exitHoldFired && transitionPanel == null) {
                        main.postDelayed(transitionHoldRunnable, StemControls.mashupOptionsHoldMs());
                    }
                    transitionHoldFired = false;
                    exitHoldFired = false;
                    return true;
                }
                boolean stuttered = endStemHold(1);
                if (!exitHoldFired && !stuttered && !nextDown) {
                    onStemKey(1);
                }
                exitHoldFired = false;
                return true;
            }
            return true;
        }
        if (isNextKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                nextDown = true;
                padOptionsDownUptimeMs = android.os.SystemClock.uptimeMillis();
                exitHoldFired = false;
                transitionHoldFired = false;
                main.removeCallbacks(exitHoldRunnable);
                main.removeCallbacks(transitionHoldRunnable);
                if (session.isMulti()) {
                    stopStemStutter();
                    if (StemControls.stemSessionContextBothSidesHeld(prevDown, nextDown)) {
                        main.removeCallbacks(transitionHoldRunnable);
                        main.postDelayed(exitHoldRunnable, StemControls.STEM_TRANSITION_HOLD_MS);
                    } else if (StemControls.stemSlotHoldOneSide(prevDown, nextDown)) {
                        // Pad Options only after intentional hold. 2026-07-21
                        main.postDelayed(transitionHoldRunnable, StemControls.mashupOptionsHoldMs());
                    }
                } else if (prevDown && nextDown) {
                    stopStemStutter();
                    main.postDelayed(exitHoldRunnable, EXIT_HOLD_MS);
                } else {
                    beginStemHold(2);
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                nextDown = false;
                main.removeCallbacks(exitHoldRunnable);
                main.removeCallbacks(transitionHoldRunnable);
                if (session.isMulti()) {
                    boolean intentional = finishPadOptionsHoldIfSpurious(
                            transitionHoldFired, exitHoldFired, event);
                    if (!intentional && !exitHoldFired && !prevDown) {
                        onStemKey(2);
                    } else {
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("pad", "next/bass");
                            d.put("intentional", intentional);
                            d.put("exitHold", exitHoldFired);
                            d.put("holdFired", transitionHoldFired);
                            d.put("heldMs", StemControls.physicalKeyHoldMs(
                                    event.getDownTime(), event.getEventTime()));
                            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                                    "StemPlayerHost.nextUP", "skipped onStemKey", "G", d);
                        } catch (Exception ignored) {}
                        // #endregion
                    }
                    if (prevDown && !exitHoldFired && transitionPanel == null) {
                        main.postDelayed(transitionHoldRunnable, StemControls.mashupOptionsHoldMs());
                    }
                    transitionHoldFired = false;
                    exitHoldFired = false;
                    return true;
                }
                boolean stuttered = endStemHold(2);
                if (!exitHoldFired && !stuttered && !prevDown) {
                    onStemKey(2);
                }
                exitHoldFired = false;
                return true;
            }
            return true;
        }

        // TRANSITION menu owns wheel / Center / Back while open. 2026-07-20
        if (transitionPanel != null) {
            return onTransitionMenuKey(keyCode, action, event);
        }

        // Center — short tap = shuffle / dial; Hold OK = circular scrub for focused pad’s song.
        // Confirm = later OK tap (whole-song seekAllPlaying / soft blend); Back cancels.
        // Was: UP always onCenterTap. Reversal: drop centerScrubHoldRunnable branch.
        // 2026-07-20 / 2026-07-21
        if (isStemCenterKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                centerDown = true;
                centerScrubHoldFired = false;
                if (stemStutterArmed) {
                    centerScrewPeeked = true;
                    updateStatusLine();
                } else if (!faceScrubArmed
                        && StemControls.centerHoldArmsPadScrub(ready, activeZone)) {
                    // Arm circular scrub after intentional hold — short OK still shuffles. 2026-07-21
                    main.removeCallbacks(centerScrubHoldRunnable);
                    main.postDelayed(centerScrubHoldRunnable,
                            StemControls.mashupCenterScrubHoldMs());
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                main.removeCallbacks(centerScrubHoldRunnable);
                // Peeked during chop (even if stem released first) — no loop toggle. 2026-07-20
                boolean skipTap = centerScrewPeeked;
                boolean holdEntered = centerScrubHoldFired;
                centerDown = false;
                centerScrewPeeked = false;
                centerScrubHoldFired = false;
                if (skipTap) {
                    updateStatusLine();
                    return true;
                }
                // Hold just armed scrub — release keeps scrub; do not shuffle/commit yet. 2026-07-21
                if (StemControls.centerReleaseKeepsFaceScrub(holdEntered)) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("holdEntered", holdEntered);
                        d.put("faceScrubArmed", faceScrubArmed);
                        d.put("padsIdle", padsIdle);
                        d.put("activeZone", activeZone);
                        com.solar.launcher.Debug897e32Log.log(host.appContext(),
                                "StemPlayerHost.centerUP", "scrub hold keep (no shuffle)",
                                "B", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    return true;
                }
                // Already scrubbing: short OK commits whole-song seek (beat-match). 2026-07-21
                if (StemControls.centerTapCommitsFaceScrub(faceScrubArmed, false)) {
                    commitFaceScrub();
                    return true;
                }
                if (ready && hasMixers()) {
                    // #region agent log
                    try {
                        org.json.JSONObject d = new org.json.JSONObject();
                        d.put("holdEntered", holdEntered);
                        d.put("faceScrubArmed", faceScrubArmed);
                        d.put("padsIdle", padsIdle);
                        d.put("activeZone", activeZone);
                        d.put("playbackStarted", playbackStarted);
                        com.solar.launcher.Debug897e32Log.log(host.appContext(),
                                "StemPlayerHost.centerUP", "dispatch onCenterTap", "A", d);
                    } catch (Exception ignored) {}
                    // #endregion
                    onCenterTap();
                }
                return true;
            }
            return true;
        }

        // BACK / top — mashup hold → Vocals pad Options; tap = Vocals pad.
        // Face scrub: Back cancels without seek (explicit confirm model). 2026-07-21
        // Exit = dual PREV+NEXT only (single) / session menu (mashup dual).
        // Was: mashup Back hold → session (−1). Reversal: openStemMixContextMenu(-1).
        // 2026-07-19 / 2026-07-21
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (faceScrubArmed) {
                if (action == KeyEvent.ACTION_UP) cancelFaceScrub(true);
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                backDown = true;
                padOptionsDownUptimeMs = android.os.SystemClock.uptimeMillis();
                backContextHoldFired = false;
                main.removeCallbacks(backContextHoldRunnable);
                // Options only after intentional hold — short Back focuses Vocals. 2026-07-21
                if (StemControls.mashupPadHoldOpensSlotContext(session.isMulti())) {
                    main.postDelayed(backContextHoldRunnable, StemControls.mashupOptionsHoldMs());
                }
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                main.removeCallbacks(backContextHoldRunnable);
                boolean intentional = finishPadOptionsHoldIfSpurious(
                        backContextHoldFired, false, event);
                if (!intentional) onStemKey(0);
                backDown = false;
                backContextHoldFired = false;
                return true;
            }
            return true;
        }

        // PLAY (bottom) — mashup: hold → Melody pad Options; short UP → Melody pad blend.
        // Single: Melody pad (+ beat-roll hold). Never MEDIA_PLAY (126) — Y1 wheel.
        // Was: mashup short Play UP opened Options; hold → session (−1).
        // Reversal: mashupPlayOpensContext UP + openStemMixContextMenu(-1).
        // 2026-07-19 / 2026-07-21
        if (com.solar.launcher.SolarPadKeys.isPadPlayKey(keyCode)) {
            // BT transport only when AVRCP — don’t steal wheel. 2026-07-21
            if (event != null
                    && com.solar.launcher.Y1BluetoothInput.isBluetoothTransportKey(event)) {
                return false;
            }
            // Mashup: hold opens Melody-song Options; short tap focuses pad 3. 2026-07-21
            if (StemControls.mashupPlayHoldOpensContext(session.isMulti())) {
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    playDown = true;
                    padOptionsDownUptimeMs = android.os.SystemClock.uptimeMillis();
                    playContextHoldFired = false;
                    main.removeCallbacks(playContextHoldRunnable);
                    main.postDelayed(playContextHoldRunnable,
                            StemControls.mashupOptionsHoldMs());
                    return true;
                }
                if (action == KeyEvent.ACTION_UP) {
                    main.removeCallbacks(playContextHoldRunnable);
                    boolean intentional = finishPadOptionsHoldIfSpurious(
                            playContextHoldFired, false, event);
                    if (!intentional) onStemKey(3);
                    playDown = false;
                    playContextHoldFired = false;
                    return true;
                }
                return true;
            }
            if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                beginStemHold(3);
                return true;
            }
            if (action == KeyEvent.ACTION_UP) {
                boolean stuttered = endStemHold(3);
                if (!stuttered) onStemKey(3);
                return true;
            }
            return true;
        }

        // #region agent log
        // Dead branch for stem keys (they return earlier); kept for non-stem fallthrough. 2026-07-19
        if (mixers != null && mixers.length >= 2
                && action == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("keyCode", keyCode);
                d.put("costMs", android.os.SystemClock.uptimeMillis() - keyT0);
                d.put("songs", mixers.length);
                d.put("fallthrough", true);
                com.solar.launcher.Debug8b0481Log.log(
                        "StemPlayerHost.onKey", "key fallthrough multi", "H2", d);
            } catch (Exception ignored) {}
        }
        // #endregion
        return false;
    }

    /**
     * Arm hold timer for hip-hop stutter on one stem (one at a time).
     * Layman: keep the pad down and it chatters; tap is focus only.
     * Technical: do NOT selectZone/setActiveZone on DOWN — that armed cycle on UP.
     * Was: selectZone(zone) when activeZone!=zone. Reversal: preview-only face tint.
     * 2026-07-19
     */
    private void beginStemHold(int zone) {
        // Disarm beat-roll / chop user entry (mute hygiene — seeks felt like restart). 2026-07-21
        // Was: mashupAllowsBeatRoll gate only. Reversal: drop userMayArmBeatRoll check.
        if (!StemControls.userMayArmBeatRoll(session.isMulti())) return;
        if (!StemControls.mashupAllowsBeatRoll(session.isMulti())) return;
        if (prevDown && nextDown) {
            // Dual side-key = exit — do not stutter. 2026-07-19
            main.removeCallbacks(stemStutterHoldRunnable);
            return;
        }
        stopStemStutter();
        stemHoldZone = zone;
        stemStutterArmed = false;
        // Preview which pad is pressed — session focus waits for UP / stutter arm. 2026-07-19
        if (face != null && zone >= 0) face.setActiveZone(zone);
        if (mashupFace != null && zone >= 0) mashupFace.setActiveZone(zone);
        notePadInteraction();
        main.removeCallbacks(stemStutterHoldRunnable);
        main.postDelayed(stemStutterHoldRunnable, StemControls.STEM_STUTTER_HOLD_MS);
    }

    /**
     * End stem hold. @return true if chop ran (skip focus/cycle tap).
     * 2026-07-19
     */
    private boolean endStemHold(int zone) {
        main.removeCallbacks(stemStutterHoldRunnable);
        boolean was = stemStutterArmed && stemHoldZone == zone;
        if (was || stemHoldZone == zone) {
            stopStemStutter();
        } else {
            stemHoldZone = -1;
            stemStutterArmed = false;
        }
        refreshFace();
        updateStatusLine();
        return was;
    }

    /**
     * Short stem key: focus pad, or crossfade that zone to the other song when multi.
     * songCount==1: focus only — classic single-track UX.
     * Repress = timed gain fade between mixers — never seek/swap/restart always-on song mixers.
     * Was: instant routing cycle + toast. Reversal: toast-only cycle without startZoneCrossfade.
     * Was: replaceZoneStem on cycle (playhead jump). Reversal: call swapPadStem again.
     * 2026-07-19 / 2026-07-20
     */
    private void onStemKey(int zone) {
        // #region agent log
        long t0 = android.os.SystemClock.uptimeMillis();
        // #endregion
        // Decide cycle from session focus BEFORE any host write. 2026-07-19
        boolean willCycle = StemControls.stemKeyShouldCycleSong(
                session.activeZone(), zone, session.songCount());
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("zone", zone);
            d.put("ready", ready);
            d.put("padsIdle", padsIdle);
            d.put("hostActive", activeZone);
            d.put("sessionActive", session.activeZone());
            d.put("willCycle", willCycle);
            d.put("songCount", session.songCount());
            d.put("songForZone", session.songIndexForZone(zone));
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.onStemKey", "focus/cycle decide", "F", d);
        } catch (Exception ignored) {}
        // #endregion
        boolean cycled = session.onStemKey(zone);
        activeZone = session.activeZone();
        armed = true;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("zone", zone);
            d.put("cycled", cycled);
            d.put("afterActive", activeZone);
            d.put("songForZone", session.songIndexForZone(zone));
            d.put("costMs", android.os.SystemClock.uptimeMillis() - t0);
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.onStemKey", "after session.onStemKey", "F", d);
        } catch (Exception ignored) {}
        // #endregion
        if (cycled && session.isMulti()) {
            // Timed gain crossfade this zone SongA→SongB; other pads keep playing. 2026-07-20
            // Was: toast song name only (instant mute/steer). Reversal: toast block above.
            int toSong = session.songIndexForZone(zone);
            int fromSong = StemControls.otherSongIndex(toSong, session.songCount());
            startZoneCrossfade(zone, fromSong, toSong);
            String name = session.trackDisplayNameForZone(zone);
            if (name == null || name.length() == 0) {
                name = "Song " + session.displaySongNumber(zone);
            }
            host.toast(name);
        }
        syncHostFromActiveSong();
        selectZone(activeZone);
        updateInteractedTrackTitle();
        // Restart idle countdown — first press after sleep only focused (no cycle). 2026-07-21
        notePadInteraction();
        // #region agent log
        if (session.songCount() >= 2) {
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("zone", zone);
                d.put("cycled", cycled);
                d.put("songs", session.songCount());
                d.put("costMs", android.os.SystemClock.uptimeMillis() - t0);
                com.solar.launcher.Debug8b0481Log.log(
                        "StemPlayerHost.onStemKey", "stem key cost", "H2", d);
            } catch (Exception ignored) {}
        }
        // #endregion
    }

    /**
     * Pull loop-ctrl + wheel mode from the song under the focused arm.
     * Layman: each song keeps its own mute/loop slate when you switch control.
     * Technical: host zoneLoopCtrl ← SongState; apply mask to that song’s mixer only.
     * Was: single mixer setLoopCtrlMask. Reversal: mixer.setLoopCtrlMask on one instance.
     * 2026-07-19
     */
    private void syncHostFromActiveSong() {
        StemSession.SongState st = session.activeSongState();
        if (st == null) return;
        for (int i = 0; i < zoneLoopCtrl.length; i++) {
            zoneLoopCtrl[i] = st.zoneLoopCtrl[i];
        }
        wheelLoopMode = false;
        if (st.loopBars > 0f) savedLoopBars = st.loopBars;
        if (st.chopStep > 0) stutterChopStep = StemBpm.clampChopStep(st.chopStep);
        StemMixer m = activeMixer();
        if (m == null) return;
        m.setLoopCtrlMask(zoneLoopCtrl);
    }

    /**
     * Push pad-owned gains onto mixers — active song gets pad level; other song’s zone muted.
     * Layman: each pad’s dial feeds only the song that pad is on.
     * Was: copy SongState.gains per song (dual-audible risk). Reversal: that loop.
     * 2026-07-19 / 2026-07-21
     */
    private void syncAllSongGainsToMixers() {
        if (mixers == null) return;
        for (int z = 0; z < StemMixer.STEM_COUNT; z++) {
            // Skip zone mid-crossfade — runnable owns those gains. 2026-07-21
            if (z == xfadeZone) continue;
            float level = session.padGain(z);
            int active = session.songIndexForZone(z);
            for (int s = 0; s < mixers.length; s++) {
                applySongZoneGain(s, z, s == active ? level : 0f);
            }
        }
    }

    /**
     * Apply gain to one song’s zone on SongState + StemMixer (mixer bookkeeping).
     * Does NOT change {@link StemSession#padGain} — pad level is separate.
     * Layman: set how loud this song’s stem is right now (crossfade ticks).
     * 2026-07-20 / 2026-07-21
     */
    private void applySongZoneGain(int songIndex, int zone, float gain) {
        float g = StemControls.clampGain(gain);
        StemSession.SongState st = session.song(songIndex);
        if (st != null && zone >= 0 && zone < st.gains.length) st.gains[zone] = g;
        StemMixer m = mixerAt(songIndex);
        if (m != null) m.setGain(zone, g);
    }

    /**
     * Start timed crossfade of one pad zone from song A → song B at the **pad’s** level.
     * Layman: blend that stem into the other track — pad dial stays unless it was silent.
     * Silent / ~1% pads auto-bump to ~10% so the new stem is heard; louder pads unchanged.
     * After fade: outgoing muted; incoming = padGain (one song per pad).
     * Was: always keep padGain (silent stayed silent). Reversal: level = session.padGain only.
     * 2026-07-20 / 2026-07-21
     */
    private void startZoneCrossfade(int zone, int fromSong, int toSong) {
        if (zone < 0 || zone >= StemMixer.STEM_COUNT) return;
        if (fromSong < 0 || toSong < 0 || fromSong == toSong) return;
        cancelZoneCrossfade();
        // Pad-owned level; bump only when at/below dual-start floor. 2026-07-21
        float prior = session.padGain(zone);
        float level = StemControls.padGainAfterTrackSwitch(prior);
        if (level != prior) session.setPadGain(zone, level);
        xfadeFromGain = prior;
        xfadeToStartGain = 0f;
        xfadeToTargetGain = level;
        xfadeZone = zone;
        xfadeFromSong = fromSong;
        xfadeToSong = toSong;
        xfadeStep = 0;
        xfadePreset = StemControls.TRANSITION_PRESET_WAVE;
        xfadeSteps = StemControls.transitionFadeSteps(StemControls.padRepressTransitionMs());
        main.post(zoneCrossfadeRunnable);
    }

    /**
     * Force one-song-per-pad after a zone blend ends (or is cancelled mid-way).
     * Layman: kill the old track’s stem on this pad; keep the new one at pad level.
     * Pad gain unchanged. 2026-07-21
     */
    private void finalizePadZoneSolo(int fromSong, int toSong, int zone, float toGain) {
        if (zone < 0 || zone >= StemMixer.STEM_COUNT) return;
        // Prefer live padGain so cancel mid-fade doesn’t invent a level. 2026-07-21
        float level = session.padGain(zone);
        if (toGain > 0.001f) level = toGain;
        float[] g = StemControls.padZoneSoloFinalGains(level);
        if (fromSong >= 0) applySongZoneGain(fromSong, zone, g[0]);
        if (toSong >= 0) applySongZoneGain(toSong, zone, g[1]);
        // Keep SongState of active song aligned with padGain for bookkeeping. 2026-07-21
        StemSession.SongState st = session.song(toSong);
        if (st != null && zone < st.gains.length) st.gains[zone] = level;
        refreshFace();
    }

    /** Stop an in-flight zone crossfade — still solo the destination. 2026-07-20 / 2026-07-21 */
    private void cancelZoneCrossfade() {
        main.removeCallbacks(zoneCrossfadeRunnable);
        if (xfadeZone >= 0 && xfadeFromSong >= 0 && xfadeToSong >= 0) {
            finalizePadZoneSolo(xfadeFromSong, xfadeToSong, xfadeZone, session.padGain(xfadeZone));
        }
        xfadeZone = -1;
        xfadeFromSong = -1;
        xfadeToSong = -1;
    }

    /**
     * Read ID3 title/artist/album + decode embedded art for mashup pads.
     * Layman: use the real song name letter, not “1” from the file name; show covers when present.
     * Technical: AudioTags on IO; BitmapFactory on IO; setArt on main.
     * 2026-07-20
     */
    private void loadMashupMetaAndArt() {
        // Always load for complications face (single-track context menu too). 2026-07-21
        // Was: if (!session.isMulti() || mashupFace == null) return;
        if (mashupFace == null) return;
        final int gen = jobGen.get();
        final SharedPreferences prefs = host.prefs();
        final File[] files = new File[session.songCount()];
        for (int i = 0; i < files.length; i++) {
            StemSession.SongState st = session.song(i);
            files[i] = st != null ? st.track : null;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                if (cancelled.get() || gen != jobGen.get()) return;
                final String[] titles = new String[files.length];
                final String[] artists = new String[files.length];
                final String[] albums = new String[files.length];
                final android.graphics.Bitmap[] arts = new android.graphics.Bitmap[files.length];
                final byte[][] artBytes = new byte[files.length][];
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    if (f == null || !f.isFile()) continue;
                    try {
                        com.solar.launcher.AudioTags.Info info =
                                com.solar.launcher.AudioTags.read(f, prefs);
                        titles[i] = info.title != null ? info.title.trim() : "";
                        artists[i] = info.artist != null ? info.artist.trim() : "";
                        albums[i] = info.album != null ? info.album.trim() : "";
                        if (info.embeddedArt != null && info.embeddedArt.length > 64) {
                            artBytes[i] = info.embeddedArt;
                            arts[i] = android.graphics.BitmapFactory.decodeByteArray(
                                    info.embeddedArt, 0, info.embeddedArt.length);
                            // Downscale for MT6572 — pad is ~120px. 2026-07-20
                            if (arts[i] != null && (arts[i].getWidth() > 256 || arts[i].getHeight() > 256)) {
                                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(
                                        arts[i], 256, 256, true);
                                if (scaled != arts[i]) {
                                    arts[i].recycle();
                                    arts[i] = scaled;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                boolean sameBytes = false;
                if (artBytes.length >= 2 && artBytes[0] != null && artBytes[1] != null
                        && artBytes[0].length == artBytes[1].length && artBytes[0].length > 0) {
                    sameBytes = java.util.Arrays.equals(artBytes[0], artBytes[1]);
                }
                final boolean sameArt = sameBytes;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelled.get() || gen != jobGen.get() || mashupFace == null) return;
                        for (int i = 0; i < files.length; i++) {
                            StemSession.SongState st = session.song(i);
                            if (st == null) continue;
                            if (titles[i] != null && titles[i].length() > 0) st.id3Title = titles[i];
                            if (artists[i] != null) st.id3Artist = artists[i];
                            if (albums[i] != null) st.id3Album = albums[i];
                            mashupFace.setArt(i, arts[i]);
                        }
                        mashupSameArtBytes = sameArt;
                        refreshFace();
                        updateInteractedTrackTitle();
                    }
                });
            }
        });
    }

    /**
     * Soft-replace one mashup song mid-jam — fade out → prep throbber → fade in.
     * Layman: the old track dissolves, a spinner shows while stems load, then the new one swells in.
     * Technical: fadeSongGainsOut → IO resolve → reloadMixerAt → fadeSongGainsIn; sibling mixers untouched.
     * Was: hard-cut release old mixer then fade-in only; loading=false hid throbber.
     * Reversal: release immediately; loading=false; skip fade-out.
     * 2026-07-20 / 2026-07-21
     */
    public void softReplaceSong(int songIndex, File trackFile) {
        if (!session.isMulti() || trackFile == null || !trackFile.isFile()) return;
        if (songIndex < 0 || songIndex >= session.songCount()) return;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("songIndex", songIndex);
            d.put("file", trackFile.getName());
            d.put("transitionMs", transitionMs);
            d.put("loading", loading);
            d.put("handoffBusy", mashupHandoffBusy);
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.softReplaceSong", "softReplace enter", "D", d);
        } catch (Exception ignored) {}
        // #endregion
        if (session != null && session.isDuplicateTrack(songIndex, trackFile)) {
            host.toast("Cannot mix a track with itself");
            closeTransitionMenu();
            return;
        }
        closeTransitionMenu();
        StemSession.SongState old = session.song(songIndex);
        final float[] prior = new float[StemMixer.STEM_COUNT];
        float audible = 0f;
        if (old != null) {
            for (int z = 0; z < prior.length; z++) {
                prior[z] = old.gains[z];
                if (prior[z] > audible) audible = prior[z];
            }
        }
        // Keep prior levels for fade-in even after SongState gains hit 0. 2026-07-21
        if (audible < 0.01f) {
            for (int z = 0; z < prior.length; z++) {
                if (session.songIndexForZone(z) == songIndex) prior[z] = 1f;
            }
        }
        if (!session.replaceSongTrack(songIndex, trackFile)) return;
        if (songIndex < tracks.size()) tracks.set(songIndex, trackFile);
        final int song = songIndex;
        final int gen = jobGen.incrementAndGet();
        final boolean premix = LalalAccount.isPremixExperimental(host.prefs());
        final String key = LalalAccount.effectiveKey(host.prefs());
        final android.content.Context ctx = host.appContext();
        replacingSongIndex = song;
        // Throbber while stems cook / mixer reloads — sibling song keeps mixing. 2026-07-21
        loading = true;
        if (statusView != null) {
            statusView.setText("Loading · "
                    + StemControls.stripTrackDisplayName(trackFile.getName()));
            statusView.setTextColor(0xFFB0B0B8);
        }
        syncPrepBusyChrome();
        refreshFace();
        SoftReplaceJob job = new SoftReplaceJob();
        job.gen = gen;
        job.song = song;
        job.prior = prior;
        softReplaceJob = job;
        // Fade out audible pads first (TRANSITION length / equal-power). 2026-07-21
        fadeSongGainsOut(song, prior, new Runnable() {
            @Override
            public void run() {
                SoftReplaceJob j = softReplaceJob;
                if (j == null || j.gen != gen) return;
                j.fadeOutDone = true;
                tryCommitSoftReplace(gen);
            }
        });
        io.execute(new Runnable() {
            @Override
            public void run() {
                if (cancelled.get() || gen != jobGen.get()) return;
                try {
                    final List<LalalClient.StemFile> stems = resolveStemsForTrack(
                            trackFile, premix, key, ctx, gen);
                    File stemDir = LalalClient.findReadyStemDir(
                            ctx, trackFile, premix, host.appCacheDir());
                    final File bassBody = StemBassBody.existingOrNull(stemDir);
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (cancelled.get() || gen != jobGen.get()) return;
                            SoftReplaceJob j = softReplaceJob;
                            if (j == null || j.gen != gen) return;
                            j.stems = stems;
                            j.bassBody = bassBody;
                            j.stemsReady = true;
                            tryCommitSoftReplace(gen);
                        }
                    });
                } catch (Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get()) return;
                            SoftReplaceJob j = softReplaceJob;
                            if (j != null && j.gen == gen) j.failed = true;
                            softReplaceJob = null;
                            replacingSongIndex = -1;
                            loading = false;
                            syncPrepBusyChrome();
                            host.toast("Could not load track");
                            refreshFace();
                        }
                    });
                }
            }
        });
    }

    /**
     * Pending soft-replace — wait for fade-out + stems before swapping the mixer.
     * Layman: don’t hard-cut until the old track has dissolved and the new pads are ready.
     * 2026-07-21
     */
    private static final class SoftReplaceJob {
        int gen;
        int song;
        float[] prior;
        List<LalalClient.StemFile> stems;
        File bassBody;
        boolean fadeOutDone;
        boolean stemsReady;
        boolean failed;
    }

    private SoftReplaceJob softReplaceJob;

    /**
     * When fade-out and stem files are both ready, reload that seat and fade in.
     * 2026-07-21
     */
    private void tryCommitSoftReplace(int gen) {
        SoftReplaceJob j = softReplaceJob;
        if (j == null || j.gen != gen || j.failed) return;
        if (!j.fadeOutDone || !j.stemsReady) return;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("song", j.song);
            d.put("fadeOutDone", j.fadeOutDone);
            d.put("stemsReady", j.stemsReady);
            d.put("stemCount", j.stems != null ? j.stems.size() : -1);
            d.put("transitionMs", transitionMs);
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.tryCommitSoftReplace", "commit reload+fadeIn", "D", d);
        } catch (Exception ignored) {}
        // #endregion
        softReplaceJob = null;
        StemSession.SongState st = session.song(j.song);
        if (st == null) {
            replacingSongIndex = -1;
            loading = false;
            syncPrepBusyChrome();
            return;
        }
        st.stems = j.stems;
        st.bassBody = j.bassBody;
        for (int z = 0; z < st.gains.length; z++) st.gains[z] = 0f;
        if (statusView != null) {
            statusView.setText("Crossfading · "
                    + session.trackDisplayNameForSong(j.song));
            statusView.setTextColor(0xFFB0B0B8);
        }
        reloadMixerAt(j.song, j.prior, gen);
    }

    /**
     * Fade all zones of one song from current levels toward silence (pre soft-replace).
     * Layman: dissolve that track’s pads before swapping the file underneath.
     * Equal-power for LONG/∞; linear for WAVE. Sibling song untouched.
     * 2026-07-21
     */
    private void fadeSongGainsOut(final int songIndex, final float[] fromGains,
            final Runnable onDone) {
        if (fromGains == null) {
            if (onDone != null) main.post(onDone);
            return;
        }
        boolean any = false;
        for (int z = 0; z < fromGains.length; z++) {
            if (fromGains[z] > 0.001f) {
                any = true;
                break;
            }
        }
        if (!any) {
            if (onDone != null) main.post(onDone);
            return;
        }
        final int steps = StemControls.transitionFadeSteps(transitionMs);
        final boolean eq = StemBlendGains.useEqualPowerForPreset(transitionPreset);
        final int[] step = new int[] { 0 };
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!sessionActive) {
                    if (onDone != null) onDone.run();
                    return;
                }
                step[0]++;
                float t = steps < 1 ? 1f : (step[0] / (float) steps);
                for (int z = 0; z < StemMixer.STEM_COUNT && z < fromGains.length; z++) {
                    float g;
                    if (eq) {
                        g = StemBlendGains.staggeredOutGain(fromGains[z], t, z, true);
                    } else {
                        g = StemControls.fadeGainStep(fromGains[z], 0f, step[0], steps);
                    }
                    applySongZoneGain(songIndex, z, g);
                }
                scheduleFaceInvalidate();
                if (step[0] >= steps) {
                    for (int z = 0; z < StemMixer.STEM_COUNT && z < fromGains.length; z++) {
                        applySongZoneGain(songIndex, z, 0f);
                    }
                    if (onDone != null) onDone.run();
                    return;
                }
                main.postDelayed(this, StemControls.TRANSITION_TICK_MS);
            }
        };
        main.post(tick);
    }

    /**
     * Reload one StemMixer after softReplaceSong; fade gains in with TRANSITION length.
     * Never touches sibling mixers — live jam on the other song stays uninterrupted.
     * 2026-07-20
     */
    private void reloadMixerAt(final int songIndex, final float[] targetGains, final int gen) {
        if (mixers == null || songIndex < 0 || songIndex >= mixers.length || root == null) {
            replacingSongIndex = -1;
            loading = false;
            syncPrepBusyChrome();
            return;
        }
        StemSession.SongState st = session.song(songIndex);
        if (st == null || st.stems == null || st.stems.isEmpty()) {
            replacingSongIndex = -1;
            loading = false;
            syncPrepBusyChrome();
            return;
        }
        // Release ONLY this slot after fade-out — sibling StemMixer keeps playing. 2026-07-20
        StemMixer old = mixers[songIndex];
        releaseMixerAsync(old);
        final StemMixer m = new StemMixer(root.getContext());
        mixers[songIndex] = m;
        m.setBpm(st.bpm > 30f ? st.bpm : StemBpm.DEFAULT_BPM);
        m.setListener(new StemMixer.Listener() {
            @Override
            public void onReady() {
                if (gen != jobGen.get() || cancelled.get()) return;
                replacingSongIndex = -1;
                loading = false;
                syncPrepBusyChrome();
                try { m.play(); } catch (Exception ignored) {}
                // Timed fade-in to prior pad levels (equal-power when LONG). 2026-07-20 / 2026-07-21
                fadeSongGainsIn(songIndex, targetGains);
                loadMashupMetaAndArt();
                refreshFace();
                updateStatusLine();
                updateInteractedTrackTitle();
            }

            @Override
            public void onError(String message) {
                if (gen != jobGen.get()) return;
                replacingSongIndex = -1;
                loading = false;
                syncPrepBusyChrome();
                host.toast(message != null ? message : "Stem load failed");
            }

            @Override
            public void onComplete() {
                // Mashup: hand off to the other song — never silence the jam. 2026-07-20
                onSongPlaybackComplete(songIndex);
            }
        });
        try {
            m.load(st.stems, st.bassBody);
        } catch (Exception e) {
            replacingSongIndex = -1;
            loading = false;
            syncPrepBusyChrome();
            host.toast("Stem load failed");
        }
    }

    /**
     * Fade all zones of one song from 0 toward targets (post soft-replace / pair-repeat).
     * Layman: bring the song’s pads up to where they sat without a hard click.
     * Equal-power for LONG/∞; linear for wave. Was: linear only. 2026-07-20 / 2026-07-21
     */
    private void fadeSongGainsIn(final int songIndex, final float[] targets) {
        if (targets == null) return;
        final int steps = StemControls.transitionFadeSteps(transitionMs);
        final boolean eq = StemBlendGains.useEqualPowerForPreset(transitionPreset);
        final int[] step = new int[] { 0 };
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!sessionActive) return;
                step[0]++;
                float t = steps < 1 ? 1f : (step[0] / (float) steps);
                for (int z = 0; z < StemMixer.STEM_COUNT && z < targets.length; z++) {
                    float g;
                    if (eq) {
                        g = StemBlendGains.staggeredInGain(targets[z], t, z, true);
                    } else {
                        g = StemControls.fadeGainStep(0f, targets[z], step[0], steps);
                    }
                    applySongZoneGain(songIndex, z, g);
                }
                // Coalesce face paints — fade ticks are dense on MT6572. 2026-07-21
                scheduleFaceInvalidate();
                if (step[0] >= steps) return;
                main.postDelayed(this, StemControls.TRANSITION_TICK_MS);
            }
        };
        main.post(tick);
    }

    /**
     * Slot context — Replace Track N / Scrub.
     * Was: TRANSITION menu on one-side hold. Reversal: openTransitionMenu().
     * 2026-07-21
     */
    private void openSlotContextMenu(int songIndex) {
        if (root == null || transitionPanel != null) return;
        slotContextSong = StemControls.clampSongIndex(songIndex, session.songCount());
        contextMode = CTX_SLOT;
        String[] rows = StemMixContextRows.slotRows(slotContextSong + 1);
        contextRowCount = rows.length;
        showContextRows(rows, "Track " + (slotContextSong + 1));
    }

    /**
     * Dual-hold session context — Play queue / TRANSITION / Exit.
     * Was: mashup dual unused; TRANSITION on one-side. Reversal: that split.
     * 2026-07-21
     */
    private void openSessionContextMenu() {
        if (root == null || transitionPanel != null) return;
        contextMode = CTX_SESSION;
        String[] rows = StemMixContextRows.sessionRows(false);
        contextRowCount = rows.length;
        showContextRows(rows, "Stem");
    }

    /** Shared overlay list for slot/session context. 2026-07-21 */
    private void showContextRows(String[] rows, String statusTitle) {
        Context ctx = root.getContext();
        transitionPanel = new LinearLayout(ctx);
        transitionPanel.setOrientation(LinearLayout.VERTICAL);
        transitionPanel.setBackgroundColor(0xEE121218);
        transitionPanel.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        transitionPanel.setLayoutParams(lp);
        transitionFocus = 0;
        for (int i = 0; i < rows.length; i++) {
            TextView row = label(ctx, rows[i], 13, false);
            row.setPadding(dp(ctx, 8), dp(ctx, 6), dp(ctx, 8), dp(ctx, 6));
            transitionPanel.addView(row);
        }
        root.addView(transitionPanel);
        paintTransitionFocus();
        host.setStatusTitle(statusTitle != null ? statusTitle : "");
    }

    /**
     * Soft scrub — Hold-OK face path preferred; overlay if mashup face missing.
     * Layman: open scrub for that song without pausing the other pads.
     * Confirm seeks entire StemMixer for that song (all stem layers together).
     * Was: ProgressBar overlay only. Reversal: openSoftScrubOverlay body only.
     * 2026-07-21
     */
    private void openSoftScrubOverlay(int songIndex) {
        if (mashupFace != null) {
            armFaceScrub(songIndex);
            return;
        }
        closeTransitionMenu();
        if (root == null) return;
        StemMixer live = mixerAt(songIndex);
        if (live == null) {
            host.toast("Track not ready");
            return;
        }
        scrubSong = songIndex;
        scrubDurationMs = Math.max(1, live.getDurationMs());
        scrubCursorMs = StemMixSoftScrub.clampSeekMs(live.getPositionMs(), scrubDurationMs);
        contextMode = CTX_SCRUB;
        Context ctx = root.getContext();
        transitionPanel = new LinearLayout(ctx);
        transitionPanel.setOrientation(LinearLayout.VERTICAL);
        transitionPanel.setBackgroundColor(0xEE121218);
        transitionPanel.setPadding(dp(ctx, 16), dp(ctx, 14), dp(ctx, 16), dp(ctx, 14));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        transitionPanel.setLayoutParams(lp);
        scrubStatusView = label(ctx, StemMixSoftScrub.statusLine(scrubCursorMs, scrubDurationMs), 18, true);
        scrubStatusView.setGravity(Gravity.CENTER_HORIZONTAL);
        transitionPanel.addView(scrubStatusView);
        scrubBar = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        scrubBar.setMax(1000);
        scrubBar.setProgress(Math.round(StemMixSoftScrub.thumbFrac(scrubCursorMs, scrubDurationMs) * 1000f));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 28));
        barLp.topMargin = dp(ctx, 16);
        scrubBar.setLayoutParams(barLp);
        transitionPanel.addView(scrubBar);
        TextView tip = label(ctx, "Wheel · Center commit · Back cancel", 12, false);
        tip.setGravity(Gravity.CENTER_HORIZONTAL);
        tip.setPadding(0, dp(ctx, 12), 0, 0);
        transitionPanel.addView(tip);
        root.addView(transitionPanel);
        host.setStatusTitle("Scrub");
    }

    /**
     * Arm circular face scrub for one live song seat (focused pad chooses seat).
     * Layman: shrink the glow, spin the seek ball; other song keeps playing.
     * Technical: preview only until commitFaceScrub → seekAllPlaying / soft blend on that mixer.
     * Whole-song lockstep: every stem layer for scrubSong moves together — never one pad alone.
     * Was: full-viewport ProgressBar overlay. Reversal: call overlay-only openSoftScrubOverlay.
     * 2026-07-21
     */
    private void armFaceScrub(int songIndex) {
        closeTransitionMenu();
        if (root == null) return;
        StemMixer live = mixerAt(songIndex);
        if (live == null) {
            host.toast("Track not ready");
            return;
        }
        scrubSong = songIndex;
        scrubDurationMs = Math.max(1, live.getDurationMs());
        scrubCursorMs = StemMixSoftScrub.clampSeekMs(live.getPositionMs(), scrubDurationMs);
        faceScrubArmed = true;
        contextMode = CTX_SCRUB;
        // Scrub owns the pad — hold idle timer until cancel/commit. 2026-07-21
        main.removeCallbacks(padIdleDefocusRunnable);
        if (padsIdle) {
            padsIdle = false;
            if (mashupFace != null) mashupFace.setPadsIdle(false);
        }
        lastPadInteractUptimeMs = android.os.SystemClock.uptimeMillis();
        paintFaceScrubCursor();
        if (statusView != null) {
            statusView.setText(StemMixSoftScrub.statusLine(scrubCursorMs, scrubDurationMs));
        }
        host.setStatusTitle("Scrub");
        try {
            host.toast("Wheel · OK land · Back cancel");
        } catch (Exception ignored) {}
    }

    /** Push scrub frac onto mashup face (visual only). 2026-07-21 */
    private void paintFaceScrubCursor() {
        if (mashupFace == null) return;
        if (!faceScrubArmed) {
            mashupFace.clearPadScrub();
            return;
        }
        mashupFace.setPadScrub(true, StemMixSoftScrub.thumbFrac(scrubCursorMs, scrubDurationMs));
    }

    /**
     * Cancel Hold-OK scrub without seeking (Back).
     * Layman: leave the needle where the song was; other pads untouched.
     * @param restoreTitle when true, put Stem title back in the status bar.
     * 2026-07-21
     */
    private void cancelFaceScrub(boolean restoreTitle) {
        boolean wasArmed = faceScrubArmed;
        faceScrubArmed = false;
        if (contextMode == CTX_SCRUB && !scrubBlendBusy) {
            scrubSong = -1;
            contextMode = CTX_SLOT;
        }
        if (mashupFace != null) mashupFace.clearPadScrub();
        if (restoreTitle) updateInteractedTrackTitle();
        updateStatusLine();
        // Resume idle countdown only after a real scrub session. 2026-07-21
        if (wasArmed) notePadInteraction();
    }

    /**
     * Confirm face scrub — beat-match then whole-song soft seek (sibling mixer untouched).
     * Layman: land all of that track’s pads on the same beat; the other song keeps going.
     * Technical: beatMatchSeekMs → commitSoftScrub (seekAllPlaying / ghost blend on scrubSong only).
     * Explicit confirm (not commit-on-release). Was: overlay Center only. Reversal: cancelFaceScrub.
     * 2026-07-21
     */
    private void commitFaceScrub() {
        if (!faceScrubArmed) return;
        faceScrubArmed = false;
        if (mashupFace != null) mashupFace.clearPadScrub();
        // beatMatch + whole-song seekAllPlaying / soft blend inside commitSoftScrub. 2026-07-21
        commitSoftScrub();
        notePadInteraction();
    }

    /** Dismiss slot/session/scrub overlay. Face Hold-OK scrub stays until cancel/commit. 2026-07-20 / 2026-07-21 */
    private void closeTransitionMenu() {
        if (transitionPanel != null && root != null) {
            try {
                root.removeView(transitionPanel);
            } catch (Exception ignored) {}
        }
        transitionPanel = null;
        scrubStatusView = null;
        scrubBar = null;
        scrubBarView = null;
        // Overlay scrub only — do not clear Hold-OK face scrub mid-arm. 2026-07-21
        if (contextMode == CTX_SCRUB && !scrubBlendBusy && !faceScrubArmed) {
            scrubSong = -1;
            contextMode = CTX_SLOT;
            updateInteractedTrackTitle();
            return;
        }
        if (!faceScrubArmed) {
            contextMode = CTX_SLOT;
            updateInteractedTrackTitle();
        }
    }

    /** Highlight focused context row. 2026-07-20 */
    private void paintTransitionFocus() {
        if (transitionPanel == null || contextMode == CTX_SCRUB) return;
        for (int i = 0; i < transitionPanel.getChildCount(); i++) {
            View v = transitionPanel.getChildAt(i);
            if (!(v instanceof TextView)) continue;
            TextView tv = (TextView) v;
            boolean on = i == transitionFocus;
            tv.setTextColor(on ? 0xFFFFFFFF : 0xFF9A9AA8);
            tv.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
            tv.setBackgroundColor(on ? 0x33FFFFFF : Color.TRANSPARENT);
        }
    }

    /**
     * Keys while slot/session/scrub overlay is open.
     * Layman: wheel picks a row or moves the scrub needle; Center confirms; Back closes.
     * 2026-07-20 / 2026-07-21
     */
    private boolean onTransitionMenuKey(int keyCode, int action, KeyEvent event) {
        if (contextMode == CTX_SCRUB) {
            return onScrubOverlayKey(keyCode, action, event);
        }
        if (action != KeyEvent.ACTION_UP || event.getRepeatCount() != 0) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            closeTransitionMenu();
            return true;
        }
        if (isStemCenterKey(keyCode) || com.solar.launcher.SolarPadKeys.isPadPlayKey(keyCode)) {
            applyContextSelection(transitionFocus);
            return true;
        }
        return true;
    }

    /** Soft-scrub overlay keys. 2026-07-21 */
    private boolean onScrubOverlayKey(int keyCode, int action, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (action == KeyEvent.ACTION_UP) closeTransitionMenu();
            return true;
        }
        if (isStemCenterKey(keyCode) || com.solar.launcher.SolarPadKeys.isPadPlayKey(keyCode)) {
            if (action == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                commitSoftScrub();
            }
            return true;
        }
        return true;
    }

    /** Apply slot or session row. 2026-07-21 */
    private void applyContextSelection(int row) {
        if (contextMode == CTX_SLOT) {
            if (row == StemMixContextRows.SLOT_REPLACE) {
                closeTransitionMenu();
                try {
                    host.onRequestStemSongReassign(slotContextSong);
                } catch (Exception ignored) {}
            } else if (row == StemMixContextRows.SLOT_PLAY_QUEUE) {
                closeTransitionMenu();
                try {
                    host.openStemMixPlayQueue();
                } catch (Exception ignored) {}
            } else if (row == StemMixContextRows.SLOT_START_NEXT) {
                closeTransitionMenu();
                try {
                    File next = host.applyStemPairAdvance(slotContextSong);
                    if (next != null) softReplaceSong(slotContextSong, next);
                    else host.toast("No next track");
                } catch (Exception ignored) {}
            } else if (row == StemMixContextRows.SLOT_SCRUB) {
                openSoftScrubOverlay(slotContextSong);
            }
            return;
        }
        if (contextMode == CTX_SESSION) {
            int preset = StemMixContextRows.transitionPresetForSessionRow(row);
            if (preset >= 0) {
                transitionPreset = preset;
                transitionMs = StemControls.transitionMsForPreset(preset);
                host.toast(preset == StemControls.TRANSITION_PRESET_OVERLAP ? "Blend ∞"
                        : preset == StemControls.TRANSITION_PRESET_WAVE ? "Blend wave" : "Blend LONG");
                closeTransitionMenu();
                return;
            }
            if (row == StemMixContextRows.SESSION_PLAY_QUEUE) {
                closeTransitionMenu();
                try {
                    host.openStemMixPlayQueue();
                } catch (Exception ignored) {}
                return;
            }
            if (StemMixContextRows.isSessionPauseRow(row)) {
                closeTransitionMenu();
                pauseJamPlayback();
                return;
            }
            if (StemMixContextRows.isSessionHomeRow(row)) {
                closeTransitionMenu();
                requestExit();
                return;
            }
            // Exit also via Home chip — kept. 2026-07-21
        }
    }

    /**
     * Commit soft scrub — dual-timeline equal-power blend to scrubCursorMs on one song.
     * Layman: fade that whole track to the new spot; vocals/drums/bass/melody stay locked.
     * Technical: ghost StemMixer + seekAllPlaying(target); sibling song mixer gains untouched.
     * Was: hard seek. Reversal: mixer.seekAllPlaying only.
     * 2026-07-21
     */
    private void commitSoftScrub() {
        final int song = scrubSong;
        // Beat-match so sections can meet — whole mixer lands on one grid. 2026-07-21
        float bpm = StemBpm.DEFAULT_BPM;
        StemSession.SongState bpmSt = session.song(song);
        if (bpmSt != null && bpmSt.bpm > 30f) bpm = bpmSt.bpm;
        else {
            StemMixer bm = mixerAt(song);
            if (bm != null) {
                bpm = bm.getBpm() > 30f ? bm.getBpm()
                        : StemBpm.estimateFromDurationMs(scrubDurationMs);
            }
        }
        final int targetMs = StemMixSoftScrub.beatMatchSeekMs(
                scrubCursorMs, scrubDurationMs, bpm);
        scrubCursorMs = targetMs;
        faceScrubArmed = false;
        if (mashupFace != null) mashupFace.clearPadScrub();
        closeTransitionMenu();
        if (song < 0 || scrubBlendBusy) return;
        final StemMixer live = mixerAt(song);
        if (live == null || root == null) return;
        StemSession.SongState st = session.song(song);
        if (st == null || st.stems == null || st.stems.isEmpty()) {
            // No stem files — duck → seek whole mixer → fade back (never hard cut). 2026-07-21
            softSeekWholeSongDuck(live, song, targetMs);
            return;
        }
        scrubBlendBusy = true;
        final float[] fromGains = new float[StemMixer.STEM_COUNT];
        for (int z = 0; z < fromGains.length; z++) fromGains[z] = st.gains[z];
        // Ghost mixer starts muted at target; live fades out. 2026-07-21
        final StemMixer ghost = new StemMixer(root.getContext());
        scrubGhostMixer = ghost;
        ghost.setBpm(st.bpm > 30f ? st.bpm : StemBpm.DEFAULT_BPM);
        ghost.setListener(new StemMixer.Listener() {
            @Override
            public void onReady() {
                try {
                    ghost.seekAllPlaying(targetMs);
                    ghost.play();
                } catch (Exception ignored) {}
                runSoftScrubBlend(song, live, ghost, fromGains);
            }

            @Override
            public void onError(String message) {
                scrubBlendBusy = false;
                releaseScrubGhost();
                softSeekWholeSongDuck(live, song, targetMs);
            }

            @Override
            public void onComplete() {}
        });
        try {
            ghost.load(st.stems, st.bassBody);
        } catch (Exception e) {
            scrubBlendBusy = false;
            releaseScrubGhost();
            softSeekWholeSongDuck(live, song, targetMs);
        }
    }

    /**
     * Soft seek without ghost mixer — duck pads, seekAllPlaying, fade back.
     * Layman: slide the whole song to the new spot without a click.
     * Sibling song mixer never touched. Was: hard seekAllPlaying. Reversal: that one-liner.
     * 2026-07-21
     */
    private void softSeekWholeSongDuck(final StemMixer live, final int song, final int targetMs) {
        if (live == null || song < 0) return;
        final float[] targets = new float[StemMixer.STEM_COUNT];
        StemSession.SongState st = session.song(song);
        for (int z = 0; z < StemMixer.STEM_COUNT; z++) {
            float g = 0f;
            if (st != null && z < st.gains.length) g = st.gains[z];
            if (session.isMulti() && session.songIndexForZone(z) == song
                    && StemControls.isGainSilent(g)) {
                g = session.padGain(z);
            }
            targets[z] = g;
            applySongZoneGain(song, z, 0f);
        }
        try {
            live.seekAllPlaying(targetMs);
            if (playbackStarted) live.play();
        } catch (Exception ignored) {}
        fadeSongGainsIn(song, targets);
        scrubBlendBusy = false;
    }

    /** Equal-power staggered blend then promote ghost → live slot. 2026-07-21 */
    private void runSoftScrubBlend(final int songIndex, final StemMixer oldM,
            final StemMixer ghost, final float[] targets) {
        final int steps = StemControls.transitionFadeSteps(transitionMs);
        final boolean eq = StemBlendGains.useEqualPowerForPreset(transitionPreset);
        final int[] step = new int[] { 0 };
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                step[0]++;
                float t = steps < 1 ? 1f : (step[0] / (float) steps);
                for (int z = 0; z < StemMixer.STEM_COUNT && z < targets.length; z++) {
                    float outG;
                    float inG;
                    if (eq) {
                        outG = StemBlendGains.staggeredOutGain(targets[z], t, z, true);
                        inG = StemBlendGains.staggeredInGain(targets[z], t, z, true);
                    } else {
                        outG = StemControls.fadeGainStep(targets[z], 0f, step[0], steps);
                        inG = StemControls.fadeGainStep(0f, targets[z], step[0], steps);
                    }
                    try {
                        oldM.setGain(z, outG);
                        ghost.setGain(z, inG);
                    } catch (Exception ignored) {}
                    StemSession.SongState st = session.song(songIndex);
                    if (st != null) st.gains[z] = inG;
                }
                refreshFace();
                if (step[0] >= steps) {
                    // Promote ghost into the live mixer slot; release old. 2026-07-21
                    if (mixers != null && songIndex >= 0 && songIndex < mixers.length) {
                        releaseMixerAsync(oldM);
                        mixers[songIndex] = ghost;
                        scrubGhostMixer = null;
                        ghost.setListener(new StemMixer.Listener() {
                            @Override public void onReady() {}
                            @Override public void onError(String message) {}
                            @Override
                            public void onComplete() {
                                onSongPlaybackComplete(songIndex);
                            }
                        });
                    } else {
                        releaseScrubGhost();
                    }
                    scrubBlendBusy = false;
                    scrubSong = -1;
                    updateStatusLine();
                    return;
                }
                main.postDelayed(this, StemControls.TRANSITION_TICK_MS);
            }
        };
        main.post(tick);
    }

    private void releaseScrubGhost() {
        if (scrubGhostMixer != null) {
            releaseMixerAsync(scrubGhostMixer);
            scrubGhostMixer = null;
        }
    }

    /** @deprecated name kept — session context replaced TRANSITION-only menu. 2026-07-21 */
    private void openTransitionMenu() {
        openSessionContextMenu();
    }

    /** @deprecated use {@link #applyContextSelection}. 2026-07-21 */
    private void applyTransitionSelection(int row) {
        contextMode = CTX_SESSION;
        applyContextSelection(row);
    }

    /** Active mashup crossfade duration (tests / status). 2026-07-20 */
    public long transitionMs() {
        return transitionMs;
    }

    public StemSession session() {
        return session;
    }

    /**
     * Soft-scrub for Track N from jam Options / Hold OK.
     * Layman: scrub that song without pausing the other pads.
     * Confirm seeks all layers of that song’s StemMixer together.
     * Was: overlay only. Reversal: openSoftScrubOverlay.
     * 2026-07-21
     */
    public void openJamScrub(int songIndex) {
        armFaceScrub(songIndex);
    }

    /**
     * Apply TRANSITION preset from jam session Options.
     * Layman: pick how long pad swaps blend. Technical: sets transitionPreset + transitionMs.
     * 2026-07-21
     */
    public void applyJamTransitionPreset(int preset) {
        if (preset < 0) return;
        transitionPreset = preset;
        transitionMs = StemControls.transitionMsForPreset(preset);
    }

    /**
     * Wheel — volume or loop bars; hold-chop resizes slice; hold-Center peeks screw.
     * CW = louder / fewer bars / more screw; CCW = quieter / more bars / less screw.
     * Was: chop hold always resized slice. Reversal: drop centerDown screw branch.
     * 2026-07-20
     */
    public boolean onWheel(int steps) {
        // Hold-OK face scrub: wheel pans seek cursor (preview only; commit seeks whole song). 2026-07-21
        if (faceScrubArmed && steps != 0) {
            scrubCursorMs = StemMixSoftScrub.clampSeekMs(
                    scrubCursorMs + StemMixSoftScrub.wheelDeltaMs(scrubDurationMs, steps),
                    scrubDurationMs);
            paintFaceScrubCursor();
            notePadInteraction();
            if (statusView != null) {
                statusView.setText(StemMixSoftScrub.statusLine(scrubCursorMs, scrubDurationMs));
            }
            return true;
        }
        // Context overlay: wheel moves menu focus or scrub thumb. 2026-07-20 / 2026-07-21
        if (transitionPanel != null && steps != 0) {
            if (contextMode == CTX_SCRUB) {
                scrubCursorMs = StemMixSoftScrub.clampSeekMs(
                        scrubCursorMs + StemMixSoftScrub.wheelDeltaMs(scrubDurationMs, steps),
                        scrubDurationMs);
                if (scrubStatusView != null) {
                    scrubStatusView.setText(StemMixSoftScrub.statusLine(scrubCursorMs, scrubDurationMs));
                }
                if (scrubBar != null) {
                    scrubBar.setProgress(Math.round(
                            StemMixSoftScrub.thumbFrac(scrubCursorMs, scrubDurationMs) * 1000f));
                }
                return true;
            }
            int n = contextRowCount > 0 ? contextRowCount : StemMixContextRows.SESSION_ROW_COUNT;
            transitionFocus = (transitionFocus + (steps > 0 ? 1 : -1) + n) % n;
            paintTransitionFocus();
            return true;
        }
        StemMixer mixer = activeMixer();
        if (!ready || mixer == null || steps == 0) {
            return true;
        }
        if (stemStutterArmed && mixer.isStuttering()) {
            StemSession.SongState cst = session.activeSongState();
            // Hold-Center peek: wheel = screw ladder; release Center → roll size again. 2026-07-20
            if (centerDown) {
                // Ladder from persisted screw, or classic hold default while SongState still cold. 2026-07-20
                float cur = (cst != null && cst.screwRate > 0.1f && cst.screwRate < 0.99f)
                        ? cst.screwRate : StemControls.DEFAULT_HOLD_SCREW_RATE;
                float screw = StemBpm.nudgeScrewRate(
                        cur, StemControls.loopStepsFromWheel(steps));
                if (cst != null) cst.screwRate = screw;
                float tempo = (cst != null && cst.tempoRate > 0.1f) ? cst.tempoRate : 1f;
                mixer.setHoldScrewRate(StemTempoSync.composePadRate(tempo, screw));
                refreshFace();
                updateStatusLine();
                return true;
            }
            stutterChopStep = StemBpm.nudgeChopStep(
                    stutterChopStep, StemControls.loopStepsFromWheel(steps));
            if (stutterChopStep < 1) stutterChopStep = 1;
            int slice = StemBpm.chopSliceMs(mixer.getBpm(), stutterChopStep);
            if (slice <= 0) slice = StemMixer.DEFAULT_STUTTER_MS;
            mixer.setStutterSliceMs(slice);
            if (cst != null) cst.chopStep = stutterChopStep;
            refreshFace();
            updateStatusLine();
            return true;
        }
        boolean useVolume = StemControls.wheelUsesVolume(wheelLoopMode);
        if (activeZone < 0 || activeZone >= StemMixer.STEM_COUNT) {
            // Idle / no focus — wheel wakes size only; no gain until a pad is lit. 2026-07-21
            notePadInteraction();
            return true;
        }
        if (!useVolume) {
            float bars = StemControls.nudgeLoopBars(
                    editLoopBars, StemControls.loopStepsFromWheel(steps));
            editLoopBars = bars;
            StemSession.SongState lst = session.activeSongState();
            if (StemControls.isLoopBarsNone(bars)) {
                if (mixer.isLooping()) {
                    savedLoopBars = mixer.getLoopBars() > 0f
                            ? mixer.getLoopBars() : StemControls.DEFAULT_LOOP_BARS;
                    mixer.clearLoop();
                }
                if (lst != null) {
                    lst.looping = false;
                    for (int i = 0; i < lst.zoneLoopCtrl.length; i++) lst.zoneLoopCtrl[i] = false;
                }
                clearZoneLoopCtrl();
            } else {
                savedLoopBars = bars;
                markZoneLoopCtrl(activeZone, true);
                if (lst != null && activeZone >= 0 && activeZone < lst.zoneLoopCtrl.length) {
                    lst.zoneLoopCtrl[activeZone] = true;
                    lst.looping = true;
                    lst.loopBars = bars;
                }
                if (!mixer.isLooping()) {
                    mixer.startLoop(bars);
                } else {
                    mixer.setLoopBars(bars);
                }
                syncLoopCtrlToMixer();
            }
            refreshFace();
            updateStatusLine();
            notePadInteraction();
            return true;
        }
        mixer.nudgeGainSteps(activeZone, StemControls.volumeStepsFromWheel(steps));
        // Pad-owned level — wheel moves the dial, not a per-song copy. 2026-07-21
        float g = mixer.getGain(activeZone);
        session.setPadGain(activeZone, g);
        StemSession.SongState st = session.activeSongState();
        if (st != null) st.gains[activeZone] = g;
        // Mute the other song’s zone so solo invariant holds while turning the dial. 2026-07-21
        if (session.isMulti()) {
            int other = StemControls.otherSongIndex(session.songIndexForZone(activeZone),
                    session.songCount());
            applySongZoneGain(other, activeZone, 0f);
        }
        notePadInteraction();
        refreshFace();
        updateStatusLine();
        return true;
    }

    /** Leave stem player — cancel job, pause every song mixer. */
    public void requestExit() {
        cancelled.set(true);
        jobGen.incrementAndGet();
        stopStemStutter();
        main.removeCallbacks(mashDriftRunnable);
        if (mixers != null) {
            for (int i = 0; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                if (m == null) continue;
                try { m.pause(); } catch (Exception ignored) {}
            }
        }
        host.onExitStemPlayer();
    }

    /**
     * Center/Enter — start jam (Play), scrub confirm already handled, else shuffle / dial.
     * Was: always shuffle on mashup OK. Reversal: drop playbackStarted gate.
     * 2026-07-19 / 2026-07-20 / 2026-07-21
     */
    private void onCenterTap() {
        if (songFinished) {
            songFinished = false;
            startUserPlayback();
            return;
        }
        // First OK starts timelines; later OK shuffles (mashup) or loop dial (single). 2026-07-21
        if (!playbackStarted) {
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("padsIdle", padsIdle);
                d.put("activeZone", activeZone);
                com.solar.launcher.Debug897e32Log.log(host.appContext(),
                        "StemPlayerHost.onCenterTap", "startUserPlayback (first OK)", "A", d);
            } catch (Exception ignored) {}
            // #endregion
            startUserPlayback();
            return;
        }
        // Mashup OK = StemFM centre shuffle (rotate pad songs + focus). 2026-07-20
        // Idle / no pad lit: OK only restores size — second intent after a pad focus. 2026-07-21
        if (session.isMulti()) {
            boolean wakeOnly = StemControls.centerTapWhilePadIdleIsWakeOnly(padsIdle, activeZone);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("padsIdle", padsIdle);
                d.put("activeZone", activeZone);
                d.put("wakeOnly", wakeOnly);
                d.put("playbackStarted", playbackStarted);
                com.solar.launcher.Debug897e32Log.log(host.appContext(),
                        "StemPlayerHost.onCenterTap",
                        wakeOnly ? "wakeOnly NO shuffle" : "shuffleMashupPads",
                        "A", d);
            } catch (Exception ignored) {}
            // #endregion
            if (wakeOnly) {
                notePadInteraction();
                return;
            }
            long t0 = android.os.SystemClock.uptimeMillis();
            shuffleMashupPads();
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("shuffleMs", android.os.SystemClock.uptimeMillis() - t0);
                org.json.JSONArray zones = new org.json.JSONArray();
                for (int z = 0; z < 4; z++) zones.put(session.songIndexForZone(z));
                d.put("zoneSongs", zones);
                com.solar.launcher.Debug897e32Log.log(host.appContext(),
                        "StemPlayerHost.onCenterTap", "shuffle done", "E", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        // Single-track: Center no longer toggles loop (mute hygiene). 2026-07-21
        if (!StemControls.centerEntersLoopEdit()) {
            if (StemControls.centerShouldLeaveLoopEdit(wheelLoopMode)) {
                leaveLoopEditToVolume();
            }
            return;
        }
        if (activeZone < 0 || activeZone >= StemMixer.STEM_COUNT) {
            return;
        }
        if (StemControls.centerShouldLeaveLoopEdit(wheelLoopMode)) {
            leaveLoopEditToVolume();
            return;
        }
        enterLoopEdit();
    }

    /**
     * Start (or resume) every song mixer after user OK / Pause→Play.
     * Layman: press the centre Play to hear the jam; levels and scrub stay put.
     * Was: auto-play in onAllMixersReady. Reversal: call play() there again.
     * 2026-07-21
     */
    public void startUserPlayback() {
        if (!ready || mixers == null) return;
        playbackStarted = true;
        for (int i = 0; i < mixers.length; i++) {
            StemMixer m = mixers[i];
            if (m == null) continue;
            try {
                m.play();
            } catch (Exception ignored) {}
        }
        songFinished = false;
        if (mashupFace != null) mashupFace.setCentreTransportPlaying(true);
        refreshFace();
        updateStatusLine();
    }

    /**
     * Pause all jam mixers (session Options → Pause). Sibling seats pause together.
     * Layman: interrupt the mix; centre shows Play again until you resume.
     * Audio: pause only — no release. Reversal: no-op.
     * 2026-07-21
     */
    public void pauseJamPlayback() {
        if (mixers == null) return;
        playbackStarted = false;
        for (int i = 0; i < mixers.length; i++) {
            StemMixer m = mixers[i];
            if (m == null) continue;
            try {
                m.pause();
            } catch (Exception ignored) {}
        }
        if (mashupFace != null) mashupFace.setCentreTransportPlaying(false);
        refreshFace();
        updateStatusLine();
    }

    public boolean isSongPlaying(int index) {
        if (!sessionActive || mixers == null || index < 0 || index >= mixers.length) return false;
        StemMixer m = mixers[index];
        return m != null && m.isPlaying();
    }

    public void toggleSongPlayPause(int index) {
        if (!sessionActive || mixers == null || index < 0 || index >= mixers.length) return;
        StemMixer m = mixers[index];
        if (m == null) return;
        try {
            if (m.isPlaying()) {
                m.pause();
            } else {
                m.play();
                playbackStarted = true;
                if (mashupFace != null) mashupFace.setCentreTransportPlaying(true);
            }
            refreshFace();
            updateStatusLine();
        } catch (Exception ignored) {}
    }

    /** True after user started (or resumed) the jam. 2026-07-21 */
    public boolean isPlaybackStarted() {
        return playbackStarted;
    }

    /**
     * Randomise pad→song at pad-owned levels; pulse shuffle disc.
     * Layman: OK remixes which song feeds each pad — dials don’t jump.
     * Was: rotate + fadeZoneToward volume animation. Reversal: that soft mix block.
     * 2026-07-20 / 2026-07-21
     */
    private void shuffleMashupPads() {
        if (!ready || !session.isMulti() || !playbackStarted) return;
        shuffleMashupPads(false);
    }

    /**
     * @param initial cold-start may invent B pads (≥1 each); user OK shuffle will not invent B.
     * 2026-07-21
     */
    private void shuffleMashupPads(boolean initial) {
        if (!ready || !session.isMulti()) return;
        int[] before = new int[StemMixer.STEM_COUNT];
        session.copyZoneSongs(before);
        float[] levels = new float[StemMixer.STEM_COUNT];
        session.copyPadGains(levels);
        session.shufflePadAssignments(new java.util.Random(), initial);
        int[] after = new int[StemMixer.STEM_COUNT];
        session.copyZoneSongs(after);
        cancelZoneCrossfade();
        // Instant re-home at same pad levels — no volume flicker. 2026-07-21
        for (int z = 0; z < StemMixer.STEM_COUNT; z++) {
            float level = levels[z];
            if (before[z] != after[z]) {
                applySongZoneGain(before[z], z, 0f);
                applySongZoneGain(after[z], z, level);
            } else {
                applySongZoneGain(after[z], z, level);
            }
        }
        if (mashupFace != null) mashupFace.pulseShuffle();
        if (!initial) notePadInteraction();
        refreshFace();
        updateInteractedTrackTitle();
        updateStatusLine();
        if (!initial) {
            try {
                host.toast("Shuffle");
            } catch (Exception ignored) {}
        }
    }

    /**
     * Smoothly nudge one pad’s current song gain toward target (short fade).
     * Layman: ease that stem louder or quieter without a hard jump.
     * 2026-07-20
     */
    private void fadeZoneToward(final int zone, final float targetGain) {
        if (zone < 0 || zone >= StemMixer.STEM_COUNT) return;
        final int song = session.songIndexForZone(zone);
        StemSession.SongState st = session.song(song);
        final float from = st != null ? st.gains[zone] : 0f;
        final float to = StemControls.clampGain(targetGain);
        if (Math.abs(from - to) < 0.01f) {
            applySongZoneGain(song, zone, to);
            return;
        }
        final int steps = Math.max(4, StemControls.transitionFadeSteps(Math.min(transitionMs, 600L)) / 2);
        final int[] step = new int[] { 0 };
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (!sessionActive || !ready) return;
                step[0]++;
                float g = StemControls.fadeGainStep(from, to, step[0], steps);
                applySongZoneGain(song, zone, g);
                refreshFace();
                if (step[0] < steps) {
                    main.postDelayed(this, 16L);
                }
            }
        };
        main.post(r);
    }

    /**
     * Enter loop-edit wheel mode — mark this stem; do not start A–B until wheel leaves none.
     * Was: dialInToLoopMode called startLoop(DEFAULT). Reversal: that auto-arm.
     * 2026-07-19
     */
    private void enterLoopEdit() {
        StemMixer mixer = activeMixer();
        if (mixer == null || !ready) return;
        markZoneLoopCtrl(activeZone, true);
        StemSession.SongState st = session.activeSongState();
        if (st != null && activeZone >= 0 && activeZone < st.zoneLoopCtrl.length) {
            st.zoneLoopCtrl[activeZone] = true;
        }
        wheelLoopMode = true;
        if (mixer.isLooping() && mixer.getLoopBars() > 0f) {
            editLoopBars = mixer.getLoopBars();
            savedLoopBars = editLoopBars;
        } else if (st != null && st.looping && st.loopBars > 0f) {
            editLoopBars = st.loopBars;
            savedLoopBars = editLoopBars;
        } else {
            editLoopBars = StemControls.LOOP_BARS_NONE;
        }
        syncLoopCtrlToMixer();
        refreshFace();
        updateStatusLine();
    }

    /**
     * Leave loop-edit → volume wheel; A–B keeps running if armed.
     * Was: stopLoopToVolumeMode cleared A–B. Reversal: that clear-on-Center.
     * 2026-07-19
     */
    private void leaveLoopEditToVolume() {
        wheelLoopMode = false;
        refreshFace();
        updateStatusLine();
    }

    /**
     * Clear A–B and loop-ctrl (wheel to none in loop-edit).
     * 2026-07-19
     */
    private void stopLoopToVolumeMode() {
        wheelLoopMode = false;
        editLoopBars = StemControls.LOOP_BARS_NONE;
        clearZoneLoopCtrl();
        StemSession.SongState st = session.activeSongState();
        if (st != null) {
            for (int i = 0; i < st.zoneLoopCtrl.length; i++) st.zoneLoopCtrl[i] = false;
            st.looping = false;
        }
        StemMixer mixer = activeMixer();
        if (mixer != null) {
            if (mixer.isLooping()) {
                savedLoopBars = mixer.getLoopBars();
            }
            mixer.clearLoop();
        }
        refreshFace();
        updateStatusLine();
    }

    /**
     * Focus a stem — always volume mode (loop audio may continue).
     * Was: loop-ctrl stems kept loop wheel. Reversal: wheelLoopModeForStem true path.
     * 2026-07-19
     */
    private void selectZone(int zone) {
        if (zone < 0 || zone >= StemMixer.STEM_COUNT) return;
        activeZone = zone;
        session.setActiveZone(zone);
        armed = true;
        wheelLoopMode = StemControls.wheelLoopModeForStem(false, zoneLoopCtrl[zone]);
        if (face != null) face.setActiveZone(zone);
        if (mashupFace != null) mashupFace.setActiveZone(zone);
        refreshFace();
        updateStatusLine();
    }

    /** Clear per-stem loop-control membership. 2026-07-19 */
    private void clearZoneLoopCtrl() {
        for (int i = 0; i < zoneLoopCtrl.length; i++) zoneLoopCtrl[i] = false;
    }

    /** Mark one stem as (not) in the loop-control set. 2026-07-19 */
    private void markZoneLoopCtrl(int zone, boolean on) {
        if (zone < 0 || zone >= zoneLoopCtrl.length) return;
        zoneLoopCtrl[zone] = on;
    }

    /**
     * Prepare live pad tracks (hybrid gate) then open mixers; overflow preps in background.
     * Layman: cook the first two songs, start the jam, keep cooking the rest quietly.
     * Was: resolve every bound track before beginMixers. Reversal: loop all jobTracks then begin.
     * 2026-07-19 / 2026-07-21
     */
    private void startJob() {
        final int gen = jobGen.incrementAndGet();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("gen", gen);
            d.put("trackCount", tracks.size());
            d.put("multi", tracks.size() > 1);
            for (int ti = 0; ti < tracks.size() && ti < 3; ti++) {
                File tf = tracks.get(ti);
                d.put("t" + ti, tf != null ? tf.getName() : "null");
            }
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.startJob", "startJob enter", "B", d);
        } catch (Exception ignored) {}
        // #endregion
        if (tracks.isEmpty()) {
            statusView.setText("No local track file");
            loading = false;
            refreshFace();
            return;
        }
        final boolean premix = LalalAccount.isPremixExperimental(host.prefs());
        final String key = LalalAccount.effectiveKey(host.prefs());
        final android.content.Context ctx = host.appContext();
        final java.util.ArrayList<File> jobTracks = new java.util.ArrayList<File>(tracks);
        final int gate = StemMixQueuePolicy.hybridPrepGateCount(jobTracks.size());

        // Count who still needs Lalal among the live gate. 2026-07-19 / 2026-07-21
        int needLal = 0;
        for (int i = 0; i < jobTracks.size() && i < gate; i++) {
            File tf = jobTracks.get(i);
            boolean ready = LalalClient.trackStemsReady(ctx, tf, premix, host.appCacheDir());
            if (!ready) needLal++;
        }
        final boolean allLocal = needLal == 0;
        if (allLocal) {
            statusView.setText(jobTracks.size() > 1 ? "Loading mashup stems…" : "Loading stems…");
        } else if (key.length() < 8) {
            statusView.setText("Lalal key missing: add stems folders or key");
            loading = false;
            refreshFace();
            return;
        } else {
            statusView.setText(jobTracks.size() > 1
                    ? "Preparing first songs…"
                    : (LalalAccount.isUserConfigured(host.prefs())
                            ? "Uploading to Lalal.ai…"
                            : "Demo key · Uploading to Lalal.ai…"));
        }

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (com.solar.launcher.SolarAutoTime.isWallClockImplausible()) {
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != jobGen.get()) return;
                                statusView.setText("Fixing clock (needed for secure download)…");
                            }
                        });
                        com.solar.launcher.SolarAutoTime.requestSyncNow(ctx);
                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {}
                        if (com.solar.launcher.SolarAutoTime.isWallClockImplausible()) {
                            throw new IOException(
                                    "Clock is wrong (shows 1970). Settings → Date & Time → Sync now, then retry.");
                        }
                    }

                    // Hybrid gate: prepare live pair fully before mixers. 2026-07-21
                    for (int i = 0; i < jobTracks.size() && i < gate; i++) {
                        if (gen != jobGen.get() || cancelled.get()) return;
                        final int songIndex = i;
                        final File src = jobTracks.get(i);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != jobGen.get()) return;
                                statusView.setText("Song " + (songIndex + 1) + "/"
                                        + gate + "…");
                            }
                        });
                        List<LalalClient.StemFile> stems = resolveStemsForTrack(
                                src, premix, key, ctx, gen);
                        if (stems == null || stems.isEmpty()) {
                            throw new IOException("No stems for " + src.getName());
                        }
                        StemSession.SongState st = session.song(songIndex);
                        if (st != null) {
                            st.stems = stems;
                            st.bassBody = null;
                            File stemDir = null;
                            for (int si = 0; si < stems.size(); si++) {
                                LalalClient.StemFile sf = stems.get(si);
                                if (sf != null && sf.file != null && sf.file.getParentFile() != null) {
                                    stemDir = sf.file.getParentFile();
                                    break;
                                }
                            }
                            if (stemDir != null) {
                                st.bassBody = StemBassBody.existingOrNull(stemDir);
                            }
                        }
                    }
                    if (gen != jobGen.get() || cancelled.get()) return;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get() || cancelled.get()) return;
                            beginMixers();
                            // Overflow queue prep — IO only; never touches live mixers. 2026-07-21
                            startBackgroundOverflowPrep(gen, premix, key, ctx);
                        }
                    });
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != jobGen.get()) return;
                            loading = false;
                            refreshFace();
                            if (cancelled.get()) {
                                statusView.setText("Cancelled");
                                return;
                            }
                            String msg = e.getMessage() != null ? e.getMessage() : "Stem failed";
                            if (msg.indexOf("Could not validate certificate") >= 0
                                    || msg.indexOf("CertificateNotYetValid") >= 0
                                    || msg.indexOf("not valid until") >= 0) {
                                msg = "Clock is wrong: Settings → Date & Time → Sync now, then retry Stem Player.";
                            }
                            statusView.setText(msg);
                            host.toast(msg);
                        }
                    });
                }
            }
        });
    }

    /**
     * After live jam starts, cook stems for Next-up+ queue rows (hybrid).
     * Layman: keep preparing later songs without pausing what’s playing.
     * Was: no overflow prep until softReplace. Reversal: empty method.
     * 2026-07-21
     */
    private void startBackgroundOverflowPrep(final int gen, final boolean premix,
            final String key, final android.content.Context ctx) {
        final java.util.ArrayList<File> overflow = new java.util.ArrayList<File>();
        try {
            PlayQueue q = host.stemMixPlayQueue();
            if (q == null) return;
            if (!StemMixQueuePolicy.shouldBackgroundPrepOverflow(
                    q.size(), StemMixQueuePolicy.STEM_LIVE_WINDOW)) {
                return;
            }
            java.util.List<PlayQueue.QueueItem> items = q.items();
            for (int i = StemMixQueuePolicy.STEM_LIVE_WINDOW; i < items.size(); i++) {
                PlayQueue.QueueItem it = items.get(i);
                if (it != null && it.file != null && it.file.isFile()) {
                    overflow.add(it.file);
                }
            }
        } catch (Exception ignored) {
            return;
        }
        if (overflow.isEmpty()) return;
        io.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < overflow.size(); i++) {
                    if (gen != jobGen.get() || cancelled.get()) return;
                    File src = overflow.get(i);
                    try {
                        // Cache hit → resolve returns quickly; miss → Lalal IO only. 2026-07-21
                        if (LalalClient.trackStemsReady(ctx, src, premix, host.appCacheDir())) {
                            continue;
                        }
                        resolveStemsForTrack(src, premix, key, ctx, gen);
                    } catch (Exception ignored) {
                        // Fail-open — Next-up softReplace will retry. 2026-07-21
                    }
                }
            }
        });
    }

    /**
     * Resolve stem MP3s for one track (user / any cache home → Lalal only if missing).
     * Prefer requested premix mode; fall back to the other mode’s ready folder.
     * Was: only durableStemDir+legacy for one premix flag (missed overflow / other mode).
     * 2026-07-19
     */
    private List<LalalClient.StemFile> resolveStemsForTrack(File src, boolean premix,
            String key, android.content.Context ctx, int gen) throws Exception {
        File readyDir = LalalClient.findReadyStemDir(ctx, src, premix, host.appCacheDir());
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("name", src != null ? src.getName() : "");
            d.put("premix", premix);
            d.put("cacheHit", readyDir != null);
            d.put("readyDir", readyDir != null ? readyDir.getAbsolutePath() : "");
            d.put("stableKey", src != null ? LalalClient.cacheKeyStable(src) : "");
            d.put("pathKey", src != null ? LalalClient.cacheKeyFor(src) : "");
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.resolveStemsForTrack",
                    readyDir != null ? "cache-hit load local" : "cache-miss will separateToMp3",
                    "LOCAL",
                    d);
        } catch (Exception ignored) {}
        // #endregion
        List<LalalClient.StemFile> cached = null;
        if (readyDir != null) {
            if (LalalClient.userStemsReady(src)
                    && readyDir.equals(LalalClient.userStemsDir(src))) {
                cached = LalalClient.loadUserStems(src, premix);
            } else {
                cached = LalalClient.loadCached(readyDir, premix);
                if (cached == null || cached.isEmpty()) {
                    cached = LalalClient.loadStemDirFlexible(readyDir);
                }
            }
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("srcName", src != null ? src.getName() : "");
                d.put("srcPath", src != null ? src.getAbsolutePath() : "");
                d.put("readyDir", readyDir.getAbsolutePath());
                d.put("stem0", cached != null && !cached.isEmpty() && cached.get(0).file != null
                        ? cached.get(0).file.getAbsolutePath() : "");
                d.put("count", cached != null ? cached.size() : 0);
                d.put("markerOk", LalalClient.markerMatchesTrack(readyDir, src));
                com.solar.launcher.Debug8b0481Log.log(
                        "StemPlayerHost.resolveStemsForTrack", "loaded cache", "H-A,H-B,H-C,H-D", d);
            } catch (Exception ignored) {}
            // #endregion
            if (cached != null && !cached.isEmpty()) {
                // Re-entered before flush finished — keep a pending publish for work leaves. 2026-07-21
                if (isLalalWorkDir(ctx, readyDir)) {
                    rememberPendingPublish(src, readyDir, premix);
                }
                return cached;
            }
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("name", src != null ? src.getName() : "");
            d.put("callingSeparate", true);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.resolveStemsForTrack", "separateToMp3 begin", "LOCAL", d);
        } catch (Exception ignored) {}
        // #endregion
        if (key == null || key.length() < 8) {
            throw new IOException("Lalal key missing for " + src.getName());
        }
        File durable = LalalClient.durableStemDir(ctx, src, premix);
        File work = LalalClient.workStemDir(ctx, src, premix);
        LalalClient client = new LalalClient(key);
        client.setCancelled(cancelled);
        // Visible prep for whole separate — sibling mixers keep playing (IO-only). 2026-07-21
        publishStemPrepProgress(src, "start", 0, null, gen);
        List<LalalClient.StemFile> pads = client.separateToMp3(src, work, durable, premix,
                new LalalClient.Progress() {
            @Override
            public void onProgress(final String phase, final int percent, final String detail) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        publishStemPrepProgress(src, phase, percent, detail, gen);
                    }
                });
            }
        });
        // Play from work; durable copy waits for detach flush. 2026-07-21
        rememberPendingPublish(src, work, premix);
        main.post(new Runnable() {
            @Override
            public void run() {
                publishStemPrepProgress(src, "ready", 100, null, gen);
            }
        });
        return pads;
    }

    /**
     * Push stem prep progress to status, queue marquee, dial, and throttled toasts.
     * Layman: keep saying what stage the cook is on until it’s done.
     * Technical: QueuePrepStatusRegistry + statusView; never pauses sibling mixers.
     * Was: statusView only while !ready (invisible mid-jam). Reversal: that gate.
     * 2026-07-21
     */
    private void publishStemPrepProgress(File src, String phase, int percent, String detail,
            int gen) {
        if (gen != jobGen.get() || cancelled.get()) return;
        String line = StemPrepProgressUi.statusLine(phase, percent, detail);
        String key = StemPrepProgressUi.registryKeyForPhase(phase);
        QueuePrepStatusRegistry.set(src, key);
        if (statusView != null) {
            // Show during session start AND mid-jam replace (status was GONE after ready). 2026-07-21
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(line);
            statusView.setTextColor(0xFFB0B0B8);
        }
        if (statusBusyHost != null) {
            statusBusyHost.setVisibility(View.VISIBLE);
        }
        if (!ready) {
            loading = !"ready".equals(phase) && !"fail".equals(phase);
        }
        feedComplicationDials();
        refreshFace();
        // Do not persist queue on every % tick — registry alone updates when queue opens. 2026-07-21
        String name = src != null ? src.getName() : "";
        if (StemPrepProgressUi.shouldToastMilestone(prepToastPhase, prepToastPercent,
                phase, percent)) {
            try {
                host.toast(StemPrepProgressUi.toastLine(name, phase, percent));
            } catch (Exception ignored) {}
            prepToastPhase = phase;
            prepToastPercent = percent;
            prepToastFile = src;
            // Phase/milestone only — refresh open queue tiers without full persist. 2026-07-21
            try {
                host.onStemMixQueueMutated();
            } catch (Exception ignored) {}
        }
        if ("ready".equals(phase) || "fail".equals(phase)) {
            QueuePrepStatusRegistry.set(src, QueuePrepStatus.KEY_IDLE);
            prepToastPhase = null;
            prepToastPercent = -1;
            if (ready && statusView != null && replacingSongIndex < 0) {
                // Hide tip walls again after cold-start cook finishes. 2026-07-21
                statusView.setVisibility(View.GONE);
                if (statusBusyHost != null) statusBusyHost.setVisibility(View.GONE);
            }
        }
    }

    /**
     * @deprecated Prefer findReadyStemDir + loadCached. Kept unused — loadCachedIfReady removed.
     * 2026-07-19
     */
    @SuppressWarnings("unused")
    private static List<LalalClient.StemFile> loadCachedIfReady(File dir, boolean premix) {
        if (dir == null) return null;
        if (!LalalClient.cacheReady(dir) && !LalalClient.cacheReadyFlexible(dir)) return null;
        List<LalalClient.StemFile> loaded = LalalClient.loadCached(dir, premix);
        if (loaded != null && !loaded.isEmpty()) return loaded;
        loaded = LalalClient.loadStemDirFlexible(dir);
        return (loaded != null && !loaded.isEmpty()) ? loaded : null;
    }

    /**
     * Open one StemMixer per song — all timelines start together at gain 0.
     * Cycle/focus only steers which song the wheel writes; never replaceZoneStem.
     * Song 1 = MediaPlayer @ 1.0; songs 2–3 = IJK SoundTouch when BPM rate ≠ 1.
     * Was: rate forced 1.0 / bassBody null. Reversal: m.load(st.stems, null) only.
     * 2026-07-20
     */
    private void beginMixers() {
        releaseMixers();
        final int n = session.songCount();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("songCount", n);
            d.put("rootNull", root == null);
            StemSession.SongState s0 = session.song(0);
            d.put("song0stems", s0 != null && s0.stems != null ? s0.stems.size() : -1);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.beginMixers", "beginMixers enter", "C", d);
        } catch (Exception ignored) {}
        // #endregion
        if (n < 1 || root == null) {
            statusView.setText("No songs ready");
            loading = false;
            refreshFace();
            return;
        }
        for (int i = 0; i < n; i++) {
            StemSession.SongState st = session.song(i);
            if (st == null || st.stems == null || st.stems.isEmpty()) {
                statusView.setText("Missing stems (song " + (i + 1) + ")");
                loading = false;
                refreshFace();
                return;
            }
        }
        // Per-song BPM from duration; Song 1 is tempo master. 2026-07-20
        for (int i = 0; i < n; i++) {
            StemSession.SongState st = session.song(i);
            if (st == null) continue;
            int dur = st.track != null ? probeDurationMs(st.track) : 180_000;
            st.bpm = StemBpm.estimateFromDurationMs(dur);
        }
        StemSession.SongState song0 = session.song(0);
        float masterBpm = song0 != null ? song0.bpm : StemBpm.DEFAULT_BPM;
        for (int i = 0; i < n; i++) {
            StemSession.SongState st = session.song(i);
            if (st == null) continue;
            st.tempoRate = StemTempoSync.rateForSong(masterBpm, st.bpm, i);
            // screwRate stays cold-start 1.0 — chop hold sets classic screw. 2026-07-20
            st.screwRate = 1f;
        }
        mixers = new StemMixer[n];
        final AtomicInteger readyLeft = new AtomicInteger(n);
        final AtomicBoolean loadFailed = new AtomicBoolean(false);
        try {
            for (int i = 0; i < n; i++) {
                final int songIndex = i;
                final StemSession.SongState st = session.song(i);
                final StemMixer m = new StemMixer(root.getContext());
                mixers[i] = m;
                m.setBpm(st.bpm > 30f ? st.bpm : masterBpm);
                m.setListener(new StemMixer.Listener() {
                    @Override
                    public void onReady() {
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("songIndex", songIndex);
                            d.put("bpm", m.getBpm());
                            d.put("tempoRate", st.tempoRate);
                            d.put("left", readyLeft.get());
                            com.solar.launcher.Debug543e15Log.log(
                                    "StemPlayerHost.beginMixers", "mixer onReady", "A", d);
                        } catch (Exception ignored) {}
                        // #endregion
                        if (loadFailed.get()) return;
                        if (readyLeft.decrementAndGet() == 0) {
                            onAllMixersReady();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!loadFailed.compareAndSet(false, true)) return;
                        // #region agent log
                        try {
                            org.json.JSONObject d = new org.json.JSONObject();
                            d.put("songIndex", songIndex);
                            d.put("msg", message != null ? message : "");
                            com.solar.launcher.Debug543e15Log.log(
                                    "StemPlayerHost.beginMixers", "mixer onError", "C", d);
                        } catch (Exception ignored) {}
                        // #endregion
                        loading = false;
                        ready = false;
                        statusView.setText(message != null ? message : "Play error");
                        statusView.setTextColor(0xFFB0B0B8);
                        refreshFace();
                    }

                    @Override
                    public void onComplete() {
                        // Mashup: either song ending → hand off to survivor. Single: finished UI.
                        // Was: only songIndex==0 set songFinished. Reversal: that song0-only gate.
                        // 2026-07-19 / 2026-07-20
                        onSongPlaybackComplete(songIndex);
                    }
                });
                // IJK SoundTouch when slave rate differs; else MediaPlayer + optional bass body. 2026-07-20
                if (StemTempoSync.needsSoundTouch(st.tempoRate)) {
                    m.loadWithSoundTouch(st.stems, st.bassBody, st.tempoRate);
                } else {
                    m.load(st.stems, st.bassBody);
                    m.setTargetRate(st.tempoRate);
                }
            }
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("loadCalled", true);
                d.put("mixerCount", n);
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.beginMixers", "load() issued", "A", d);
            } catch (Exception ignored) {}
            // #endregion
        } catch (Exception e) {
            loadFailed.set(true);
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("err", e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                com.solar.launcher.Debug543e15Log.log(
                        "StemPlayerHost.beginMixers", "load threw", "C", d);
            } catch (Exception ignored) {}
            // #endregion
            loading = false;
            statusView.setText(e.getMessage() != null ? e.getMessage() : "Mixer failed");
            refreshFace();
        }
        statusView.setText(n > 1 ? "Buffering " + n + " songs…" : "Buffering stems…");
    }

    /**
     * A song’s timeline ended — pair soft-restart (either seat), Next-up advance (3+), or self-loop.
     * Layman: short jam loops whichever track finished; longer queue pulls the next waiting song in.
     * Both pair members soft-restart independently when *they* end; partner keeps playing.
     * Was: advance miss → survivor mute (killed short song). Reversal: survivor block below.
     * 2026-07-20 / 2026-07-21
     */
    private void onSongPlaybackComplete(int finishedSong) {
        if (!sessionActive) return;
        if (mashupHandoffBusy) {
            // Remember both seats if they end during a fade (bitmask). 2026-07-21
            if (finishedSong >= 0 && finishedSong < 8) {
                pendingCompleteMask |= (1 << finishedSong);
            }
            // #region agent log
            try {
                org.json.JSONObject d = new org.json.JSONObject();
                d.put("finishedSong", finishedSong);
                d.put("pendingMask", pendingCompleteMask);
                com.solar.launcher.Debug897e32Log.log(host.appContext(),
                        "StemPlayerHost.onSongPlaybackComplete", "deferred (handoff busy)",
                        "D", d);
            } catch (Exception ignored) {}
            // #endregion
            return;
        }
        // Single-track: soft-restart (seek+fade) — no Center “play again” stall. 2026-07-21
        if (!session.isMulti() || mixers == null || mixers.length < 2) {
            softRestartSong(finishedSong >= 0 ? finishedSong : 0);
            return;
        }
        int qSize = stemQueueSize();
        boolean pairRestart = StemMixQueuePolicy.pairSoftRestartsEitherSeat(qSize, finishedSong);
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("finishedSong", finishedSong);
            d.put("qSize", qSize);
            d.put("pairRestart", pairRestart);
            d.put("transitionMs", transitionMs);
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.onSongPlaybackComplete", "complete decision enter", "C", d);
        } catch (Exception ignored) {}
        // #endregion
        // Closed pair: either finished seat soft-restarts; sibling mixer untouched. 2026-07-21
        if (pairRestart) {
            softRestartSong(finishedSong);
            return;
        }
        // Overflow queue: soft-replace from Next-up. 2026-07-21
        File nextFile = null;
        try {
            nextFile = host.applyStemPairAdvance(finishedSong);
        } catch (Exception ignored) {}
        boolean gotNext = nextFile != null && nextFile.isFile();
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("finishedSong", finishedSong);
            d.put("qSize", qSize);
            d.put("gotNext", gotNext);
            d.put("next", nextFile != null ? nextFile.getName() : "null");
            com.solar.launcher.Debug897e32Log.log(host.appContext(),
                    "StemPlayerHost.onSongPlaybackComplete",
                    gotNext ? "softReplace next-up" : "no next → self restart",
                    "C", d);
        } catch (Exception ignored) {}
        // #endregion
        if (gotNext) {
            mashupHandoffBusy = true;
            if (statusView != null) {
                statusView.setText("Next up · "
                        + StemControls.stripTrackDisplayName(nextFile.getName()));
                statusView.setTextColor(0xFFB0B0B8);
            }
            softReplaceSong(finishedSong, nextFile);
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mashupHandoffBusy = false;
                    flushPendingSongComplete();
                    if (ready && statusView != null) updateStatusLine();
                }
            }, Math.max(400L, transitionMs));
            return;
        }
        // No next → soft-restart finished seat into itself (never hard silence). 2026-07-21
        if (StemMixQueuePolicy.softRestartFinishedSeat(qSize, false)) {
            softRestartSong(finishedSong);
        }
    }

    /**
     * Run deferred song-ends one seat at a time after the prior fade clears.
     * Layman: catch up the other pad if it finished while we were looping this one.
     * 2026-07-21
     */
    private void flushPendingSongComplete() {
        if (mashupHandoffBusy || !sessionActive) return;
        for (int i = 0; i < StemSession.MAX_SONGS; i++) {
            int bit = 1 << i;
            if ((pendingCompleteMask & bit) != 0) {
                pendingCompleteMask &= ~bit;
                onSongPlaybackComplete(i);
                return;
            }
        }
    }

    /**
     * Unified play-queue size for StemFM handoff (fail-open as live window = pair-repeat).
     * Layman: how many songs are in the jam list right now.
     * 2026-07-21
     */
    private int stemQueueSize() {
        try {
            PlayQueue q = host.stemMixPlayQueue();
            if (q != null) return q.size();
        } catch (Exception ignored) {}
        // No queue → treat as closed pair so we soft-restart instead of survivor kill. 2026-07-21
        return session.songCount();
    }

    /**
     * Soft-restart one song at 0 with equal-power fade — same StemMixer, partner untouched.
     * Layman: that track starts over quietly then fades in; the other keeps mixing.
     * Technical: mute pads → seekAllPlaying(0) → fadeSongGainsIn; force LONG equal-power when pair-repeat.
     * Was: survivor handoff or softReplace reload; WAVE could hard-cut restart.
     * Reversal: call softReplace / survivor path; use active transitionPreset only.
     * 2026-07-21
     */
    private void softRestartSong(int songIndex) {
        if (!sessionActive) return;
        final int song = StemControls.clampSongIndex(songIndex, Math.max(1, session.songCount()));
        StemMixer m = mixerAt(song);
        if (m == null && !session.isMulti()) {
            // Single-track still loading / finished UI — fall open to Center play again. 2026-07-21
            songFinished = true;
            ready = true;
            loading = false;
            wheelLoopMode = false;
            clearZoneLoopCtrl();
            if (statusView != null) {
                statusView.setText("Finished. Center to play again");
                statusView.setTextColor(0xFFB0B0B8);
            }
            scheduleFaceInvalidate();
            return;
        }
        if (m == null) return;
        mashupHandoffBusy = true;
        songFinished = false;
        // Pair-repeat (queue ≤2): always LONG equal-power — not WAVE hard cut. 2026-07-21
        final int savedPreset = transitionPreset;
        final long savedMs = transitionMs;
        if (StemMixQueuePolicy.shouldPairRepeat(stemQueueSize())) {
            transitionPreset = StemControls.TRANSITION_PRESET_LONG;
            transitionMs = StemControls.transitionMsForPreset(transitionPreset);
        }
        // Snapshot this song’s zone gains before mute-for-seek. 2026-07-21
        final float[] targets = new float[StemMixer.STEM_COUNT];
        StemSession.SongState st = session.song(song);
        for (int z = 0; z < StemMixer.STEM_COUNT; z++) {
            float g = 0f;
            if (st != null && z < st.gains.length) g = st.gains[z];
            // Pad still on this song but SongState muted — use pad level so audible pads return. 2026-07-21
            if (session.isMulti() && session.songIndexForZone(z) == song
                    && StemControls.isGainSilent(g)) {
                g = session.padGain(z);
            }
            targets[z] = g;
            applySongZoneGain(song, z, 0f);
        }
        softRestartSkipDriftSong = song;
        softRestartSkipDriftUntilMs = android.os.SystemClock.uptimeMillis()
                + Math.max(800L, transitionMs) + 500L;
        try {
            m.seekAllPlaying(0);
            m.play();
        } catch (Exception ignored) {}
        fadeSongGainsIn(song, targets);
        if (statusView != null) {
            String name = session.trackDisplayNameForSong(song);
            if (name == null || name.length() == 0) name = "Track " + (song + 1);
            statusView.setText("Repeating · " + name);
            statusView.setTextColor(0xFFB0B0B8);
        }
        scheduleFaceInvalidate();
        updateInteractedTrackTitle();
        final long holdMs = Math.max(400L, transitionMs);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Restore user TRANSITION preset after pair soft-restart. 2026-07-21
                transitionPreset = savedPreset;
                transitionMs = savedMs;
                mashupHandoffBusy = false;
                if (softRestartSkipDriftSong == song
                        && android.os.SystemClock.uptimeMillis() >= softRestartSkipDriftUntilMs) {
                    softRestartSkipDriftSong = -1;
                }
                flushPendingSongComplete();
                if (ready && statusView != null) updateStatusLine();
            }
        }, holdMs);
    }

    /** Best-effort duration for BPM guess (MediaMetadataRetriever). 2026-07-19 */
    private static int probeDurationMs(File track) {
        if (track == null || !track.isFile()) return 180_000;
        android.media.MediaMetadataRetriever mmr = null;
        try {
            mmr = new android.media.MediaMetadataRetriever();
            mmr.setDataSource(track.getAbsolutePath());
            String d = mmr.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                int ms = Integer.parseInt(d);
                if (ms > 1000) return ms;
            }
        } catch (Exception ignored) {
        } finally {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (Exception ignored) {}
            }
        }
        return 180_000;
    }

    /**
     * All song mixers prepared — start every timeline at gains 0 (wheel raises volume).
     * Layman: everything is playing quietly; turn pads up to hear the mix.
     * Was: one mixer play(). Reversal: mixer.play() only for song0.
     * 2026-07-19
     */
    private void onAllMixersReady() {
        ready = true;
        loading = false;
        songFinished = false;
        // ID3 titles + embedded art for StemFM pads (off UI thread decode → main). 2026-07-20
        loadMashupMetaAndArt();
        // All pads wake at 50% (single + multi) for audible cold start. 2026-07-21
        session.seedMashupStartPadGains();
        if (session.isMulti()) {
            syncAllSongGainsToMixers();
            shuffleMashupPads(true);
        } else {
            syncAllSongGainsToMixers();
        }
        // #region agent log
        try {
            int totalPlayers = 0;
            int z3 = 0;
            if (mixers != null) {
                for (int i = 0; i < mixers.length; i++) {
                    StemMixer m = mixers[i];
                    if (m == null) continue;
                    totalPlayers += m.getPlayerCount();
                    z3 += m.countPlayersForZone(3);
                }
            }
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("songs", mixers != null ? mixers.length : 0);
            d.put("totalPlayers", totalPlayers);
            d.put("melodyPlayers", z3);
            d.put("playingAll", true);
            d.put("seededMultiGains", session.isMulti());
            com.solar.launcher.Debug8b0481Log.log(
                    "StemPlayerHost.onAllMixersReady", "mashup player budget", "H1,H3", d);
        } catch (Exception ignored) {}
        // #endregion
        // Prepare timelines paused — OK starts play so user can set levels / scrub first. 2026-07-21
        // Was: m.play() here. Reversal: restore auto-play loop below.
        if (mixers != null) {
            for (int i = 0; i < mixers.length; i++) {
                StemMixer m = mixers[i];
                if (m == null) continue;
                try {
                    m.seekAllPlaying(0);
                } catch (Exception ignored) {}
            }
            StemMixer lead = mixers[0];
            StemSession.SongState st = session.song(0);
            if (st != null && lead != null) st.bpm = lead.getBpm();
        }
        playbackStarted = false;
        if (mashupFace != null) mashupFace.setCentreTransportPlaying(false);
        main.removeCallbacks(mashDriftRunnable);
        if (mixers != null && mixers.length > 1) {
            main.postDelayed(mashDriftRunnable, 3000);
        }
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("ready", ready);
            d.put("multi", session.isMulti());
            d.put("mixerCount", mixers != null ? mixers.length : 0);
            d.put("playing", true);
            com.solar.launcher.Debug543e15Log.log(
                    "StemPlayerHost.onAllMixersReady", "all timelines started gains0", "A", d);
        } catch (Exception ignored) {}
        // #endregion
        refreshFace();
        if (statusView != null) {
            // Names live on dials — hide tip walls for all Stem sessions. 2026-07-21
            // Was: only hide when isMulti(); single kept status text. Reversal: that else branch.
            statusView.setVisibility(View.GONE);
            if (statusBusyHost != null) statusBusyHost.setVisibility(View.GONE);
            if (hintView != null) hintView.setVisibility(View.GONE);
            if (titleView != null) titleView.setVisibility(View.GONE);
        }
        updateInteractedTrackTitle();
        host.setStatusTitle(session.isMulti() ? "Stem Mashup" : "Stem Player");
        // Start 2s idle shrink clock once the face is live. 2026-07-21
        notePadInteraction();
    }

    /**
     * Queue one face paint on the next looper pass (coalesce rapid fade/crossfade ticks).
     * Layman: don’t redraw the pads a dozen times in one blink.
     * Was: refreshFace() every tick. Reversal: call refreshFaceNow() directly.
     * 2026-07-21
     */
    private void scheduleFaceInvalidate() {
        if (faceInvalidatePending) return;
        faceInvalidatePending = true;
        long now = android.os.SystemClock.uptimeMillis();
        long delay = Math.max(0, StemControls.FACE_INVALIDATE_MIN_MS - (now - lastFaceInvalidateTimeMs));
        if (delay == 0) {
            main.post(faceInvalidateRunnable);
        } else {
            main.postDelayed(faceInvalidateRunnable, delay);
        }
    }

    /** Immediate face paint (attach / key paths that need sync). 2026-07-21 */
    private void refreshFace() {
        scheduleFaceInvalidate();
    }

    private void refreshFaceNow() {
        float[] g = new float[4];
        for (int i = 0; i < 4; i++) {
            // Face beads = pad-owned gain (unchanged across track swap). 2026-07-21
            // Was: song(songIndexForZone).gains[i]. Reversal: that SongState read.
            g[i] = session.padGain(i);
        }
        // Always complications mashup face (single-track + multi). 2026-07-21
        // Was: mashup only when isMulti(); else StemFaceView lights. Reversal: that branch.
        if (mashupFace != null) {
            int[] zoneSongs = new int[4];
            session.copyZoneSongs(zoneSongs);
            // Single-track: all pads show song 0. 2026-07-21
            if (!session.isMulti()) {
                for (int z = 0; z < 4; z++) zoneSongs[z] = 0;
            }
            int halo = 0xFFFFCC00;
            try {
                halo = host.stemFocusHaloColor();
            } catch (Exception ignored) {}
            String t0 = titledForQueue(0);
            String t1 = session.isMulti() ? titledForQueue(1) : "";
            mashupFace.setState(zoneSongs, g, activeZone,
                    t0,
                    session.trackArtistForSong(0),
                    t1,
                    session.isMulti() ? session.trackArtistForSong(1) : "",
                    halo, loading, session.songsShareAlbum() || mashupSameArtBytes);
            // Re-apply Hold-OK scrub chrome after state paint. 2026-07-21
            if (faceScrubArmed) paintFaceScrubCursor();
            feedComplicationDials();
            syncPrepBusyChrome();
            return;
        }
        syncPrepBusyChrome();
    }

    /**
     * Push Up Next + Prep chronograph onto corner dials (real prep only).
     * Layman: waiting song and cook status sit on the watch dials.
     * 2026-07-21
     */
    private void feedComplicationDials() {
        if (mashupFace == null) return;
        String next = "";
        try {
            PlayQueue q = host.stemMixPlayQueue();
            next = StemMixQueuePolicy.nextUpLabel(q, StemMixQueuePolicy.STEM_LIVE_WINDOW);
        } catch (Exception ignored) {}
        String prepKey = QueuePrepStatus.KEY_IDLE;
        boolean overflowWait = false;
        try {
            PlayQueue q = host.stemMixPlayQueue();
            if (q != null && q.size() > StemMixQueuePolicy.STEM_LIVE_WINDOW) {
                overflowWait = true;
            }
            if (loading) {
                prepKey = QueuePrepStatus.KEY_STEMS;
            } else if (QueuePrepStatusRegistry.anyBusy()) {
                // Prefer live overflow file’s prep key when present. 2026-07-21
                if (q != null) {
                    int ni = StemMixQueuePolicy.nextUpIndex(q, StemMixQueuePolicy.STEM_LIVE_WINDOW);
                    if (ni >= 0 && ni < q.size()) {
                        PlayQueue.QueueItem it = q.items().get(ni);
                        if (it != null && it.file != null) {
                            prepKey = QueuePrepStatusRegistry.get(it.file);
                        }
                    }
                }
                if (!QueuePrepStatus.isBusy(prepKey)) {
                    prepKey = QueuePrepStatus.KEY_STEMS;
                }
            }
        } catch (Exception ignored) {}
        String prepText = StemComplicationGeometry.prepDialLabel(prepKey, overflowWait);
        float frac = StemComplicationGeometry.prepDialFraction(
                loading || QueuePrepStatus.isBusy(prepKey));
        mashupFace.setComplications(next, null, prepText, frac,
                loading || QueuePrepStatus.isBusy(prepKey));
    }

    /**
     * Whimsical Up Next dial flash after prep-aware reorder.
     * Layman: blink the waiting-song dial red when readiness jumped the line.
     * Audio path unchanged.
     * 2026-07-21
     */
    public void flashUpNextPrepAlert() {
        if (mashupFace != null) mashupFace.flashUpNextAlert();
    }

    /**
     * Display title for a live song with unified-queue position badge.
     * Layman: show "(2) Song" so you know which queue row feeds that dial.
     * Was: bare trackDisplayNameForSong. Reversal: return that only.
     * 2026-07-21
     */
    private String titledForQueue(int songIndex) {
        String name = session.trackDisplayNameForSong(songIndex);
        StemSession.SongState st = session.song(songIndex);
        File f = st != null ? st.track : null;
        int pos = -1;
        try {
            pos = StemMixQueuePolicy.oneBasedQueuePosition(host.stemMixPlayQueue(), f);
        } catch (Exception ignored) {}
        if (pos < 1) {
            // Fail-open: live seat order when queue lookup misses. 2026-07-21
            pos = songIndex + 1;
        }
        return StemMixQueuePolicy.titleWithQueuePosition(name, pos);
    }

    /**
     * 2026-07-20 — Keep status-bar + inline spinner aligned with stem prep.
     * Layman: spin while pads are still loading; stop when ready or failed.
     * Technical: RowBusyChrome + UiBusy REASON_STEM_PREPARE; nest-safe via clear/begin.
     * Reversal: remove calls; text-only statusView again.
     */
    private void syncPrepBusyChrome() {
        if (statusBusyHost != null) {
            RowBusyChrome.setBusy(statusBusyHost, loading);
        }
        if (loading) {
            if (!UiBusy.isBusy(UiBusy.REASON_STEM_PREPARE)) {
                UiBusy.beginAutoEnd(UiBusy.REASON_STEM_PREPARE, 600_000L);
            }
        } else {
            UiBusy.clear(UiBusy.REASON_STEM_PREPARE);
        }
    }

    /**
     * Title + status chrome for the song on the focused pad.
     * Layman: show which track you are mixing on this pad.
     * 2026-07-19
     */
    private void updateInteractedTrackTitle() {
        if (titleView == null) return;
        String songName = "";
        if (activeZone >= 0) {
            songName = session.trackDisplayNameForZone(activeZone);
        }
        if (songName.length() == 0 && track != null) {
            songName = stripTrackExt(track.getName());
        }
        if (songName.length() == 0) {
            songName = session.isMulti() ? "Stem Mashup" : "Stem Player";
        }
        titleView.setText(songName);
        titleView.setSelected(true);
        try {
            host.setStatusTitle(songName);
        } catch (Exception ignored) {}
    }

    private void updateStatusLine() {
        if (statusView == null || !ready) return;
        StemMixer m = activeMixer();
        String trackBit = "";
        if (activeZone >= 0) {
            String tn = session.trackDisplayNameForZone(activeZone);
            if (tn.length() > 0) trackBit = tn + " · ";
        }
        // Next-up from unified queue (StemFM). 2026-07-21
        String nextBit = "";
        if (session.isMulti()) {
            try {
                com.solar.launcher.PlayQueue q = host.stemMixPlayQueue();
                String nu = StemMixQueuePolicy.nextUpLabel(q, StemMixQueuePolicy.STEM_LIVE_WINDOW);
                if (nu != null && nu.length() > 0) nextBit = " · Next up " + nu;
            } catch (Exception ignored) {}
        }
        if (stemStutterArmed && m != null && m.isStuttering()) {
            // Show live screw (persisted or classic hold default). 2026-07-20
            StemSession.SongState sst = session.activeSongState();
            float screwShow = StemControls.DEFAULT_HOLD_SCREW_RATE;
            if (sst != null && sst.screwRate > 0.1f && sst.screwRate < 0.99f) {
                screwShow = sst.screwRate;
            }
            if (centerDown) {
                statusView.setText(trackBit + "Screw " + formatScrew(screwShow)
                        + " · wheel = rate · release Center = roll size");
            } else {
                statusView.setText(trackBit + "Beat roll " + StemBpm.rollLabel(stutterChopStep)
                        + " · screw " + formatScrew(screwShow)
                        + " · wheel = slice · hold Center = screw");
            }
            statusView.setTextColor(zoneColor(activeZone));
            return;
        }
        if (wheelLoopMode && m != null) {
            if (StemControls.isLoopBarsNone(editLoopBars)) {
                statusView.setText(trackBit + "Loop off · scroll to bars · Center = volume");
            } else {
                statusView.setText(trackBit + "Loop " + formatBars(editLoopBars)
                        + ". CW shorter · Center = volume");
            }
            statusView.setTextColor(0xFFFFFFFF);
            return;
        }
        if (activeZone < 0) {
            statusView.setText("Ready: tap a stem pad · wheel raises volume" + nextBit);
            statusView.setTextColor(0xFFB0B0B8);
            return;
        }
        String name = LalalClient.labelForZone(activeZone);
        int pct = m != null ? Math.round(m.getGain(activeZone) * 100f) : 0;
        String songBit = session.isMulti()
                ? (" · song " + session.displaySongNumber(activeZone)) : "";
        // No "Center = loop" — loop/chop disarmed; wheel = volume only. 2026-07-21
        // Was: loopBit + hold = beat roll · Center = loop. Reversal: restore that suffix.
        statusView.setText(trackBit + name + "  " + pct + "%" + songBit + nextBit
                + " · wheel = volume");
        statusView.setTextColor(zoneColor(activeZone));
    }

    /** Drop file extension for on-face track titles. 2026-07-19 */
    private static String stripTrackExt(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }

    private static boolean isPrevKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    private static boolean isNextKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    /**
     * Center dial only — ENTER / DPAD_CENTER (not MEDIA_PLAY_PAUSE).
     * Play (85) is Melody (bottom). If Y1 wheel click only ever sends 85, STEM_KEY logs will show
     * and Center dial never fires — then we revisit the collision.
     * 2026-07-19
     */
    private static boolean isStemCenterKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == 66
                || keyCode == 23;
    }

    private static int zoneColor(int z) {
        if (z < 0 || z >= StemFaceView.STEM_COLORS.length) return 0xFFB0B0B8;
        return StemFaceView.STEM_COLORS[z];
    }

    private static String formatBars(float bars) {
        if (StemControls.isLoopBarsNone(bars)) return "off";
        if (bars == (int) bars) return String.valueOf((int) bars) + " bar";
        return bars + " bar";
    }

    /** Short screw rate for status (e.g. 0.85). 2026-07-20 */
    private static String formatScrew(float rate) {
        if (rate >= 0.995f) return "1.0";
        return String.format(java.util.Locale.US, "%.2f", rate);
    }

    /**
     * Status chip — phase + overall % + step detail (so mix is not frozen at 96%).
     * 2026-07-19
     */
    private static String phaseLabel(String phase, int percent, String detail) {
        String head;
        if ("upload".equals(phase)) head = "Uploading";
        else if ("split".equals(phase)) head = "Separating stems";
        else if ("download".equals(phase)) head = "Downloading stems";
        else if ("mix".equals(phase)) head = "Mixing Melody pad";
        else if ("publish".equals(phase)) head = "Saving stems";
        else if ("ready".equals(phase)) head = "Ready";
        else head = phase != null ? phase : "";
        StringBuilder sb = new StringBuilder();
        sb.append(head);
        if (!"ready".equals(phase)) {
            sb.append("… ").append(percent).append('%');
        }
        if (detail != null && detail.length() > 0 && !"ready".equals(phase)) {
            sb.append(" · ").append(detail);
        }
        return sb.toString();
    }

    private static TextView label(Context ctx, String text, int sp, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(sp);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(false);
        return tv;
    }

    private static int dp(Context ctx, int v) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
