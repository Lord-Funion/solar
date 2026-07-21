package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.solar.launcher.deezer.DeezerAccount;
import com.solar.launcher.deezer.DeezerClient;
import com.solar.launcher.deezer.DeezerDownloader;
import com.solar.launcher.deezer.DeezerResult;
import com.solar.launcher.deezer.DeezerTrackData;
import com.solar.launcher.stem.LalalAccount;
import com.solar.launcher.stem.LalalClient;
import com.solar.launcher.youtube.YouTubeDownloader;
import com.solar.launcher.youtube.YouTubeVideo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2026-07-21 — Async Background Job Manager for Web Portal Stem Preparer & Media Downloads.
 * Layman: handles downloading songs from Deezer/YouTube and splitting them into stems in the background
 * while reporting live progress to the PC web browser.
 */
public final class SolarStemJobManager {

    public static class JobInfo {
        public final String id;
        public final String title;
        public final String artist;
        public final String source; // "deezer", "youtube", "library"
        public volatile String phase; // "downloading", "separating", "ready", "error"
        public volatile int percent;
        public volatile String detail;
        public volatile String error;
        public volatile long doneBytes;
        public volatile long totalBytes;
        public volatile String targetPath;

        public JobInfo(String id, String title, String artist, String source) {
            this.id = id != null ? id : UUID.randomUUID().toString();
            this.title = title != null ? title : "";
            this.artist = artist != null ? artist : "";
            this.source = source != null ? source : "library";
            this.phase = "starting";
            this.percent = 0;
            this.detail = "Initializing job...";
            this.error = "";
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", id);
                obj.put("title", title);
                obj.put("artist", artist);
                obj.put("source", source);
                obj.put("phase", phase != null ? phase : "");
                obj.put("percent", percent);
                obj.put("detail", detail != null ? detail : "");
                obj.put("error", error != null ? error : "");
                obj.put("done_bytes", doneBytes);
                obj.put("total_bytes", totalBytes);
                obj.put("target_path", targetPath != null ? targetPath : "");
            } catch (Exception ignored) {}
            return obj;
        }
    }

    private static volatile SolarStemStemJobManagerInstanceHolder sHolder = new SolarStemStemJobManagerInstanceHolder();

    private static class SolarStemStemJobManagerInstanceHolder {
        private final ConcurrentHashMap<String, JobInfo> jobsMap = new ConcurrentHashMap<String, JobInfo>();
        private final List<String> orderedJobIds = Collections.synchronizedList(new ArrayList<String>());
        private final ExecutorService executor = Executors.newCachedThreadPool();
    }

    public static SolarStemJobManager getInstance() {
        return new SolarStemJobManager();
    }

    public JobInfo getJob(String id) {
        if (id == null) return null;
        return sHolder.jobsMap.get(id);
    }

    public List<JobInfo> getAllJobs() {
        List<JobInfo> list = new ArrayList<JobInfo>();
        synchronized (sHolder.orderedJobIds) {
            for (int i = sHolder.orderedJobIds.size() - 1; i >= 0; i--) {
                String jid = sHolder.orderedJobIds.get(i);
                JobInfo job = sHolder.jobsMap.get(jid);
                if (job != null) list.add(job);
            }
        }
        return list;
    }

    public String getJobsJsonString() {
        JSONObject root = new JSONObject();
        JSONArray arr = new JSONArray();
        List<JobInfo> list = getAllJobs();
        for (JobInfo job : list) {
            if (job != null) arr.put(job.toJson());
        }
        try {
            root.put("jobs", arr);
        } catch (Exception ignored) {}
        return root.toString();
    }

    private JobInfo registerJob(String title, String artist, String source) {
        String id = "job_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
        JobInfo job = new JobInfo(id, title, artist, source);
        sHolder.jobsMap.put(id, job);
        synchronized (sHolder.orderedJobIds) {
            sHolder.orderedJobIds.add(id);
            if (sHolder.orderedJobIds.size() > 50) {
                String oldId = sHolder.orderedJobIds.remove(0);
                sHolder.jobsMap.remove(oldId);
            }
        }
        return job;
    }

    /**
     * Start background stem separation on an existing track file on the device (`userStemsDir`).
     */
    public String startDeviceStemPreparation(final Context ctx, final File trackFile) {
        if (trackFile == null || !trackFile.isFile()) {
            return null;
        }
        final String title = trackFile.getName();
        final JobInfo job = registerJob(title, "", "library");
        job.targetPath = trackFile.getAbsolutePath();
        job.phase = "separating";
        job.percent = 1;
        job.detail = "Checking credentials and preparing stem target...";

        sHolder.executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = ctx.getSharedPreferences(LalalAccount.PREFS_NAME, Context.MODE_PRIVATE);
                    String key = LalalAccount.effectiveKey(prefs);
                    if (key == null || key.length() < 8) {
                        job.phase = "error";
                        job.error = "No valid Lalal.ai API key or demo key configured on player.";
                        job.detail = "Stem separation failed: missing API key.";
                        return;
                    }

                    LalalClient client = new LalalClient(key);
                    File stemsDir = LalalClient.userStemsDir(trackFile);
                    if (stemsDir == null) {
                        job.phase = "error";
                        job.error = "Could not resolve stem directory path for track.";
                        job.detail = "Stem separation failed.";
                        return;
                    }

                    job.detail = "Connecting to Lalal.ai neural network...";
                    LalalClient.Progress progress = new LalalClient.Progress() {
                        @Override
                        public void onProgress(String phase, int percent, String detail) {
                            job.phase = phase != null && phase.length() > 0 ? phase : "separating";
                            job.percent = percent;
                            if (detail != null && detail.length() > 0) {
                                job.detail = detail;
                            } else {
                                job.detail = "Separating stems (" + percent + "%)...";
                            }
                        }
                    };

                    List<LalalClient.StemFile> result = client.separateToMp3(trackFile, stemsDir, progress);
                    if (result != null && result.size() >= 4) {
                        LalalClient.writeTrackMarkerIfOwned(stemsDir, trackFile);
                        job.phase = "ready";
                        job.percent = 100;
                        job.detail = "✅ All stems separated and ready for Now Playing!";
                    } else {
                        job.phase = "error";
                        job.error = "Stem separation returned incomplete files.";
                        job.detail = "Stem processing failed.";
                    }
                } catch (Exception e) {
                    job.phase = "error";
                    job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    job.detail = "Stem processing error: " + job.error;
                }
            }
        });

        return job.id;
    }

    /**
     * Download track from Deezer right into `Music/Deezer/` and optionally separate stems immediately after.
     */
    public String startDeezerDownloadAndPrepare(final Context ctx, final long trackId, final String title,
            final String artist, final boolean autoStems) {
        final JobInfo job = registerJob(title, artist, "deezer");
        job.phase = "downloading";
        job.percent = 0;
        job.detail = "Initializing Deezer download...";

        sHolder.executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences prefs = ctx.getSharedPreferences(DeezerAccount.PREFS_NAME, Context.MODE_PRIVATE);
                    DeezerClient client = new DeezerClient(prefs);
                    if (!client.isSessionValid()) {
                        client.initSession();
                    }

                    File destDir = new File(DeviceFeatures.getNewMediaRoot(ctx), "Music/Deezer");
                    if (!destDir.exists()) destDir.mkdirs();

                    DeezerResult result = new DeezerResult(trackId, title, artist, "", 0, 0, "", "");
                    DeezerDownloader downloader = new DeezerDownloader(client);

                    downloader.download(result, destDir, "mp3", new DeezerDownloader.Listener() {
                        @Override
                        public void onProgress(long done, long total) {
                            job.doneBytes = done;
                            job.totalBytes = total;
                            if (total > 0) {
                                job.percent = (int) (done * 100 / total);
                                job.detail = "Downloading Deezer track (" + job.percent + "%)...";
                            } else {
                                job.detail = "Downloading Deezer track (" + (done / 1024) + " KB)...";
                            }
                        }

                        @Override
                        public void onPartialReady(File dest, long bytesRead) {
                            if (dest != null) job.targetPath = dest.getAbsolutePath();
                        }

                        @Override
                        public void onComplete(File dest, DeezerTrackData track) {
                            if (dest != null) job.targetPath = dest.getAbsolutePath();
                            if (autoStems && dest != null && dest.isFile()) {
                                job.phase = "separating";
                                job.percent = 1;
                                job.detail = "Download complete. Starting stem separation...";
                                runStemPreparationSync(ctx, dest, job);
                            } else {
                                job.phase = "ready";
                                job.percent = 100;
                                job.detail = "✅ Deezer track downloaded to Music/Deezer!";
                            }
                        }

                        @Override
                        public void onError(String message) {
                            job.phase = "error";
                            job.error = message != null ? message : "Deezer download error";
                            job.detail = "Download failed: " + job.error;
                        }
                    });
                } catch (Exception e) {
                    job.phase = "error";
                    job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    job.detail = "Deezer setup failed: " + job.error;
                }
            }
        });

        return job.id;
    }

    /**
     * Download track from YouTube right into `Music/YouTube/` and optionally separate stems immediately after.
     */
    public String startYouTubeDownloadAndPrepare(final Context ctx, final String videoId, final String title,
            final String author, final boolean autoStems) {
        final JobInfo job = registerJob(title, author, "youtube");
        job.phase = "downloading";
        job.percent = 0;
        job.detail = "Resolving YouTube audio stream...";

        sHolder.executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    YouTubeVideo video = new YouTubeVideo(videoId, title, author, "");
                    YouTubeDownloader.saveAudio(ctx, video, new YouTubeDownloader.Callback() {
                        @Override
                        public void onProgress(String phase, int percent, long doneBytes, long totalBytes) {
                            job.doneBytes = doneBytes;
                            job.totalBytes = totalBytes;
                            if (percent >= 0 && percent <= 100) {
                                job.percent = percent;
                            }
                            if ("resolve".equalsIgnoreCase(phase)) {
                                job.detail = "Resolving stream URLs from backend...";
                            } else {
                                job.detail = "Downloading YouTube audio (" + job.percent + "%)...";
                            }
                        }

                        @Override
                        public void onComplete(File savedFile) {
                            if (savedFile != null) job.targetPath = savedFile.getAbsolutePath();
                            if (autoStems && savedFile != null && savedFile.isFile()) {
                                job.phase = "separating";
                                job.percent = 1;
                                job.detail = "Audio download complete. Starting stem separation...";
                                runStemPreparationSync(ctx, savedFile, job);
                            } else {
                                job.phase = "ready";
                                job.percent = 100;
                                job.detail = "✅ YouTube audio downloaded to Music/YouTube!";
                            }
                        }

                        @Override
                        public void onError(String message) {
                            job.phase = "error";
                            job.error = message != null ? message : "YouTube download error";
                            job.detail = "Download failed: " + job.error;
                        }
                    });
                } catch (Exception e) {
                    job.phase = "error";
                    job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    job.detail = "YouTube error: " + job.error;
                }
            }
        });

        return job.id;
    }

    private void runStemPreparationSync(final Context ctx, final File trackFile, final JobInfo job) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(LalalAccount.PREFS_NAME, Context.MODE_PRIVATE);
            String key = LalalAccount.effectiveKey(prefs);
            if (key == null || key.length() < 8) {
                job.phase = "error";
                job.error = "No valid Lalal.ai API key or demo key configured.";
                job.detail = "Stem separation failed: missing API key.";
                return;
            }

            LalalClient client = new LalalClient(key);
            File stemsDir = LalalClient.userStemsDir(trackFile);
            if (stemsDir == null) {
                job.phase = "error";
                job.error = "Could not resolve stem folder path.";
                return;
            }

            LalalClient.Progress progress = new LalalClient.Progress() {
                @Override
                public void onProgress(String phase, int percent, String detail) {
                    job.phase = phase != null && phase.length() > 0 ? phase : "separating";
                    job.percent = percent;
                    if (detail != null && detail.length() > 0) {
                        job.detail = detail;
                    } else {
                        job.detail = "Separating stems (" + percent + "%)...";
                    }
                }
            };

            List<LalalClient.StemFile> result = client.separateToMp3(trackFile, stemsDir, progress);
            if (result != null && result.size() >= 4) {
                LalalClient.writeTrackMarkerIfOwned(stemsDir, trackFile);
                job.phase = "ready";
                job.percent = 100;
                job.detail = "✅ Track downloaded and all stems prepared for Now Playing!";
            } else {
                job.phase = "error";
                job.error = "Stem separation returned incomplete files.";
                job.detail = "Stem processing failed.";
            }
        } catch (Exception e) {
            job.phase = "error";
            job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            job.detail = "Stem processing error: " + job.error;
        }
    }
}
