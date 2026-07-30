# Rockbox Solar (clean-room Android shell)

Rockbox Solar is an unofficial, wheel-first Android launcher and music player for the Innioasis Y1/Y2 family. It is a new implementation: it does **not** copy Solar source code or Rockbox firmware code.

## Implemented in the first APK

- Rockbox-style full-screen, hardware-key-first interface
- Local file browser and recursive music database scan
- MP3/FLAC/OGG/WAV/M4A/AAC/Opus/WMA/APE handoff to Android `MediaPlayer`
- Now Playing, queue, seek, next/previous and a global quick menu
- JSON themes stored at `/sdcard/RockboxSolar/themes`
- Podcast RSS/Atom loading and enclosure downloads
- Resumable authorized direct-file downloads to `/sdcard/Music/RockboxSolar`
- Wi-Fi, Bluetooth and sound settings shortcuts
- Optional launch bridge into separately installed Solar for Reach, Deezer and Soulseek
- Android 4.2.2 / API 17 compatibility target

## Important boundary

This project does not contain a Deezer media extractor, ARL-cookie handler, DRM circumvention, or copied Solar implementation. The Deezer/Reach/Soulseek entries only open a separately installed Solar application. Use services and downloads only where you have authorization and in accordance with their terms.

## Build

```bash
gradle :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install without flashing (recommended)

Enable USB debugging, connect the Y1, then:

```bash
adb devices
adb install -r RockboxSolar-v0.1-debug.apk
```

Press Home and select **Rockbox Solar**. Do not select “Always” until the hardware keys and Back behavior have been tested.

## Install as a system app on a rooted Y1

An APK is installed, not normally “flashed.” To place it in the read-only system image without touching boot-critical partitions:

```bash
adb push RockboxSolar-v0.1-debug.apk /data/local/tmp/RockboxSolar.apk
adb shell su -c 'mount -o remount,rw /system'
adb shell su -c 'cp /data/local/tmp/RockboxSolar.apk /system/app/RockboxSolar.apk'
adb shell su -c 'chmod 0644 /system/app/RockboxSolar.apk'
adb shell su -c 'sync'
adb reboot
```

Some Y1 images use a different `su` implementation or mount command. Verify `/system` is writable before copying. This APK requests no platform-signature permissions.

## Recovery

Before choosing it as the permanent Home app, verify that ADB works. To remove it:

```bash
adb uninstall dev.lordfunion.rockboxsolar
```

For a system-app install:

```bash
adb shell su -c 'mount -o remount,rw /system'
adb shell su -c 'rm -f /system/app/RockboxSolar.apk'
adb reboot
```

If the launcher loops, use ADB to start Android settings or uninstall the package. Do **not** flash `preloader`, `lk`, `boot`, or `recovery` merely to install this APK. Y1 Type A and Type B boot-critical images are not interchangeable.

## Theme format

Create a JSON file in `/sdcard/RockboxSolar/themes`, for example:

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

This is a first device-test build. It has not been verified on a physical Y1 in this build environment. Use ADB installation first and keep a recovery path.
