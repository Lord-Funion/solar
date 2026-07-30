#!/usr/bin/env sh
set -eu
adb uninstall dev.lordfunion.rockboxsolar || true
echo "User-installed copy removed. A system-app copy under /system/app must be removed with root."
