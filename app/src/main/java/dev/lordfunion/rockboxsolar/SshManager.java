package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Properties;

final class SshManager {
    interface Interaction {
        boolean confirmHostKey(String message);
        void onProgress(String message);
    }

    static final class CommandResult {
        final String stdout;
        final String stderr;
        final int exitStatus;
        final long elapsedMs;

        CommandResult(String stdout, String stderr, int exitStatus, long elapsedMs) {
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitStatus = exitStatus;
            this.elapsedMs = elapsedMs;
        }
    }

    static final class TransferResult {
        final File localFile;
        final long bytes;

        TransferResult(File localFile, long bytes) {
            this.localFile = localFile;
            this.bytes = bytes;
        }
    }

    static final class CombinedResult {
        final CommandResult command;
        final TransferResult transfer;

        CombinedResult(CommandResult command, TransferResult transfer) {
            this.command = command;
            this.transfer = transfer;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final long COMMAND_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final int MAX_CAPTURE_BYTES = 1024 * 1024;
    private final Context context;
    private final File knownHosts;

    SshManager(Context context) {
        this.context = context.getApplicationContext();
        this.knownHosts = new File(context.getFilesDir(), "ssh_known_hosts");
        try {
            if (!knownHosts.exists()) knownHosts.createNewFile();
        } catch (Exception ignored) { }
    }

    CombinedResult runAndFetch(SshProfile profile, String secret, String command,
                               String remotePath, String preferredName,
                               Interaction interaction) throws Exception {
        Session session = connect(profile, secret, interaction);
        try {
            CommandResult commandResult = null;
            if (command != null && command.trim().length() > 0) {
                interaction.onProgress("Running command on " + profile.host + "…");
                commandResult = runCommand(session, command.trim());
                interaction.onProgress("Command finished with exit " + commandResult.exitStatus);
            }
            TransferResult transferResult = null;
            if (remotePath != null && remotePath.trim().length() > 0) {
                interaction.onProgress("Receiving " + remotePath.trim() + " with SCP…");
                transferResult = receiveScp(session, remotePath.trim(), preferredName, interaction);
                interaction.onProgress("Saved " + transferResult.localFile.getAbsolutePath());
            }
            return new CombinedResult(commandResult, transferResult);
        } finally {
            session.disconnect();
        }
    }

    private Session connect(SshProfile profile, String secret, Interaction interaction) throws Exception {
        if (profile.host == null || profile.host.trim().length() == 0) throw new IllegalArgumentException("SSH host is empty");
        if (profile.user == null || profile.user.trim().length() == 0) throw new IllegalArgumentException("SSH user is empty");
        interaction.onProgress("Connecting to " + profile.user + "@" + profile.host + ":" + profile.port + "…");
        JSch jsch = new JSch();
        jsch.setKnownHosts(knownHosts.getAbsolutePath());
        String cleanSecret = secret == null ? "" : secret;
        if (profile.usesKey()) {
            File key = new File(profile.keyPath == null ? "" : profile.keyPath);
            if (!key.isFile()) throw new IllegalArgumentException("Private key not found: " + key.getAbsolutePath());
            if (cleanSecret.length() > 0) jsch.addIdentity(key.getAbsolutePath(), cleanSecret);
            else jsch.addIdentity(key.getAbsolutePath());
        }
        Session session = jsch.getSession(profile.user.trim(), profile.host.trim(), profile.port);
        Properties configuration = new Properties();
        configuration.put("StrictHostKeyChecking", "ask");
        configuration.put("PreferredAuthentications", profile.usesKey()
                ? "publickey,keyboard-interactive,password"
                : "keyboard-interactive,password,publickey");
        session.setConfig(configuration);
        if (!profile.usesKey()) session.setPassword(cleanSecret);
        session.setUserInfo(new InteractionUserInfo(cleanSecret, interaction));
        session.setServerAliveInterval(15000);
        session.setServerAliveCountMax(3);
        session.connect(CONNECT_TIMEOUT_MS);
        interaction.onProgress("SSH connected; host key verified");
        return session;
    }

    private CommandResult runCommand(Session session, String command) throws Exception {
        long started = System.currentTimeMillis();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        channel.setCommand(command);
        channel.setPty(false);
        channel.setErrStream(new LimitedOutputStream(stderr, MAX_CAPTURE_BYTES), true);
        InputStream stdoutStream = channel.getInputStream();
        channel.connect(CONNECT_TIMEOUT_MS);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (true) {
                while (stdoutStream.available() > 0) {
                    int read = stdoutStream.read(buffer, 0, Math.min(buffer.length, stdoutStream.available()));
                    if (read < 0) break;
                    appendLimited(stdout, buffer, read);
                }
                if (channel.isClosed()) {
                    while (stdoutStream.available() > 0) {
                        int read = stdoutStream.read(buffer, 0, Math.min(buffer.length, stdoutStream.available()));
                        if (read < 0) break;
                        appendLimited(stdout, buffer, read);
                    }
                    break;
                }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("SSH command cancelled");
                if (System.currentTimeMillis() - started > COMMAND_TIMEOUT_MS) throw new IllegalStateException("SSH command timed out after 30 minutes");
                Thread.sleep(80L);
            }
            return new CommandResult(new String(stdout.toByteArray(), Charset.forName("UTF-8")),
                    new String(stderr.toByteArray(), Charset.forName("UTF-8")),
                    channel.getExitStatus(), System.currentTimeMillis() - started);
        } finally {
            channel.disconnect();
        }
    }

    private TransferResult receiveScp(Session session, String remotePath, String preferredName,
                                      Interaction interaction) throws Exception {
        String safeRemote = remotePath.startsWith("-") ? "./" + remotePath : remotePath;
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand("scp -f " + shellQuote(safeRemote));
        InputStream input = channel.getInputStream();
        OutputStream output = channel.getOutputStream();
        channel.connect(CONNECT_TIMEOUT_MS);
        File part = null;
        try {
            sendAck(output);
            while (true) {
                int code = readResponseCode(input);
                if (code == -1) throw new IllegalStateException("SCP ended before sending a file");
                if (code == 'T') {
                    readLine(input);
                    sendAck(output);
                    continue;
                }
                if (code != 'C') throw new IllegalStateException("Unsupported SCP response: " + (char) code);
                String header = readLine(input);
                int firstSpace = header.indexOf(' ');
                int secondSpace = firstSpace < 0 ? -1 : header.indexOf(' ', firstSpace + 1);
                if (firstSpace < 0 || secondSpace < 0) throw new IllegalStateException("Malformed SCP header");
                long size = Long.parseLong(header.substring(firstSpace + 1, secondSpace));
                String remoteName = sanitizeFilename(header.substring(secondSpace + 1));
                String localName = preferredName == null || preferredName.trim().length() == 0
                        ? remoteName : sanitizeFilename(preferredName.trim());
                File directory = new File(new File(Environment.getExternalStorageDirectory(), "Music"), "RockboxSolar/Remote");
                if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Cannot create " + directory);
                File destination = uniqueFile(directory, localName);
                part = new File(destination.getAbsolutePath() + ".part");
                sendAck(output);
                FileOutputStream fileOutput = new FileOutputStream(part);
                byte[] buffer = new byte[32768];
                long remaining = size;
                long received = 0L;
                long lastProgress = 0L;
                try {
                    while (remaining > 0) {
                        int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read < 0) throw new IllegalStateException("SCP connection closed after " + received + " of " + size + " bytes");
                        fileOutput.write(buffer, 0, read);
                        remaining -= read;
                        received += read;
                        long now = System.currentTimeMillis();
                        if (now - lastProgress > 700L) {
                            lastProgress = now;
                            interaction.onProgress("SCP " + received + " / " + size + " bytes");
                        }
                    }
                    fileOutput.flush();
                    fileOutput.getFD().sync();
                } finally {
                    fileOutput.close();
                }
                int endCode = readResponseCode(input);
                if (endCode != 0) throw new IllegalStateException("SCP file transfer failed");
                sendAck(output);
                if (destination.exists() && !destination.delete()) throw new IllegalStateException("Cannot replace destination");
                if (!part.renameTo(destination)) throw new IllegalStateException("Cannot finalize downloaded file");
                part = null;
                Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scan.setData(Uri.fromFile(destination));
                context.sendBroadcast(scan);
                return new TransferResult(destination, size);
            }
        } finally {
            if (part != null && part.exists()) part.delete();
            channel.disconnect();
        }
    }

    static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void sendAck(OutputStream output) throws Exception {
        output.write(0);
        output.flush();
    }

    private static int readResponseCode(InputStream input) throws Exception {
        int code = input.read();
        if (code == 1 || code == 2) {
            String message = readLine(input);
            throw new IllegalStateException("SCP: " + message);
        }
        return code;
    }

    private static String readLine(InputStream input) throws Exception {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0 && value != '\n') {
            if (line.size() > 8192) throw new IllegalStateException("SCP response line too long");
            line.write(value);
        }
        return new String(line.toByteArray(), Charset.forName("UTF-8"));
    }

    private static void appendLimited(ByteArrayOutputStream output, byte[] data, int length) {
        int remaining = MAX_CAPTURE_BYTES - output.size();
        if (remaining <= 0) return;
        output.write(data, 0, Math.min(remaining, length));
    }

    private static String sanitizeFilename(String value) {
        String name = new File(value == null ? "remote-file" : value).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.length() == 0 || ".".equals(name) || "..".equals(name)) name = "remote-file";
        if (name.length() > 160) name = name.substring(name.length() - 160);
        return name;
    }

    private static File uniqueFile(File directory, String filename) {
        File result = new File(directory, filename);
        if (!result.exists()) return result;
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        for (int i = 2; i < 10000; i++) {
            result = new File(directory, base + " (" + i + ")" + extension);
            if (!result.exists()) return result;
        }
        return new File(directory, System.currentTimeMillis() + "-" + filename);
    }

    private static final class InteractionUserInfo implements UserInfo, UIKeyboardInteractive {
        private final String secret;
        private final Interaction interaction;

        InteractionUserInfo(String secret, Interaction interaction) {
            this.secret = secret == null ? "" : secret;
            this.interaction = interaction;
        }

        @Override public String getPassphrase() { return secret; }
        @Override public String getPassword() { return secret; }
        @Override public boolean promptPassword(String message) { return secret.length() > 0; }
        @Override public boolean promptPassphrase(String message) { return true; }
        @Override public boolean promptYesNo(String message) { return interaction.confirmHostKey(message); }
        @Override public void showMessage(String message) { interaction.onProgress(message); }

        @Override public String[] promptKeyboardInteractive(String destination, String name,
                                                            String instruction, String[] prompt,
                                                            boolean[] echo) {
            String[] answers = new String[prompt.length];
            for (int i = 0; i < answers.length; i++) answers[i] = secret;
            return answers;
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream output;
        private final int limit;

        LimitedOutputStream(ByteArrayOutputStream output, int limit) {
            this.output = output;
            this.limit = limit;
        }

        @Override public void write(int value) {
            if (output.size() < limit) output.write(value);
        }

        @Override public void write(byte[] data, int offset, int length) {
            int remaining = limit - output.size();
            if (remaining > 0) output.write(data, offset, Math.min(remaining, length));
        }
    }
}
