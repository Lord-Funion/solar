package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.PowerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class PlayerController implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {
    interface Listener { void onPlayerChanged(); }

    private final Context context;
    private final Listener listener;
    private final ArrayList<File> queue = new ArrayList<File>();
    private MediaPlayer player;
    private final AudioEffects effects = new AudioEffects();
    private int index = -1;
    private String title = "Nothing playing";
    private String artist = "";
    private String album = "";

    PlayerController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    synchronized void play(File file, List<File> surrounding) {
        queue.clear();
        if (surrounding != null) queue.addAll(surrounding);
        index = queue.indexOf(file);
        if (index < 0) {
            queue.add(file);
            index = queue.size() - 1;
        }
        startCurrent();
    }

    synchronized void replaceQueue(List<File> tracks, int startIndex) {
        queue.clear();
        if (tracks != null) queue.addAll(tracks);
        if (queue.isEmpty()) {
            stop();
            return;
        }
        index = Math.max(0, Math.min(startIndex, queue.size() - 1));
        startCurrent();
    }

    private void startCurrent() {
        if (index < 0 || index >= queue.size()) return;
        releasePlayer();
        File file = queue.get(index);
        readMetadata(file);
        try {
            player = new MediaPlayer();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            effects.attach(context, player.getAudioSessionId());
            player.start();
        } catch (Exception e) {
            title = "Playback failed: " + file.getName();
            releasePlayer();
        }
        listener.onPlayerChanged();
    }

    synchronized void toggle() {
        if (player == null) {
            if (!queue.isEmpty()) startCurrent();
            return;
        }
        if (player.isPlaying()) player.pause(); else player.start();
        listener.onPlayerChanged();
    }

    synchronized void next() {
        if (queue.isEmpty()) return;
        index = (index + 1) % queue.size();
        startCurrent();
    }

    synchronized void previous() {
        if (queue.isEmpty()) return;
        index = (index - 1 + queue.size()) % queue.size();
        startCurrent();
    }

    synchronized void seekRelative(int milliseconds) {
        if (player == null) return;
        int target = Math.max(0, Math.min(duration(), position() + milliseconds));
        player.seekTo(target);
        listener.onPlayerChanged();
    }

    synchronized boolean isPlaying() {
        try { return player != null && player.isPlaying(); } catch (IllegalStateException e) { return false; }
    }

    synchronized int position() {
        try { return player == null ? 0 : player.getCurrentPosition(); } catch (IllegalStateException e) { return 0; }
    }

    synchronized int duration() {
        try { return player == null ? 0 : player.getDuration(); } catch (IllegalStateException e) { return 0; }
    }

    synchronized String title() { return title; }
    synchronized String artist() { return artist; }
    synchronized String album() { return album; }
    synchronized List<File> queue() { return new ArrayList<File>(queue); }
    synchronized int queueIndex() { return index; }

    synchronized void stop() {
        releasePlayer();
        listener.onPlayerChanged();
    }

    synchronized void release() { releasePlayer(); }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            effects.release();
            player.release();
            player = null;
        }
    }

    private void readMetadata(File file) {
        title = stripExtension(file.getName());
        artist = "";
        album = "";
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (value != null && value.trim().length() > 0) title = value.trim();
            value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (value != null) artist = value.trim();
            value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (value != null) album = value.trim();
        } catch (Exception ignored) {
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Override public synchronized void onCompletion(MediaPlayer mediaPlayer) { next(); }

    @Override public synchronized boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        title = "Decoder error (" + what + "/" + extra + ")";
        listener.onPlayerChanged();
        return true;
    }
}
