# Rockbox Solar 0.3

Rockbox Solar is a clean-room, wheel-first Android launcher and media shell for the Innioasis Y1/Y2 family. It does not copy Rockbox firmware code or Solar source code.

## Implemented

- Rockbox-style local music browser, recursive library scan, playback queue, seeking, media keys, podcasts, authorized direct downloads, and JSON themes
- DSP presets, bass boost, virtualizer, microphone recording, and FM application/hardware probing
- Reach/Soulseek inside the APK:
  - experimental direct Soulseek login/search/download protocol client
  - slskd-compatible search and queued downloads for the more reliable full-network path
- Deezer public catalog search and official Deezer-provided preview playback
- LALAL.AI stem separation, local stem caching, and synchronized two-stem mixing
- Original-Y1 theme archive translator for colors, static backgrounds, XML/properties palettes, and scale hints
- Built-in wheel-friendly plugins: Snake, stopwatch, calculator, dice roller, starfield, and system information
- Official YouTube Data API metadata search
- Saved SSH hosts, verified host keys, arbitrary command execution, command history, and SCP retrieval
- YouTube/Deezer URL-to-SSH command templates with shell-quoted placeholders
- Replacement Wi-Fi interface: enable/disable, scan, connect, disconnect, forget, signal/security details
- Replacement Bluetooth interface: enable/disable, discovery, pairing, unpairing, A2DP connect/disconnect where firmware permits it
- Checksum-verified APK updates and Type-A/Type-B-gated ROM package staging
- Physical-Y1 validation suite with key/scan-code capture, storage, audio, Wi-Fi, Bluetooth, microphone/FM, root probes, and JSON report export

## Important boundaries

- Deezer integration uses the public catalog and previews exposed by Deezer. It does not include ARL-cookie extraction, private download endpoints, DRM removal, or subscription-track downloading.
- Soulseek must be used only for files you are authorized to obtain. Direct protocol mode is experimental and requires a reachable peer-listener port; slskd mode is the reliable fallback.
- LALAL.AI requires the user's own license key and may consume paid processing minutes.
- Theme translation cannot execute vendor scripts, proprietary fonts/widgets, or device-specific binaries.
- The plugin menu contains Android-native equivalents. It is not a binary port of every upstream Rockbox C plugin or codec.
- The ROM manager verifies and stages packages only. It never automatically flashes preloader, LK, boot, recovery, or other boot-critical partitions.
- A successful CI build is not physical Y1 validation. Run the Hardware Validation screen on the actual player and keep ADB recovery available.

## Build

Requirements: JDK 21, Android SDK 35, and Gradle 8.9.

```sh
gradle --no-daemon :app:assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a Y1

First test as a normal APK:

```sh
adb devices
adb install -r RockboxSolar-v0.3-debug.apk
```

Press Home, select Rockbox Solar, and choose **Just once** until wheel, Select, Back, playback, Wi-Fi, Bluetooth, and ADB recovery have been tested. Do not flash boot-critical partitions merely to install this APK.

Uninstall:

```sh
adb uninstall dev.lordfunion.rockboxsolar
```

## Storage

- Music/downloads: `/sdcard/Music/RockboxSolar`
- SSH/SCP files: `/sdcard/Music/RockboxSolar/Remote`
- Reach downloads: `/sdcard/Music/RockboxSolar/Reach`
- Stems: `/sdcard/Music/RockboxSolar/Stems`
- Recordings: `/sdcard/Music/RockboxSolar/Recordings`
- Themes: `/sdcard/RockboxSolar/themes`
- Updates: `/sdcard/RockboxSolar/Updates`
- Validation reports: `/sdcard/RockboxSolar/Diagnostics`
