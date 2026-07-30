package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

final class DownloadEngine {
    interface Listener { void onDownloadsChanged(); }

    static final class Job {
        final int id;
        final String title;
        final String url;
        final File destination;
        volatile long downloaded;
        volatile long total;
        volatile String state = "Queued";
        volatile String error = "";

        Job(int id, String title, String url, File destination) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.destination = destination;
        }

        String summary() {
            if ("Downloading".equals(state) && total > 0) {
                return String.format(Locale.US, "%s — %d%%", title, (downloaded * 100L) / total);
            }
            if (error.length() > 0) return title + " — " + state + ": " + error;
            return title + " — " + state;
        }
    }

    private final Context context;
    private final Listener listener;
    private final OkHttpClient client;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AtomicInteger ids = new AtomicInteger(1);
    private final List<Job> jobs = Collections.synchronizedList(new ArrayList<Job>());

    DownloadEngine(Context context, Listener listener, OkHttpClient client) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.client = client;
    }

    OkHttpClient client() { return client; }

    Job enqueue(String url, String preferredName) {
        File directory = new File(new File(Environment.getExternalStorageDirectory(), "Music"), "RockboxSolar");
        if (!directory.exists()) {
            //noinspection ResultOfMethodCallIgnored
            directory.mkdirs();
        }
        String filename = sanitize(preferredName);
        if (filename.indexOf('.') < 0) filename += extensionFromUrl(url);
        File destination = uniqueFile(directory, filename);
        final Job job = new Job(ids.getAndIncrement(), preferredName, url, destination);
        jobs.add(0, job);
        listener.onDownloadsChanged();
        executor.execute(new Runnable() {
            @Override public void run() { run(job); }
        });
        return job;
    }

    List<Job> jobs() {
        synchronized (jobs) { return new ArrayList<Job>(jobs); }
    }

    void shutdown() { executor.shutdownNow(); }

    private void run(Job job) {
        File part = new File(job.destination.getAbsolutePath() + ".part");
        long existing = part.exists() ? part.length() : 0L;
        Request.Builder requestBuilder = new Request.Builder().url(job.url)
                .header("User-Agent", "RockboxSolar/0.1 (authorized download client)");
        if (existing > 0) requestBuilder.header("Range", "bytes=" + existing + "-");
        job.state = "Downloading";
        listener.onDownloadsChanged();
        try {
            Response response = client.newCall(requestBuilder.build()).execute();
            if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IllegalStateException("Empty response");
            boolean append = existing > 0 && response.code() == 206;
            if (!append) existing = 0L;
            long contentLength = body.contentLength();
            job.total = contentLength < 0 ? 0 : existing + contentLength;
            job.downloaded = existing;
            InputStream input = body.byteStream();
            FileOutputStream output = new FileOutputStream(part, append);
            byte[] buffer = new byte[32 * 1024];
            int read;
            long lastNotify = 0L;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                job.downloaded += read;
                long now = System.currentTimeMillis();
                if (now - lastNotify > 500L) {
                    lastNotify = now;
                    listener.onDownloadsChanged();
                }
            }
            output.flush();
            output.getFD().sync();
            output.close();
            input.close();
            response.close();
            if (job.destination.exists() && !job.destination.delete()) {
                throw new IllegalStateException("Cannot replace destination");
            }
            if (!part.renameTo(job.destination)) throw new IllegalStateException("Final rename failed");
            job.state = "Complete";
            Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scan.setData(Uri.fromFile(job.destination));
            context.sendBroadcast(scan);
        } catch (Exception e) {
            job.state = "Failed";
            job.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        listener.onDownloadsChanged();
    }

    private static String sanitize(String name) {
        String clean = name == null ? "download" : name.trim();
        clean = clean.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ");
        if (clean.length() == 0) clean = "download";
        if (clean.length() > 100) clean = clean.substring(0, 100);
        return clean;
    }

    private static String extensionFromUrl(String url) {
        try {
            String path = Uri.parse(url).getLastPathSegment();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && path.length() - dot <= 6) return path.substring(dot);
            }
        } catch (Exception ignored) { }
        return ".bin";
    }

    private static File uniqueFile(File directory, String filename) {
        File file = new File(directory, filename);
        if (!file.exists()) return file;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            file = new File(directory, base + " (" + i + ")" + extension);
            if (!file.exists()) return file;
        }
        return new File(directory, System.currentTimeMillis() + "-" + filename);
    }
}
