package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public final class StemMixerActivity extends Activity {
    private MediaPlayer a;
    private MediaPlayer b;
    private File fileA;
    private File fileB;
    private TextView status;
    private SeekBar volumeA;
    private SeekBar volumeB;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); int p = AppUi.dp(this, 12); root.setPadding(p,p,p,p);
        TextView title = AppUi.text(this, "Stem Mixer", 24f); root.addView(title);
        status = AppUi.text(this, "Choose two cached stems. Playback starts them together.", 14f); root.addView(status);
        Button chooseA = new Button(this); chooseA.setText("Choose stem A"); root.addView(chooseA);
        volumeA = new SeekBar(this); volumeA.setMax(100); volumeA.setProgress(100); root.addView(volumeA);
        Button chooseB = new Button(this); chooseB.setText("Choose stem B"); root.addView(chooseB);
        volumeB = new SeekBar(this); volumeB.setMax(100); volumeB.setProgress(100); root.addView(volumeB);
        Button play = new Button(this); play.setText("Play / pause together"); root.addView(play);
        Button stop = new Button(this); stop.setText("Stop and rewind"); root.addView(stop);
        setContentView(root);
        chooseA.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { choose(true); }});
        chooseB.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { choose(false); }});
        volumeA.setOnSeekBarChangeListener(listener(true)); volumeB.setOnSeekBarChangeListener(listener(false));
        play.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggle(); }});
        stop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopPlayers(); }});
    }

    private SeekBar.OnSeekBarChangeListener listener(final boolean first) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                MediaPlayer player = first ? a : b; if (player != null) { float level = progress / 100f; player.setVolume(level, level); }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
    }

    private void choose(final boolean first) {
        File dir = new File(new File(Environment.getExternalStorageDirectory(), "Music/RockboxSolar"), "Stems");
        AppUi.promptText(this, first ? "Stem A path" : "Stem B path", dir.getAbsolutePath() + "/track.mp3",
                dir.getAbsolutePath(), new AppUi.TextCallback() {
                    @Override public void onText(String value) {
                        File file = new File(value); if (!file.isFile()) { Toast.makeText(StemMixerActivity.this, "File not found", Toast.LENGTH_LONG).show(); return; }
                        if (first) fileA = file; else fileB = file; updateStatus();
                    }
                });
    }

    private void toggle() {
        if (a != null && a.isPlaying()) { a.pause(); if (b != null) b.pause(); status.setText("Paused"); return; }
        if (fileA == null || fileB == null) { Toast.makeText(this, "Choose both stems", Toast.LENGTH_LONG).show(); return; }
        if (a == null || b == null) {
            try {
                a = new MediaPlayer(); b = new MediaPlayer();
                a.setAudioStreamType(AudioManager.STREAM_MUSIC); b.setAudioStreamType(AudioManager.STREAM_MUSIC);
                a.setDataSource(fileA.getAbsolutePath()); b.setDataSource(fileB.getAbsolutePath()); a.prepare(); b.prepare();
                float va = volumeA.getProgress()/100f, vb = volumeB.getProgress()/100f; a.setVolume(va,va); b.setVolume(vb,vb);
            } catch (Exception e) { release(); Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); return; }
        }
        a.seekTo(Math.min(a.getCurrentPosition(), b.getDuration())); b.seekTo(a.getCurrentPosition()); b.start(); a.start(); status.setText("Mixing " + fileA.getName() + " + " + fileB.getName());
    }

    private void stopPlayers() { if (a != null) { try { a.pause(); a.seekTo(0); } catch(Exception ignored){} } if (b != null) { try { b.pause(); b.seekTo(0); } catch(Exception ignored){} } status.setText("Stopped"); }
    private void updateStatus() { status.setText("A: " + (fileA==null?"none":fileA.getName()) + "\nB: " + (fileB==null?"none":fileB.getName())); release(); }
    private void release() { if(a!=null){a.release();a=null;} if(b!=null){b.release();b=null;} }
    @Override protected void onDestroy(){release();super.onDestroy();}
}
