# Rockbox Solar (clean-room Android shell)

Rockbox Solar is an unofficial, wheel-first Android launcher and music player for the Innioasis Y1/Y2 family. It is a new implementation and does **not** copy Solar source code or Rockbox firmware code.

## Implemented in v0.2

- Unified HOME launcher for Rockbox Player, YouTube Search, Remote SSH + SCP, Solar Reach, Wi-Fi, Bluetooth, and Android settings
- Rockbox-style local file browser, recursive music scan, Now Playing, playlist, seeking, hardware media keys, quick controls, and JSON themes
- Podcast RSS/Atom browsing and enclosure downloads
- Resumable authorized direct-file downloads
- Public YouTube Data API v3 metadata search for title, channel, IDs, URL, duration, views, publication date, description, and thumbnail URL
- Copy or open a YouTube result URL
- Pass a selected result into an SSH command template
- Saved SSH profiles using password or private-key authentication
- Strict host-key verification with first-use fingerprint confirmation and a persistent local `known_hosts` file
- Arbitrary user-entered SSH commands with stdout, stderr, exit status, progress, timeout, and command history
- Run a command and then retrieve a specified remote output file using the SCP protocol
- SCP-only retrieval to `/sdcard/Music/RockboxSolar/Remote`
- YouTube-to-SSH placeholders: `{url}`, `{videoId}`, `{title}`, and `{channel}`; each value is shell-quoted before insertion
- Optional bridge into a separately installed Solar build for Reach, Deezer, and Soulseek
- Android 4.2.2 / API 17 compatibility target

## YouTube setup

The app uses the official YouTube Data API v3 for public metadata only.

1. Create a Google Cloud project.
2. Enable **YouTube Data API v3**.
3. Create an API key.
4. Open **YouTube Search → API Key** on the player and paste it.

The key is stored in this app's private preferences. The YouTube feature does not download video or audio. It can hand a selected public URL to a command on a computer you control.

## SSH and SCP setup

Open **Remote SSH + SCP → Add SSH host** and enter a display name, hostname/IP, port, username, and either password or private-key authentication.

Example private-key path:

```text
/sdcard/RockboxSolar/ssh/id_ed25519
```

Passwords and key passphrases are requested per connection and are not saved. On the first connection, compare the displayed host-key fingerprint with the fingerprint on the computer before accepting it.

The SSH menu supports:

- run command
- run command, then SCP a remote file back
- SCP a remote file without running a command
- edit/delete hosts
- command history

Received files are placed in:

```text
/sdcard/Music/RockboxSolar/Remote
```

For a YouTube result, select **Run SSH command with URL**. A command template may contain:

```text
{url} {videoId} {title} {channel}
```

After entering the command, optionally provide the exact remote output path to copy back after the command exits.

## Boundaries

This project does not contain a Deezer media extractor, ARL-cookie handler, DRM circumvention, copied Solar implementation, or built-in YouTube media downloader. The Deezer/Reach/Soulseek entries only open a separately installed Solar application. You control the remote command and are responsible for using it only with systems and content you are authorized to access or process.

## Build

```bash
gradle --no-daemon :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install without flashing

```bash
adb devices
adb install -r RockboxSolar-v0.2-debug.apk
```

Press Home and select **Rockbox Solar**. Test the wheel, Select, Back, playback, YouTube entry, and SSH entry before choosing **Always**.

## Optional rooted system-app installation

An APK is installed, not normally flashed:

```bash
adb push RockboxSolar-v0.2-debug.apk /data/local/tmp/RockboxSolar.apk
adb shell su -c 'mount -o remount,rw /system'
adb shell su -c 'cp /data/local/tmp/RockboxSolar.apk /system/app/RockboxSolar.apk'
adb shell su -c 'chmod 0644 /system/app/RockboxSolar.apk'
adb shell su -c 'sync'
adb reboot
```

Some Y1 images use different root/remount commands. Verify `/system` is writable first.

## Recovery

```bash
adb uninstall dev.lordfunion.rockboxsolar
```

For a system-app installation:

```bash
adb shell su -c 'mount -o remount,rw /system'
adb shell su -c 'rm -f /system/app/RockboxSolar.apk'
adb reboot
```

Do **not** flash `preloader`, `lk`, `boot`, or `recovery` merely to install this APK. Y1 Type A and Type B boot-critical images are not interchangeable.

## Theme format

Create JSON files under `/sdcard/RockboxSolar/themes`:

```json
{
  "name": "Purple Parlor",
  "background": "#120A1F",
  "foreground": "#F5ECFF",
  "accent": "#C68CFF",
  "selected": "#3C205B",
  "muted": "#A68DBD",
  "fontScale": 1.0
}
```

## Status

This is a development build. Compilation and packaging are checked in GitHub Actions. Physical Y1 networking, host-key prompts, SSH algorithm negotiation, SCP interoperability, storage permissions, and long-running remote commands still require device testing before a final release.
