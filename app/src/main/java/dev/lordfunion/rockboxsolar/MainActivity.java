package dev.lordfunion.rockboxsolar;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import org.conscrypt.Conscrypt;

import java.io.File;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class MainActivity extends Activity implements PlayerController.Listener, DownloadEngine.Listener {
    private enum Screen { HOME, FILES, LIBRARY, QUEUE, PODCASTS, GET_MUSIC, DOWNLOADS, THEMES, SETTINGS, ABOUT, NOW_PLAYING }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ThemeManager themeManager;
    private PlayerController player;
    private DownloadEngine downloads;
    private RockboxView view;
    private Screen screen = Screen.HOME;
    private int selection = 0;
    private File currentDirectory;
    private List<File> files = new ArrayList<File>();
    private List<File> library = new ArrayList<File>();
    private List<PodcastFeed.Episode> episodes = new ArrayList<PodcastFeed.Episode>();
    private String status = "Ready";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        try { Security.insertProviderAt(Conscrypt.newProvider(), 1); } catch (Throwable ignored) { }
        themeManager = new ThemeManager(this);
        player = new PlayerController(this, this);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
        downloads = new DownloadEngine(this, this, client);
        currentDirectory = chooseStartDirectory();
        files = LibraryScanner.listDirectory(currentDirectory);
        view = new RockboxView(this);
        setContentView(view);
        requestStoragePermission();
        tick();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 40);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 40) {
            currentDirectory = chooseStartDirectory();
            files = LibraryScanner.listDirectory(currentDirectory);
            view.invalidate();
        }
    }

    private File chooseStartDirectory() {
        List<File> roots = LibraryScanner.roots();
        if (!roots.isEmpty()) return roots.get(0);
        return Environment.getExternalStorageDirectory();
    }

    private void tick() {
        if (view == null) return;
        view.invalidate();
        view.postDelayed(new Runnable() { @Override public void run() { tick(); } }, 750L);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_MENU) {
            showQuickMenu();
            return true;
        }
        if (key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || key == KeyEvent.KEYCODE_HEADSETHOOK) {
            player.toggle();
            return true;
        }
        if (key == KeyEvent.KEYCODE_MEDIA_NEXT) {
            player.next();
            return true;
        }
        if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            player.previous();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_PAGE_UP) {
            move(-1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_DOWN || key == KeyEvent.KEYCODE_PAGE_DOWN) {
            move(1);
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (screen == Screen.NOW_PLAYING) player.seekRelative(-10000); else back();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (screen == Screen.NOW_PLAYING) player.seekRelative(10000); else activate();
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_BUTTON_A) {
            activate();
            return true;
        }
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_BUTTON_B) {
            back();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void move(int delta) {
        int count = currentLabels().size();
        if (count <= 0) return;
        selection = (selection + delta + count) % count;
        view.invalidate();
    }

    private void activate() {
        List<String> labels = currentLabels();
        if (labels.isEmpty()) return;
        selection = Math.max(0, Math.min(selection, labels.size() - 1));
        switch (screen) {
            case HOME: activateHome(selection); break;
            case FILES: activateFile(selection); break;
            case LIBRARY: activateLibrary(selection); break;
            case QUEUE: activateQueue(selection); break;
            case PODCASTS: activatePodcast(selection); break;
            case GET_MUSIC: activateGetMusic(selection); break;
            case THEMES: activateTheme(selection); break;
            case SETTINGS: activateSettings(selection); break;
            case DOWNLOADS: break;
            case NOW_PLAYING: player.toggle(); break;
            case ABOUT: back(); break;
        }
        view.invalidate();
    }

    private void activateHome(int index) {
        switch (index) {
            case 0: switchScreen(Screen.NOW_PLAYING); break;
            case 1: files = LibraryScanner.listDirectory(currentDirectory); switchScreen(Screen.FILES); break;
            case 2: scanLibrary(); break;
            case 3: switchScreen(Screen.QUEUE); break;
            case 4: promptPodcastFeed(); break;
            case 5: switchScreen(Screen.GET_MUSIC); break;
            case 6: switchScreen(Screen.DOWNLOADS); break;
            case 7: themeManager.reload(); switchScreen(Screen.THEMES); break;
            case 8: switchScreen(Screen.SETTINGS); break;
            case 9: switchScreen(Screen.ABOUT); break;
        }
    }

    private void activateFile(int index) {
        if (index < 0 || index >= files.size()) return;
        File chosen = files.get(index);
        if (chosen.isDirectory()) {
            currentDirectory = chosen;
            files = LibraryScanner.listDirectory(currentDirectory);
            selection = 0;
        } else {
            ArrayList<File> playable = new ArrayList<File>();
            for (File file : files) if (LibraryScanner.isAudio(file)) playable.add(file);
            player.play(chosen, playable);
            switchScreen(Screen.NOW_PLAYING);
        }
    }

    private void scanLibrary() {
        status = "Scanning music…";
        switchScreen(Screen.LIBRARY);
        worker.execute(new Runnable() {
            @Override public void run() {
                final List<File> result = LibraryScanner.scanAll();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        library = result;
                        status = result.size() + " tracks";
                        selection = 0;
                        view.invalidate();
                    }
                });
            }
        });
    }

    private void activateLibrary(int index) {
        if (index < 0 || index >= library.size()) return;
        player.play(library.get(index), library);
        switchScreen(Screen.NOW_PLAYING);
    }

    private void activateQueue(int index) {
        List<File> queue = player.queue();
        if (index < 0 || index >= queue.size()) return;
        player.replaceQueue(queue, index);
        switchScreen(Screen.NOW_PLAYING);
    }

    private void promptPodcastFeed() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://example.com/feed.xml");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Podcast RSS URL").setView(input)
                .setPositiveButton("Load", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        loadPodcast(input.getText().toString().trim());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void loadPodcast(final String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Enter a full HTTP or HTTPS feed URL");
            return;
        }
        status = "Loading podcast…";
        episodes = new ArrayList<PodcastFeed.Episode>();
        switchScreen(Screen.PODCASTS);
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final List<PodcastFeed.Episode> result = PodcastFeed.fetch(downloads.client(), url);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            episodes = result;
                            status = result.size() + " episodes";
                            selection = 0;
                            view.invalidate();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { status = "Feed failed: " + safeMessage(e); view.invalidate(); }
                    });
                }
            }
        });
    }

    private void activatePodcast(int index) {
        if (index < 0 || index >= episodes.size()) return;
        PodcastFeed.Episode episode = episodes.get(index);
        downloads.enqueue(episode.url, episode.title + extensionForMime(episode.mime));
        toast("Podcast download queued");
        switchScreen(Screen.DOWNLOADS);
    }

    private void activateGetMusic(int index) {
        if (index == 0) promptAuthorizedDownload();
        else if (index == 1) openSolar("reach");
        else if (index == 2) openSolar("deezer");
        else if (index == 3) openSolar("soulseek");
        else if (index == 4) promptPodcastFeed();
    }

    private void promptAuthorizedDownload() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Direct audio URL you are allowed to download");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Authorized direct download").setMessage("Use only a direct file URL you own or have permission to save.")
                .setView(input).setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        promptDownloadName(input.getText().toString().trim());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptDownloadName(final String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("Enter a full HTTP or HTTPS URL");
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Filename or track title");
        new AlertDialog.Builder(this).setTitle("Save as").setView(input)
                .setPositiveButton("Download", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.length() == 0) name = "download";
                        downloads.enqueue(url, name);
                        switchScreen(Screen.DOWNLOADS);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void activateTheme(int index) {
        List<Theme> themes = themeManager.all();
        if (index < 0 || index >= themes.size()) return;
        themeManager.activate(themes.get(index));
        toast("Theme: " + themes.get(index).name);
        view.invalidate();
    }

    private void activateSettings(int index) {
        try {
            switch (index) {
                case 0: startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
                case 1: startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
                case 2: startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
                case 3: scanLibrary(); break;
                case 4: openSolar("home"); break;
                case 5: themeManager.reload(); toast("Themes reloaded"); break;
                case 6: finish(); break;
            }
        } catch (Exception e) {
            toast("Setting unavailable on this firmware");
        }
    }

    private void openSolar(String feature) {
        if (!SolarBridge.open(this, feature)) {
            toast("Solar is not installed. Install Solar separately to use Reach/Deezer/Soulseek.");
        }
    }

    private void showQuickMenu() {
        final String[] items = { player.isPlaying() ? "Pause" : "Play", "Previous", "Next", "Seek back 10s", "Seek forward 10s", "Wi-Fi", "Bluetooth", "Open Solar" };
        new AlertDialog.Builder(this).setTitle("Quick Menu").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                try {
                    switch (which) {
                        case 0: player.toggle(); break;
                        case 1: player.previous(); break;
                        case 2: player.next(); break;
                        case 3: player.seekRelative(-10000); break;
                        case 4: player.seekRelative(10000); break;
                        case 5: startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
                        case 6: startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); break;
                        case 7: openSolar("home"); break;
                    }
                } catch (Exception e) { toast("Action unavailable"); }
            }
        }).show();
    }

    private void back() {
        if (screen == Screen.HOME) {
            moveTaskToBack(true);
            return;
        }
        if (screen == Screen.FILES && currentDirectory != null && currentDirectory.getParentFile() != null) {
            File start = chooseStartDirectory();
            if (!sameFile(currentDirectory, start)) {
                currentDirectory = currentDirectory.getParentFile();
                files = LibraryScanner.listDirectory(currentDirectory);
                selection = 0;
                view.invalidate();
                return;
            }
        }
        switchScreen(Screen.HOME);
    }

    private void switchScreen(Screen next) {
        screen = next;
        selection = 0;
        view.invalidate();
    }

    private List<String> currentLabels() {
        ArrayList<String> labels = new ArrayList<String>();
        switch (screen) {
            case HOME:
                return Arrays.asList("Now Playing", "Files", "Database", "Current Playlist", "Podcasts", "Get Music", "Downloads", "Themes", "Settings", "About");
            case FILES:
                for (File file : files) labels.add((file.isDirectory() ? "[DIR] " : "") + file.getName());
                return labels;
            case LIBRARY:
                for (File file : library) labels.add(file.getName());
                return labels;
            case QUEUE:
                for (File file : player.queue()) labels.add(file.getName());
                return labels;
            case PODCASTS:
                for (PodcastFeed.Episode episode : episodes) labels.add(episode.title);
                return labels;
            case GET_MUSIC:
                return Arrays.asList("Authorized direct URL", "Reach (open Solar)", "Deezer (open Solar)", "Soulseek (open Solar)", "Podcast RSS");
            case DOWNLOADS:
                for (DownloadEngine.Job job : downloads.jobs()) labels.add(job.summary());
                return labels;
            case THEMES:
                for (Theme theme : themeManager.all()) labels.add((theme.name.equals(themeManager.active().name) ? "* " : "  ") + theme.name);
                return labels;
            case SETTINGS:
                return Arrays.asList("Wi-Fi", "Bluetooth", "Sound", "Rescan database", "Open Solar", "Reload themes", "Exit launcher");
            case ABOUT:
                return Arrays.asList("Rockbox Solar 0.1", "Clean-room Android shell", "Local playback + themes + podcasts", "Solar bridge for Reach services", "Not affiliated with Rockbox or Deezer", "Press Select or Back");
            case NOW_PLAYING:
                return Arrays.asList(player.isPlaying() ? "Pause" : "Play");
            default:
                return labels;
        }
    }

    private String titleForScreen() {
        switch (screen) {
            case HOME: return "Rockbox Solar";
            case FILES: return currentDirectory == null ? "Files" : currentDirectory.getAbsolutePath();
            case LIBRARY: return "Database — " + status;
            case QUEUE: return "Current Playlist";
            case PODCASTS: return "Podcasts — " + status;
            case GET_MUSIC: return "Get Music";
            case DOWNLOADS: return "Downloads";
            case THEMES: return "Themes — " + themeManager.themeDirectory().getAbsolutePath();
            case SETTINGS: return "Settings";
            case ABOUT: return "About";
            case NOW_PLAYING: return "Now Playing";
            default: return "Rockbox Solar";
        }
    }

    @Override public void onPlayerChanged() { if (view != null) view.postInvalidate(); }
    @Override public void onDownloadsChanged() { if (view != null) view.postInvalidate(); }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
        downloads.shutdown();
        player.release();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }

    private static boolean sameFile(File a, File b) {
        try { return a.getCanonicalPath().equals(b.getCanonicalPath()); }
        catch (Exception e) { return a.getAbsolutePath().equals(b.getAbsolutePath()); }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? e.getClass().getSimpleName() : message;
    }

    private static String extensionForMime(String mime) {
        if (mime == null) return ".mp3";
        String lower = mime.toLowerCase(Locale.US);
        if (lower.contains("mp4") || lower.contains("m4a")) return ".m4a";
        if (lower.contains("ogg")) return ".ogg";
        if (lower.contains("flac")) return ".flac";
        return ".mp3";
    }

    private final class RockboxView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        RockboxView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Theme theme = themeManager.active();
            canvas.drawColor(theme.background);
            float width = getWidth();
            float height = getHeight();
            float scale = Math.max(0.8f, Math.min(1.35f, height / 480f)) * theme.fontScale;
            float header = 52f * scale;
            float footer = 31f * scale;
            paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
            paint.setTextSize(24f * scale);
            paint.setColor(theme.accent);
            paint.setFakeBoldText(true);
            canvas.drawText(ellipsize(titleForScreen(), paint, width - 24f * scale), 12f * scale, 34f * scale, paint);
            paint.setFakeBoldText(false);
            paint.setStrokeWidth(1f);
            canvas.drawLine(0, header - 1, width, header - 1, paint);

            if (screen == Screen.NOW_PLAYING) {
                drawNowPlaying(canvas, theme, scale, header, footer);
            } else {
                drawList(canvas, theme, scale, header, footer);
            }

            paint.setColor(theme.muted);
            paint.setTextSize(14f * scale);
            String footerText = "↑↓ move  Select open  Back return  Menu quick controls";
            canvas.drawText(ellipsize(footerText, paint, width - 20f * scale), 10f * scale, height - 9f * scale, paint);
        }

        private void drawList(Canvas canvas, Theme theme, float scale, float header, float footer) {
            List<String> labels = currentLabels();
            float rowHeight = 34f * scale;
            int visible = Math.max(1, (int) ((getHeight() - header - footer) / rowHeight));
            int start = Math.max(0, selection - visible / 2);
            if (start + visible > labels.size()) start = Math.max(0, labels.size() - visible);
            paint.setTextSize(20f * scale);
            for (int row = 0; row < visible && start + row < labels.size(); row++) {
                int index = start + row;
                float top = header + row * rowHeight;
                if (index == selection) {
                    paint.setColor(theme.selected);
                    rect.set(5f * scale, top + 2f, getWidth() - 5f * scale, top + rowHeight - 2f);
                    canvas.drawRoundRect(rect, 5f * scale, 5f * scale, paint);
                    paint.setColor(theme.accent);
                    paint.setFakeBoldText(true);
                    canvas.drawText(">", 11f * scale, top + 25f * scale, paint);
                } else {
                    paint.setColor(theme.foreground);
                    paint.setFakeBoldText(false);
                }
                canvas.drawText(ellipsize(labels.get(index), paint, getWidth() - 50f * scale), 34f * scale, top + 25f * scale, paint);
            }
            paint.setFakeBoldText(false);
            if (labels.isEmpty()) {
                paint.setColor(theme.muted);
                canvas.drawText(status.length() == 0 ? "No items" : status, 16f * scale, header + 36f * scale, paint);
            }
            if (!labels.isEmpty()) {
                paint.setColor(theme.muted);
                paint.setTextSize(13f * scale);
                String position = (selection + 1) + "/" + labels.size();
                canvas.drawText(position, getWidth() - paint.measureText(position) - 10f * scale, header - 12f * scale, paint);
            }
        }

        private void drawNowPlaying(Canvas canvas, Theme theme, float scale, float header, float footer) {
            float width = getWidth();
            float height = getHeight();
            paint.setColor(theme.foreground);
            paint.setTextSize(31f * scale);
            paint.setFakeBoldText(true);
            canvas.drawText(ellipsize(player.title(), paint, width - 40f * scale), 20f * scale, header + 60f * scale, paint);
            paint.setFakeBoldText(false);
            paint.setTextSize(21f * scale);
            paint.setColor(theme.accent);
            String artist = player.artist().length() == 0 ? "Unknown artist" : player.artist();
            canvas.drawText(ellipsize(artist, paint, width - 40f * scale), 20f * scale, header + 98f * scale, paint);
            paint.setColor(theme.muted);
            paint.setTextSize(17f * scale);
            String album = player.album().length() == 0 ? "" : player.album();
            canvas.drawText(ellipsize(album, paint, width - 40f * scale), 20f * scale, header + 128f * scale, paint);

            int duration = player.duration();
            int position = player.position();
            float barLeft = 20f * scale;
            float barRight = width - 20f * scale;
            float barTop = height - footer - 78f * scale;
            paint.setColor(theme.selected);
            rect.set(barLeft, barTop, barRight, barTop + 14f * scale);
            canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
            if (duration > 0) {
                paint.setColor(theme.accent);
                rect.set(barLeft, barTop, barLeft + (barRight - barLeft) * Math.min(1f, position / (float) duration), barTop + 14f * scale);
                canvas.drawRoundRect(rect, 7f * scale, 7f * scale, paint);
            }
            paint.setColor(theme.foreground);
            paint.setTextSize(16f * scale);
            canvas.drawText(formatTime(position), barLeft, barTop + 36f * scale, paint);
            String right = formatTime(duration);
            canvas.drawText(right, barRight - paint.measureText(right), barTop + 36f * scale, paint);
            paint.setTextSize(19f * scale);
            paint.setColor(theme.accent);
            String state = player.isPlaying() ? "▶ Playing" : "Ⅱ Paused";
            canvas.drawText(state, width / 2f - paint.measureText(state) / 2f, barTop + 36f * scale, paint);
        }

        private String ellipsize(String text, Paint paint, float maxWidth) {
            if (text == null) return "";
            if (paint.measureText(text) <= maxWidth) return text;
            String suffix = "…";
            int end = text.length();
            while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) end--;
            return end <= 0 ? suffix : text.substring(0, end) + suffix;
        }

        private String formatTime(int milliseconds) {
            int total = Math.max(0, milliseconds / 1000);
            return String.format(Locale.US, "%d:%02d", total / 60, total % 60);
        }
    }
}
