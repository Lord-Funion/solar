package com.solar.launcher.stem;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.solar.launcher.R;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2026-07-24 — Full-screen blocking UI for stem batch preparation.
 * Takes a list of tracks, stops media, and separates them sequentially.
 */
public class StemPrepHost {
    private final Activity activity;
    private final View root;
    private final TextView tvSubtitle;
    private final ProgressBar pbProgress;
    private final TextView tvPercent;
    
    private final List<File> tracks;
    private final Callbacks callbacks;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService io;
    
    private boolean cancelled = false;

    public interface Callbacks {
        void onBatchFinished();
        void onBatchError(String error);
    }

    public StemPrepHost(Activity activity, View root, List<File> tracks, Callbacks callbacks) {
        this.activity = activity;
        this.root = root;
        this.tracks = tracks;
        this.callbacks = callbacks;
        
        tvSubtitle = root.findViewById(R.id.stem_prep_subtitle);
        pbProgress = root.findViewById(R.id.stem_prep_progress);
        tvPercent = root.findViewById(R.id.stem_prep_percent);
        
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Block clicks
            }
        });
    }

    public void start() {
        if (tracks == null || tracks.isEmpty()) {
            callbacks.onBatchFinished();
            return;
        }
        
        cancelled = false;
        io = Executors.newSingleThreadExecutor();
        
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = activity.getSharedPreferences(LalalAccount.PREFS_NAME, Context.MODE_PRIVATE);
                    String key = LalalAccount.effectiveKey(prefs);
                    if (key == null || key.length() < 8) {
                        postError("No Lalal.ai API key configured.");
                        return;
                    }

                    LalalClient client = new LalalClient(key);
                    for (int i = 0; i < tracks.size(); i++) {
                        if (cancelled) break;
                        final File track = tracks.get(i);
                        final String trackName = track.getName();
                        
                        final String baseStatus = "Song " + (i + 1) + " of " + tracks.size() + " · " + trackName;
                        postUpdate(baseStatus, 0);

                        File stemsDir = LalalClient.userStemsDir(track);
                        if (stemsDir != null) {
                            List<LalalClient.StemFile> existing = LalalClient.loadUserStems(track);
                            if (existing != null && existing.size() >= 4) {
                                // Already prepared
                                continue;
                            }
                        }
                        
                        try {
                            File workDir = new File(activity.getCacheDir(), "stem_prep_work");
                            workDir.mkdirs();
                            List<LalalClient.StemFile> result = client.separateToMp3(track, workDir, stemsDir, new LalalClient.Progress() {
                                @Override
                                public void onProgress(final String phase, final int percent, final String detail) {
                                    if (cancelled) return;
                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            tvSubtitle.setText(baseStatus + "\n" + (detail != null ? detail : phase));
                                            pbProgress.setProgress(Math.max(0, percent));
                                            tvPercent.setText(Math.max(0, percent) + "%");
                                        }
                                    });
                                }
                            });
                            
                            if (result != null && result.size() >= 4 && stemsDir != null) {
                                LalalClient.writeTrackMarkerIfOwned(stemsDir, track);
                            } else {
                                postError("Failed to separate track: " + trackName);
                                return;
                            }
                        } catch (Exception e) {
                            postError("Error separating track: " + trackName);
                            return;
                        }
                    }
                    
                    if (!cancelled) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                callbacks.onBatchFinished();
                            }
                        });
                    }
                } catch (final Exception e) {
                    postError(e.getMessage() != null ? e.getMessage() : "Extraction failed");
                }
            }
        });
    }

    public void cancel() {
        cancelled = true;
        if (io != null) {
            io.shutdownNow();
        }
    }

    private void postUpdate(final String text, final int percent) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                tvSubtitle.setText(text);
                pbProgress.setProgress(percent);
                tvPercent.setText(percent + "%");
            }
        });
    }

    private void postError(final String error) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                callbacks.onBatchError(error);
            }
        });
    }
}
