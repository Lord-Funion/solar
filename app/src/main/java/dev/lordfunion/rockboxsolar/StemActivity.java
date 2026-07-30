package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.conscrypt.Conscrypt;

import java.io.File;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class StemActivity extends Activity {
    private static final String PREFS = "lalal_api";
    private static final String KEY = "license_key";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AppUi.Screen ui;
    private ArrayAdapter<String> adapter;
    private File source;
    private String stem = "vocals";
    private String extraction = "deep_extraction";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { Security.insertProviderAt(Conscrypt.newProvider(), 1); } catch (Throwable ignored) { }
        ui = AppUi.screen(this, "Stem Player", "LALAL.AI separation, local caching, and two-stem mixing");
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ui.list.setAdapter(adapter);
        refresh();
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) setKey();
                else if (position == 1) chooseSource();
                else if (position == 2) chooseStem();
                else if (position == 3) chooseExtraction();
                else if (position == 4) startSplit();
                else if (position == 5) startActivity(new Intent(StemActivity.this, StemMixerActivity.class));
                else if (position == 6) checkMinutes();
            }
        });
    }

    private void refresh() {
        adapter.clear();
        String key = getPreferences().getString(KEY, "");
        adapter.add("LALAL.AI license key — " + (key.length() == 0 ? "not configured" : "configured"));
        adapter.add("Source file — " + (source == null ? "choose local audio" : source.getAbsolutePath()));
        adapter.add("Stem — " + stem);
        adapter.add("Extraction — " + extraction);
        adapter.add("Separate and download stems");
        adapter.add("Open stem mixer");
        adapter.add("Check remaining processing minutes");
        adapter.notifyDataSetChanged();
    }

    private android.content.SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void setKey() {
        AppUi.prompt(this, "LALAL.AI license key", "X-License-Key", getPreferences().getString(KEY, ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, new AppUi.TextCallback() {
                    @Override public void onText(String value) { getPreferences().edit().putString(KEY, value).apply(); refresh(); }
                });
    }

    private void chooseSource() {
        String initial = source == null ? new File(Environment.getExternalStorageDirectory(), "Music").getAbsolutePath() : source.getAbsolutePath();
        AppUi.promptText(this, "Local audio path", "/sdcard/Music/song.mp3", initial, new AppUi.TextCallback() {
            @Override public void onText(String value) {
                File file = new File(value);
                if (!file.isFile()) Toast.makeText(StemActivity.this, "That file does not exist", Toast.LENGTH_LONG).show();
                else { source = file; refresh(); }
            }
        });
    }

    private void chooseStem() {
        final String[] values = {"vocals", "drum", "bass", "piano", "electric_guitar", "acoustic_guitar", "synthesizer", "strings", "wind"};
        new AlertDialog.Builder(this).setTitle("Stem to isolate")
                .setItems(values, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { stem = values[which]; refresh(); }
                }).show();
    }

    private void chooseExtraction() {
        final String[] labels = {"Deep extraction", "Clear cut"};
        new AlertDialog.Builder(this).setTitle("Extraction level")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { extraction = which == 0 ? "deep_extraction" : "clear_cut"; refresh(); }
                }).show();
    }

    private void startSplit() {
        final String key = getPreferences().getString(KEY, "");
        if (key.length() == 0 || source == null) {
            Toast.makeText(this, "Configure a license key and source file first", Toast.LENGTH_LONG).show(); return;
        }
        AppUi.confirm(this, "Use processing minutes?", "The selected local file will be uploaded to LALAL.AI and this task may consume paid processing minutes.", new AppUi.ConfirmCallback() {
            @Override public void onConfirm() {
                worker.execute(new Runnable() {
                    @Override public void run() {
                        final LalalClient client = new LalalClient(new OkHttpClient.Builder().build(), key);
                        try {
                            LalalClient.SplitResult result = client.split(source, stem, extraction, new LalalClient.Progress() {
                                @Override public void onProgress(final String status) { runOnUiThread(new Runnable() { @Override public void run() { ui.subtitle.setText(status); }}); }
                            });
                            File directory = new File(new File(Environment.getExternalStorageDirectory(), "Music/RockboxSolar"), "Stems");
                            final List<File> outputs = client.downloadAll(result, directory, stripExtension(source.getName()), new LalalClient.Progress() {
                                @Override public void onProgress(final String status) { runOnUiThread(new Runnable() { @Override public void run() { ui.subtitle.setText(status); }}); }
                            });
                            client.deleteSource(result.sourceId);
                            String[] paths = new String[outputs.size()]; for (int i = 0; i < outputs.size(); i++) paths[i] = outputs.get(i).getAbsolutePath();
                            MediaScannerConnection.scanFile(StemActivity.this, paths, null, null);
                            runOnUiThread(new Runnable() { @Override public void run() {
                                ui.subtitle.setText("Complete: " + outputs.size() + " tracks in Music/RockboxSolar/Stems");
                                Toast.makeText(StemActivity.this, "Stem separation complete", Toast.LENGTH_LONG).show();
                            }});
                        } catch (final Exception e) {
                            runOnUiThread(new Runnable() { @Override public void run() { ui.subtitle.setText("Failed"); Toast.makeText(StemActivity.this, e.getMessage(), Toast.LENGTH_LONG).show(); }});
                        }
                    }
                });
            }
        });
    }

    private void checkMinutes() {
        final String key = getPreferences().getString(KEY, "");
        if (key.length() == 0) { Toast.makeText(this, "Configure the license key first", Toast.LENGTH_LONG).show(); return; }
        worker.execute(new Runnable() { @Override public void run() {
            try {
                final double left = new LalalClient(new OkHttpClient.Builder().build(), key).minutesLeft();
                runOnUiThread(new Runnable() { @Override public void run() { ui.subtitle.setText("Processing minutes left: " + left); }});
            } catch (final Exception e) { runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(StemActivity.this, e.getMessage(), Toast.LENGTH_LONG).show(); }}); }
        }});
    }

    private static String stripExtension(String name) { int dot = name.lastIndexOf('.'); return dot > 0 ? name.substring(0, dot) : name; }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
