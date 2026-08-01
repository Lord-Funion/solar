package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.conscrypt.Conscrypt;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class DeezerActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<DeezerClient.Track> tracks = new ArrayList<DeezerClient.Track>();
    private AppUi.Screen ui;
    private ArrayAdapter<String> adapter;
    private DeezerClient client;
    private MediaPlayer preview;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        try { Security.insertProviderAt(Conscrypt.newProvider(), 1); } catch (Throwable ignored) { }
        ui = AppUi.screen(this, "Deezer", "Official catalog search and Deezer-provided previews");
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ui.list.setAdapter(adapter);
        client = new DeezerClient(new OkHttpClient.Builder().retryOnConnectionFailure(true).build());
        showStart();
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (tracks.isEmpty()) {
                    if (position == 0) promptSearch();
                    else if (position == 1) stopPreview();
                } else if (position >= 0 && position < tracks.size()) showTrack(tracks.get(position));
            }
        });
    }

    private void showStart() {
        tracks.clear();
        adapter.clear();
        adapter.add("Search Deezer catalog");
        adapter.add("Stop preview");
        adapter.notifyDataSetChanged();
    }

    private void promptSearch() {
        AppUi.promptText(this, "Search Deezer", "artist, album, or track", "", new AppUi.TextCallback() {
            @Override public void onText(final String value) {
                if (value.length() == 0) return;
                ui.subtitle.setText("Searching…");
                worker.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            final List<DeezerClient.Track> found = client.search(value);
                            runOnUiThread(new Runnable() { @Override public void run() {
                                tracks.clear(); tracks.addAll(found); adapter.clear();
                                for (DeezerClient.Track track : tracks) adapter.add(track.label());
                                adapter.notifyDataSetChanged();
                                ui.subtitle.setText(found.size() + " results • previews are supplied by Deezer");
                            }});
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() { @Override public void run() {
                                ui.subtitle.setText("Search failed");
                                Toast.makeText(DeezerActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                            }});
                        }
                    }
                });
            }
        });
    }

    private void showTrack(final DeezerClient.Track track) {
        final String[] actions = track.preview.length() == 0
                ? new String[]{"Open on Deezer", "Run SSH workflow with Deezer URL", "New search"}
                : new String[]{"Play 30-second preview", "Open on Deezer", "Run SSH workflow with Deezer URL", "New search"};
        new AlertDialog.Builder(this).setTitle(track.title + " — " + track.artist)
                .setMessage("Album: " + track.album + "\nDuration: " + track.duration + " seconds\nID: " + track.id)
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int offset = track.preview.length() == 0 ? 0 : 1;
                        if (track.preview.length() > 0 && which == 0) playPreview(track);
                        else if (which == offset) openUrl(track.link);
                        else if (which == offset + 1) {
                            Intent intent = new Intent(DeezerActivity.this, SshActivity.class);
                            intent.putExtra("template_url", track.link);
                            intent.putExtra("template_title", track.title);
                            intent.putExtra("template_channel", track.artist);
                            startActivity(intent);
                        } else promptSearch();
                    }
                }).show();
    }

    private void playPreview(final DeezerClient.Track track) {
        stopPreview();
        ui.subtitle.setText("Loading preview: " + track.title);
        preview = new MediaPlayer();
        preview.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            preview.setDataSource(track.preview);
            preview.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) { ui.subtitle.setText("Preview: " + track.title); mp.start(); }
            });
            preview.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { ui.subtitle.setText("Preview finished"); stopPreview(); }
            });
            preview.prepareAsync();
        } catch (Exception e) {
            stopPreview();
            Toast.makeText(this, "Preview failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this, "No browser available", Toast.LENGTH_LONG).show(); }
    }

    private void stopPreview() {
        if (preview != null) {
            try { preview.stop(); } catch (Exception ignored) { }
            preview.release(); preview = null;
        }
    }

    @Override protected void onDestroy() { stopPreview(); worker.shutdownNow(); super.onDestroy(); }
}
