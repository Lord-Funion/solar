#!/usr/bin/env sh
set -eu
APK="${1:-RockboxSolar-v0.3-debug.apk}"
if [ ! -f "$APK" ]; then
  echo "APK not found: $APK" >&2
  exit 1
fi
adb devices
adb install -r "$APK"
echo "Installed. Press Home, choose Rockbox Solar, and use Just once until hardware validation and ADB recovery pass."
