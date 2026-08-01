package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.conscrypt.Conscrypt;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class YouTubeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<YouTubeSearch.Result> results = new ArrayList<YouTubeSearch.Result>();
    private OkHttpClient client;
    private EditText queryView;
    private TextView statusView;
    private ListView listView;
    private String nextPageToken = "";
    private String currentQuery = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try { Security.insertProviderAt(Conscrypt.newProvider(), 1); } catch (Throwable ignored) { }
        client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
        buildUi();
        if (apiKey().length() == 0) promptApiKey(true);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("YouTube Metadata Search");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        queryView = new EditText(this);
        queryView.setSingleLine(true);
        queryView.setHint("Search titles, channels, and videos");
        queryView.setInputType(InputType.TYPE_CLASS_TEXT);
        Button search = new Button(this);
        search.setText("Search");
        search.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startSearch(false); }
        });
        Button key = new Button(this);
        key.setText("API Key");
        key.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { promptApiKey(false); }
        });
        searchBar.addView(queryView, new LinearLayout.LayoutParams(0, -2, 1f));
        searchBar.addView(search, new LinearLayout.LayoutParams(-2, -2));
        searchBar.addView(key, new LinearLayout.LayoutParams(-2, -2));
        root.addView(searchBar, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("Public metadata only. No video or audio is downloaded here.");
        statusView.setPadding(0, dp(4), 0, dp(5));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < results.size()) showResultActions(results.get(position));
                else if (nextPageToken.length() > 0) startSearch(true);
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        refreshList();
    }

    private void startSearch(final boolean append) {
        final String key = apiKey();
        if (key.length() == 0) { promptApiKey(true); return; }
        final String query = append ? currentQuery : queryView.getText().toString().trim();
        if (query.length() == 0) { toast("Enter a YouTube search query"); return; }
        final String token = append ? nextPageToken : "";
        statusView.setText(append ? "Loading more results…" : "Searching YouTube metadata…");
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    final YouTubeSearch.Page page = YouTubeSearch.search(client, key, query, token);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!append) results.clear();
                            results.addAll(page.results);
                            nextPageToken = page.nextPageToken;
                            currentQuery = query;
                            statusView.setText(results.size() + " metadata results");
                            refreshList();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { statusView.setText("YouTube search failed: " + safeMessage(e)); }
                    });
                }
            }
        });
    }

    private void refreshList() {
        ArrayList<String> labels = new ArrayList<String>();
        for (YouTubeSearch.Result result : results) labels.add(result.summary());
        if (nextPageToken.length() > 0) labels.add("Load more results…");
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels));
    }

    private void showResultActions(final YouTubeSearch.Result result) {
        final String[] actions = {"Copy video URL", "Open on YouTube", "Run SSH command with URL", "Show metadata"};
        new AlertDialog.Builder(this).setTitle(result.title).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) copyUrl(result.url());
                else if (which == 1) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(result.url()))); }
                    catch (Exception e) { copyUrl(result.url()); }
                } else if (which == 2) {
                    Intent intent = new Intent(YouTubeActivity.this, SshActivity.class);
                    intent.putExtra(SshActivity.EXTRA_URL, result.url());
                    intent.putExtra(SshActivity.EXTRA_VIDEO_ID, result.videoId);
                    intent.putExtra(SshActivity.EXTRA_TITLE, result.title);
                    intent.putExtra(SshActivity.EXTRA_CHANNEL, result.channel);
                    startActivity(intent);
                } else {
                    showMetadata(result);
                }
            }
        }).show();
    }

    private void showMetadata(final YouTubeSearch.Result result) {
        String text = "Title: " + result.title + "\nChannel: " + result.channel +
                "\nChannel ID: " + result.channelId + "\nVideo ID: " + result.videoId +
                "\nURL: " + result.url() + "\nDuration: " + result.duration +
                "\nViews: " + result.views + "\nPublished: " + result.publishedAt +
                "\nThumbnail: " + result.thumbnailUrl + "\n\n" + result.description;
        new AlertDialog.Builder(this).setTitle("YouTube metadata").setMessage(text)
                .setPositiveButton("Copy URL", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { copyUrl(result.url()); }
                }).setNegativeButton("Close", null).show();
    }

    private void copyUrl(String url) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("YouTube URL", url));
        toast("YouTube URL copied");
    }

    private void promptApiKey(boolean required) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("YouTube Data API v3 key");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(apiKey());
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("YouTube Data API key")
                .setMessage("Used only for public metadata search. The key is stored locally on this player.")
                .setView(input).setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        getSharedPreferences("rockbox_solar_youtube", MODE_PRIVATE).edit().putString("api_key", value).apply();
                        statusView.setText(value.length() == 0 ? "No YouTube API key configured" : "YouTube API key saved locally");
                    }
                });
        if (!required) builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private String apiKey() {
        return getSharedPreferences("rockbox_solar_youtube", MODE_PRIVATE).getString("api_key", "").trim();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? e.getClass().getSimpleName() : message;
    }
}
