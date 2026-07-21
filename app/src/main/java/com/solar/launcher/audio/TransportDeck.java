package com.solar.launcher.audio;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;

import com.solar.launcher.stem.StemControls;
import com.solar.launcher.stem.StemSoundTouch;
import com.solar.launcher.video.SolarIjkPlayerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 2026-07-20 — One transport pad (MediaPlayer or IJK) with mute-via-pause gains.
 * Layman: one song layer that can fade in/out without killing the other layer.
 * Technical: audio-looper Handler for fades; StemMixer silent-pause rule.
 * Was: single MainActivity mediaPlayer hard-cut. Reversal: drop this class; use prepareMusicTrack.
 */
public final class TransportDeck {
    public interface Listener {
        void onReady(TransportDeck deck);
        void onComplete(TransportDeck deck);
        void onError(TransportDeck deck, String message);
    }

    private final Handler audio;
    private MediaPlayer mp;
    private IjkMediaPlayer ijk;
    private boolean useIjk;
    private Listener listener;
    private float gain = 1f;
    private boolean started;
    private boolean released;
    private boolean prepared;
    private File path;
    private String url;
    /** 2026-07-20 — Keep FD open until release (API 17 MediaPlayer dies if stream closes early). */
    private FileInputStream heldFileStream;
    private int fadeStepsLeft;
    private float fadeFrom;
    private float fadeTo;
    private Runnable fadeOnDone;
    private final Runnable fadeTick = new Runnable() {
        @Override
        public void run() {
            if (released) {
                if (fadeOnDone != null) {
                    Runnable d = fadeOnDone;
                    fadeOnDone = null;
                    d.run();
                }
                return;
            }
            if (fadeStepsLeft <= 0) {
                gain = fadeTo;
                applyVolume();
                Runnable d = fadeOnDone;
                fadeOnDone = null;
                if (d != null) d.run();
                return;
            }
            fadeStepsLeft--;
            // MixDeck-style 10×40ms ramp. 2026-07-20
            int done = 10 - fadeStepsLeft;
            gain = StemControls.fadeGainStep(fadeFrom, fadeTo, done, 10);
            applyVolume();
            audio.postDelayed(this, 40L);
        }
    };

    public TransportDeck(Handler audioLooperHandler) {
        this.audio = audioLooperHandler;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public File getPath() {
        return path;
    }

    public float getGain() {
        return gain;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public boolean isPlaying() {
        try {
            if (useIjk && ijk != null) return ijk.isPlaying();
            return mp != null && mp.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    /** Load local file via MediaPlayer. 2026-07-20 */
    public void loadFile(File track) throws IOException {
        loadFile(track, false);
    }

    /**
     * Load local file; preferIjk for opus/webm / EQ paths.
     * 2026-07-20
     */
    public void loadFile(File track, boolean preferIjk) throws IOException {
        releasePlayerOnly();
        prepared = false;
        started = false;
        url = null;
        if (track == null || !track.isFile()) throw new IOException("TransportDeck missing file");
        path = track;
        useIjk = preferIjk;
        if (preferIjk) {
            loadIjkPath(track.getAbsolutePath());
        } else {
            loadMediaPlayerFile(track);
        }
    }

    /** Stream / remote URL via IJK with read-ahead-friendly options. 2026-07-20 */
    public void loadUrl(String sourceUrl) throws IOException {
        releasePlayerOnly();
        prepared = false;
        started = false;
        path = null;
        if (sourceUrl == null || sourceUrl.length() == 0) {
            throw new IOException("TransportDeck missing url");
        }
        url = sourceUrl;
        useIjk = true;
        loadIjkPath(sourceUrl);
    }

    /**
     * Open local file on stock MediaPlayer.
     * Layman: hold the file open while the song plays — closing early breaks old Android.
     * Was: close FileInputStream right after setDataSource → prepare fail / “Load Failed” on Y1.
     * Reversal: close fis in finally after setDataSource again.
     * 2026-07-20
     */
    private void loadMediaPlayerFile(File track) throws IOException {
        mp = new MediaPlayer();
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        closeHeldFileStream();
        heldFileStream = new FileInputStream(track);
        mp.setDataSource(heldFileStream.getFD());
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                prepared = true;
                applyVolume();
                postReady();
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (released) return;
                if (listener != null) listener.onComplete(TransportDeck.this);
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if (listener != null) {
                    listener.onError(TransportDeck.this, "MP " + what + "/" + extra);
                }
                return true;
            }
        });
        mp.prepareAsync();
    }

    private void loadIjkPath(String src) throws IOException {
        ijk = SolarIjkPlayerFactory.create();
        StemSoundTouch.applyStemPlayerOptions(ijk);
        ijk.setAudioStreamType(AudioManager.STREAM_MUSIC);
        ijk.setDataSource(src);
        ijk.setOnPreparedListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(tv.danmaku.ijk.media.player.IMediaPlayer p) {
                prepared = true;
                applyVolume();
                postReady();
            }
        });
        ijk.setOnCompletionListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(tv.danmaku.ijk.media.player.IMediaPlayer p) {
                if (released) return;
                if (listener != null) listener.onComplete(TransportDeck.this);
            }
        });
        ijk.setOnErrorListener(new tv.danmaku.ijk.media.player.IMediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(tv.danmaku.ijk.media.player.IMediaPlayer p, int what, int extra) {
                if (listener != null) {
                    listener.onError(TransportDeck.this, "IJK " + what + "/" + extra);
                }
                return true;
            }
        });
        ijk.prepareAsync();
    }

    private void postReady() {
        audio.post(new Runnable() {
            @Override
            public void run() {
                if (released) return;
                if (listener != null) listener.onReady(TransportDeck.this);
            }
        });
    }

    /** Start from ms (lockstep seek). 2026-07-20 */
    public void playFrom(int ms) {
        if (released || !prepared) return;
        seekTo(ms);
        start();
    }

    public void start() {
        if (released) return;
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("path", path != null ? path.getName() : (url != null ? "url" : "null"));
            d.put("useIjk", useIjk);
            d.put("wasStarted", started);
            d.put("wasPlaying", isPlaying());
            d.put("deckId", System.identityHashCode(this));
            com.solar.launcher.Debug290fecLog.log("TransportDeck.start", "deck start", "H2,H4,H5", d);
        } catch (Exception ignored) {}
        // #endregion
        try {
            if (useIjk && ijk != null) {
                ijk.start();
            } else if (mp != null) {
                mp.start();
            }
            started = true;
            applyVolume();
        } catch (Exception e) {
            if (listener != null) listener.onError(this, e.getMessage());
        }
    }

    public void pause() {
        // #region agent log
        try {
            org.json.JSONObject d = new org.json.JSONObject();
            d.put("path", path != null ? path.getName() : (url != null ? "url" : "null"));
            d.put("wasPlaying", isPlaying());
            d.put("deckId", System.identityHashCode(this));
            com.solar.launcher.Debug290fecLog.log("TransportDeck.pause", "deck pause", "H1,H2", d);
        } catch (Exception ignored) {}
        // #endregion
        try {
            if (useIjk && ijk != null && ijk.isPlaying()) ijk.pause();
            else if (mp != null && mp.isPlaying()) mp.pause();
        } catch (Exception ignored) {}
    }

    public void setGainImmediate(float g) {
        audio.removeCallbacks(fadeTick);
        fadeStepsLeft = 0;
        fadeOnDone = null;
        gain = StemControls.clampGain(g);
        applyVolume();
    }

    /** Smooth fade on audio looper (~400ms). 2026-07-20 */
    public void fadeTo(float target, Runnable onDone) {
        audio.removeCallbacks(fadeTick);
        fadeFrom = gain;
        fadeTo = StemControls.clampGain(target);
        fadeStepsLeft = 10;
        fadeOnDone = onDone;
        audio.post(fadeTick);
    }

    public int getPositionMs() {
        try {
            if (useIjk && ijk != null) return (int) ijk.getCurrentPosition();
            return mp != null ? mp.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getDurationMs() {
        try {
            if (useIjk && ijk != null) return (int) ijk.getDuration();
            return mp != null ? mp.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public void seekTo(int ms) {
        int p = Math.max(0, ms);
        try {
            if (useIjk && ijk != null) {
                ijk.seekTo(p);
                if (started && !ijk.isPlaying() && !StemControls.isGainSilent(gain)) ijk.start();
            } else if (mp != null) {
                mp.seekTo(p);
                if (started && !mp.isPlaying() && !StemControls.isGainSilent(gain)) mp.start();
            }
        } catch (Exception ignored) {}
    }

    /**
     * Gapless handoff when both decks are stock MediaPlayer.
     * Layman: next song sits ready so the end of this one does not click.
     * Technical: MediaPlayer.setNextMediaPlayer. Reversal: ignore; dual-slot start on complete.
     * 2026-07-20
     */
    public boolean attachNextMediaPlayer(TransportDeck next) {
        if (useIjk || next == null || next.useIjk || mp == null || next.mp == null) return false;
        try {
            mp.setNextMediaPlayer(next.mp);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void release() {
        released = true;
        audio.removeCallbacks(fadeTick);
        releasePlayerOnly();
        listener = null;
    }

    private void applyVolume() {
        boolean silent = StemControls.isGainSilent(gain);
        float v = silent ? 0f : gain;
        try {
            if (useIjk && ijk != null) {
                ijk.setVolume(v, v);
                if (silent) {
                    if (ijk.isPlaying()) ijk.pause();
                } else if (started && !released && !ijk.isPlaying()) {
                    ijk.start();
                }
            } else if (mp != null) {
                mp.setVolume(v, v);
                if (silent) {
                    if (mp.isPlaying()) mp.pause();
                } else if (started && !released && !mp.isPlaying()) {
                    mp.start();
                }
            }
        } catch (Exception ignored) {}
    }

    private void releasePlayerOnly() {
        if (ijk != null) {
            try { ijk.stop(); } catch (Exception ignored) {}
            try { ijk.release(); } catch (Exception ignored) {}
            ijk = null;
        }
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) {}
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
        // Close after MediaPlayer.release so the FD stays valid through prepare/play. 2026-07-20
        closeHeldFileStream();
        prepared = false;
        started = false;
        useIjk = false;
    }

    /** Drop the held local-file stream. 2026-07-20 */
    private void closeHeldFileStream() {
        if (heldFileStream == null) return;
        try {
            heldFileStream.close();
        } catch (Exception ignored) {}
        heldFileStream = null;
    }
}
