package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SshActivity extends Activity {
    static final String EXTRA_URL = "youtube_url";
    static final String EXTRA_VIDEO_ID = "youtube_video_id";
    static final String EXTRA_TITLE = "youtube_title";
    static final String EXTRA_CHANNEL = "youtube_channel";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SshProfileStore profileStore;
    private SshManager sshManager;
    private TextView statusView;
    private ListView listView;
    private ScrollView outputScroll;
    private TextView outputView;
    private boolean showingOutput;
    private List<SshProfile> profiles = new ArrayList<SshProfile>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        profileStore = new SshProfileStore(this);
        sshManager = new SshManager(this);
        buildUi();
        refreshProfiles();
        handleYouTubeIntent();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Remote SSH + SCP");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        statusView = new TextView(this);
        statusView.setText("Select a saved host or add one.");
        statusView.setTextSize(15f);
        statusView.setPadding(0, dp(4), 0, dp(8));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) editProfile(null);
                else if (position - 1 < profiles.size()) showProfileActions(profiles.get(position - 1));
                else showCommandHistory();
            }
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1f));

        outputView = new TextView(this);
        outputView.setTextSize(14f);
        outputView.setTypeface(Typeface.MONOSPACE);
        outputView.setTextIsSelectable(true);
        outputView.setPadding(dp(8), dp(8), dp(8), dp(8));
        outputScroll = new ScrollView(this);
        outputScroll.addView(outputView, new ScrollView.LayoutParams(-1, -2));
        outputScroll.setVisibility(View.GONE);
        root.addView(outputScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        setContentView(root);
    }

    private void refreshProfiles() {
        profiles = profileStore.all();
        ArrayList<String> labels = new ArrayList<String>();
        labels.add("＋ Add SSH host");
        for (SshProfile profile : profiles) labels.add(profile.displayName());
        labels.add("Command history");
        listView.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, labels));
        listView.setSelection(0);
    }

    private void handleYouTubeIntent() {
        final String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.length() == 0) return;
        if (profiles.isEmpty()) {
            toast("Add an SSH host first, then return to the YouTube result.");
            editProfile(null);
            return;
        }
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) labels[i] = profiles.get(i).displayName();
        new AlertDialog.Builder(this).setTitle("Run YouTube URL through which host?")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        promptYouTubeCommand(profiles.get(which));
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void editProfile(final SshProfile existing) {
        final LinearLayout form = verticalForm();
        final EditText name = field("Name, e.g. Desktop", InputType.TYPE_CLASS_TEXT);
        final EditText host = field("Host or IP", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText port = field("Port", InputType.TYPE_CLASS_NUMBER);
        final EditText user = field("Username", InputType.TYPE_CLASS_TEXT);
        final Spinner auth = new Spinner(this);
        auth.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Password", "Private key file"}));
        final EditText keyPath = field("Private key path, e.g. /sdcard/RockboxSolar/ssh/id_ed25519", InputType.TYPE_CLASS_TEXT);
        if (existing != null) {
            name.setText(existing.name);
            host.setText(existing.host);
            port.setText(Integer.toString(existing.port));
            user.setText(existing.user);
            auth.setSelection(existing.usesKey() ? 1 : 0);
            keyPath.setText(existing.keyPath);
        } else {
            port.setText("22");
        }
        form.addView(name);
        form.addView(host);
        form.addView(port);
        form.addView(user);
        form.addView(auth);
        form.addView(keyPath);
        new AlertDialog.Builder(this).setTitle(existing == null ? "Add SSH host" : "Edit SSH host")
                .setView(form).setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String h = host.getText().toString().trim();
                        String u = user.getText().toString().trim();
                        if (h.length() == 0 || u.length() == 0) {
                            toast("Host and username are required");
                            return;
                        }
                        int p = 22;
                        try { p = Integer.parseInt(port.getText().toString().trim()); }
                        catch (Exception ignored) { }
                        if (p < 1 || p > 65535) {
                            toast("Invalid SSH port");
                            return;
                        }
                        String id = existing == null ? Long.toString(System.currentTimeMillis()) : existing.id;
                        String display = name.getText().toString().trim();
                        if (display.length() == 0) display = h;
                        String mode = auth.getSelectedItemPosition() == 1 ? SshProfile.AUTH_KEY : SshProfile.AUTH_PASSWORD;
                        SshProfile profile = new SshProfile(id, display, h, p, u, mode,
                                keyPath.getText().toString().trim());
                        profileStore.save(profile);
                        refreshProfiles();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showProfileActions(final SshProfile profile) {
        final String[] actions = {"Run command", "Run command, then SCP file back", "SCP remote file to player", "Edit host", "Delete host"};
        new AlertDialog.Builder(this).setTitle(profile.displayName()).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) promptCommand(profile, false);
                else if (which == 1) promptCommand(profile, true);
                else if (which == 2) promptRemotePath(profile, "", true);
                else if (which == 3) editProfile(profile);
                else if (which == 4) confirmDelete(profile);
            }
        }).show();
    }

    private void promptCommand(final SshProfile profile, final boolean fetchAfter) {
        final EditText command = field("Command to run", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        List<String> history = profileStore.history();
        if (!history.isEmpty()) command.setText(history.get(0));
        command.setSingleLine(false);
        command.setMinLines(2);
        new AlertDialog.Builder(this).setTitle(fetchAfter ? "Command before SCP" : "SSH command")
                .setView(command).setPositiveButton(fetchAfter ? "Next" : "Run", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = command.getText().toString().trim();
                        if (value.length() == 0) {
                            toast("Enter a command");
                            return;
                        }
                        if (fetchAfter) promptRemotePath(profile, value, false);
                        else promptSecretAndExecute(profile, value, "", "");
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptRemotePath(final SshProfile profile, final String command, final boolean scpOnly) {
        final LinearLayout form = verticalForm();
        final EditText remote = field("Remote file path, e.g. /tmp/output.mp3", InputType.TYPE_CLASS_TEXT);
        final EditText localName = field("Local filename override (optional)", InputType.TYPE_CLASS_TEXT);
        form.addView(remote);
        form.addView(localName);
        new AlertDialog.Builder(this).setTitle(scpOnly ? "SCP file from host" : "File produced by command")
                .setView(form).setPositiveButton("Run", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String path = remote.getText().toString().trim();
                        if (path.length() == 0) {
                            toast("Enter the remote output path");
                            return;
                        }
                        promptSecretAndExecute(profile, command, path, localName.getText().toString().trim());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptSecretAndExecute(final SshProfile profile, final String command,
                                        final String remotePath, final String localName) {
        final EditText secret = field(profile.usesKey() ? "Private-key passphrase (blank if none)" : "SSH password",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle(profile.usesKey() ? "Key passphrase" : "SSH password")
                .setView(secret).setPositiveButton("Connect", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        execute(profile, secret.getText().toString(), command, remotePath, localName);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void execute(final SshProfile profile, final String secret, final String command,
                         final String remotePath, final String localName) {
        if (command != null && command.trim().length() > 0) profileStore.rememberCommand(command);
        showOutput();
        outputView.setText("$ " + (command == null ? "" : command) + "\n");
        statusView.setText("Starting SSH…");
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    SshManager.CombinedResult result = sshManager.runAndFetch(profile, secret, command, remotePath, localName,
                            new SshManager.Interaction() {
                                @Override public boolean confirmHostKey(String message) {
                                    return confirmHostKeyBlocking(message);
                                }

                                @Override public void onProgress(final String message) {
                                    runOnUiThread(new Runnable() {
                                        @Override public void run() { setProgress(message); }
                                    });
                                }
                            });
                    final String report = formatResult(result);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            outputView.append(report);
                            statusView.setText("Finished");
                            scrollOutputToBottom();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            outputView.append("\nERROR: " + safeMessage(e) + "\n");
                            statusView.setText("SSH/SCP failed");
                            scrollOutputToBottom();
                        }
                    });
                }
            }
        });
    }

    private String formatResult(SshManager.CombinedResult combined) {
        StringBuilder report = new StringBuilder();
        if (combined.command != null) {
            report.append("\n--- stdout ---\n").append(combined.command.stdout);
            if (combined.command.stderr.length() > 0) {
                report.append("\n--- stderr ---\n").append(combined.command.stderr);
            }
            report.append("\n--- exit ").append(combined.command.exitStatus).append("; ")
                    .append(combined.command.elapsedMs).append(" ms ---\n");
        }
        if (combined.transfer != null) {
            report.append("\nSCP saved ").append(combined.transfer.bytes).append(" bytes to:\n")
                    .append(combined.transfer.localFile.getAbsolutePath()).append('\n');
        }
        return report.toString();
    }

    private void setProgress(String message) {
        statusView.setText(message);
        outputView.append("\n[" + message + "]");
        scrollOutputToBottom();
    }

    private boolean confirmHostKeyBlocking(final String message) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean accepted = new AtomicBoolean(false);
        runOnUiThread(new Runnable() {
            @Override public void run() {
                AlertDialog dialog = new AlertDialog.Builder(SshActivity.this)
                        .setTitle("Verify SSH host key")
                        .setMessage(message + "\n\nAccept only if this fingerprint matches the computer you intended to reach.")
                        .setPositiveButton("Accept and save", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                accepted.set(true);
                                latch.countDown();
                            }
                        }).setNegativeButton("Reject", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface dialog, int which) {
                                latch.countDown();
                            }
                        }).create();
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface dialog) { latch.countDown(); }
                });
                dialog.show();
            }
        });
        try { latch.await(2, TimeUnit.MINUTES); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return accepted.get();
    }

    private void promptYouTubeCommand(final SshProfile profile) {
        final EditText command = field("Command template; use {url}, {videoId}, {title}, {channel}",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String saved = getSharedPreferences("rockbox_solar_youtube", MODE_PRIVATE)
                .getString("ssh_template", "printf '%s\\n' {url}");
        command.setText(saved);
        command.setSingleLine(false);
        command.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("SSH command for YouTube result")
                .setView(command).setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String template = command.getText().toString().trim();
                        if (template.length() == 0) {
                            toast("Enter a command template");
                            return;
                        }
                        getSharedPreferences("rockbox_solar_youtube", MODE_PRIVATE).edit()
                                .putString("ssh_template", template).apply();
                        String expanded = applyYouTubeTemplate(template, getIntent());
                        promptOptionalYouTubeOutput(profile, expanded);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void promptOptionalYouTubeOutput(final SshProfile profile, final String command) {
        final LinearLayout form = verticalForm();
        final EditText remote = field("Remote output path to SCP back (optional)", InputType.TYPE_CLASS_TEXT);
        final EditText local = field("Local filename override (optional)", InputType.TYPE_CLASS_TEXT);
        form.addView(remote);
        form.addView(local);
        new AlertDialog.Builder(this).setTitle("Run command and fetch output")
                .setMessage("Leave the remote path blank to run the command without copying a file.")
                .setView(form).setPositiveButton("Run", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        promptSecretAndExecute(profile, command, remote.getText().toString().trim(),
                                local.getText().toString().trim());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private static String applyYouTubeTemplate(String template, Intent intent) {
        return template
                .replace("{url}", SshManager.shellQuote(value(intent, EXTRA_URL)))
                .replace("{videoId}", SshManager.shellQuote(value(intent, EXTRA_VIDEO_ID)))
                .replace("{title}", SshManager.shellQuote(value(intent, EXTRA_TITLE)))
                .replace("{channel}", SshManager.shellQuote(value(intent, EXTRA_CHANNEL)));
    }

    private static String value(Intent intent, String key) {
        String value = intent.getStringExtra(key);
        return value == null ? "" : value;
    }

    private void showCommandHistory() {
        final List<String> history = profileStore.history();
        if (history.isEmpty()) {
            toast("No commands have been run yet");
            return;
        }
        String[] values = history.toArray(new String[history.size()]);
        new AlertDialog.Builder(this).setTitle("Command history").setItems(values, null).show();
    }

    private void confirmDelete(final SshProfile profile) {
        new AlertDialog.Builder(this).setTitle("Delete " + profile.name + "?")
                .setMessage("This removes the saved host profile. Saved host-key fingerprints remain trusted.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        profileStore.delete(profile.id);
                        refreshProfiles();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showOutput() {
        showingOutput = true;
        listView.setVisibility(View.GONE);
        outputScroll.setVisibility(View.VISIBLE);
        outputView.requestFocus();
    }

    private void showHosts() {
        showingOutput = false;
        outputScroll.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        refreshProfiles();
        statusView.setText("Select a saved host or add one.");
        listView.requestFocus();
    }

    @Override public void onBackPressed() {
        if (showingOutput) showHosts();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout verticalForm() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        form.setPadding(pad, pad / 2, pad, 0);
        return form;
    }

    private EditText field(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private void scrollOutputToBottom() {
        outputScroll.post(new Runnable() {
            @Override public void run() { outputScroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_LONG).show();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? e.getClass().getSimpleName() : message;
    }
}
