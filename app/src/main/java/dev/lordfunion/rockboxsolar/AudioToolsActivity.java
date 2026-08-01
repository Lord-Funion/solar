package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRecorder;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class AudioToolsActivity extends Activity {
    private AppUi.Screen ui;
    private ArrayAdapter<String> adapter;
    private SharedPreferences prefs;
    private MediaRecorder recorder;
    private File recording;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(AudioEffects.PREFS, Context.MODE_PRIVATE);
        ui = AppUi.screen(this, "Audio Tools", "DSP presets, bass, spatial effect, recording, and FM hardware probe");
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        ui.list.setAdapter(adapter); refresh();
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) choosePreset(); else if (position == 1) chooseStrength("bass", "Bass boost");
                else if (position == 2) chooseStrength("virtualizer", "Virtualizer");
                else if (position == 3) toggleRecording(); else if (position == 4) launchFm();
            }
        });
    }

    private void refresh() {
        adapter.clear();
        adapter.add("Equalizer preset — " + (prefs.getBoolean("eq_enabled", false) ? prefs.getString("preset", "Flat") : "disabled"));
        adapter.add("Bass boost — " + prefs.getInt("bass", 0) + "/1000");
        adapter.add("Virtualizer — " + prefs.getInt("virtualizer", 0) + "/1000");
        adapter.add(recorder == null ? "Start microphone recording" : "Stop recording");
        adapter.add("Open/probe FM radio hardware");
        adapter.notifyDataSetChanged();
    }

    private void choosePreset() {
        final ArrayList<String> names = new ArrayList<String>(); names.add("Disabled");
        Equalizer eq = null;
        try {
            eq = new Equalizer(0, 0); short count = eq.getNumberOfPresets();
            for (short i = 0; i < count; i++) names.add(eq.getPresetName(i));
        } catch (Throwable ignored) { }
        if (eq != null) eq.release();
        if (names.size() == 1) { names.add("Flat"); names.add("Rock"); names.add("Classical"); }
        final String[] values = names.toArray(new String[names.size()]);
        new android.app.AlertDialog.Builder(this).setTitle("Equalizer preset").setItems(values, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) {
                prefs.edit().putBoolean("eq_enabled", which != 0).putString("preset", values[which]).apply(); refresh();
                Toast.makeText(AudioToolsActivity.this, "Applies when the next track starts", Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    private void chooseStrength(final String key, String title) {
        final String[] labels = {"Off", "Low", "Medium", "High", "Maximum"};
        final int[] values = {0, 250, 500, 750, 1000};
        new android.app.AlertDialog.Builder(this).setTitle(title).setItems(labels, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) { prefs.edit().putInt(key, values[which]).apply(); refresh(); }
        }).show();
    }

    private void toggleRecording() {
        if (recorder != null) { stopRecording(); return; }
        File dir = new File(new File(Environment.getExternalStorageDirectory(), "Music/RockboxSolar"), "Recordings");
        if (!dir.exists()) dir.mkdirs();
        recording = new File(dir, "Recording " + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(new Date()) + ".m4a");
        try {
            recorder = new MediaRecorder(); recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000); recorder.setAudioSamplingRate(44100); recorder.setOutputFile(recording.getAbsolutePath());
            recorder.prepare(); recorder.start(); ui.subtitle.setText("Recording to " + recording.getName()); refresh();
        } catch (Exception e) { if (recorder != null) { recorder.release(); recorder = null; } Toast.makeText(this, "Recording failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void stopRecording() {
        try { recorder.stop(); } catch (Exception ignored) { if (recording != null) recording.delete(); }
        recorder.release(); recorder = null;
        if (recording != null && recording.isFile()) {
            sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(recording)));
            ui.subtitle.setText("Saved " + recording.getAbsolutePath());
        }
        refresh();
    }

    private void launchFm() {
        String[] packages = {"com.mediatek.FMRadio", "com.mediatek.fmradio", "com.android.fmradio", "com.caf.fmradio", "com.sec.android.app.fm"};
        for (String name : packages) {
            try { Intent intent = getPackageManager().getLaunchIntentForPackage(name); if (intent != null) { startActivity(intent); return; } } catch (Exception ignored) { }
        }
        try { startActivity(new Intent("com.mediatek.FMRadio.FMRadioActivity")); return; } catch (Exception ignored) { }
        Toast.makeText(this, "No compatible FM application/API was found. Wired headphones may be required as the antenna.", Toast.LENGTH_LONG).show();
    }

    @Override protected void onDestroy() { if (recorder != null) stopRecording(); super.onDestroy(); }
}
