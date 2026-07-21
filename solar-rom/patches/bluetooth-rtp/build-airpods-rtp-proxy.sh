#!/usr/bin/env bash
# 2026-07-19 — Build AirPods RTP timestamp proxy (Semy0nBu y1-airpods-rtpfix).
# Clears Cursor AppImage LD_LIBRARY_PATH so NDK gcc can find cc1.
# Usage: build-airpods-rtp-proxy.sh [ANDROID_NDK_ROOT]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$SCRIPT_DIR/src/libbluetoothdrv_proxy.c"
OUT_DIR="$SCRIPT_DIR/prebuilt"
OUT="$OUT_DIR/libbluetoothdrv.so"

NDK="${1:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK" ]; then
  for cand in \
    "$HOME/Documents/Rocksayr/staging/android-ndk-r10e" \
    "$HOME/Android/Sdk/ndk/"* \
    /opt/android-ndk*; do
    if [ -d "$cand" ]; then NDK="$cand"; break; fi
  done
fi
[ -n "$NDK" ] && [ -d "$NDK" ] || { echo "build-airpods-rtp-proxy: set ANDROID_NDK_ROOT" >&2; exit 1; }
[ -f "$SRC" ] || { echo "missing $SRC" >&2; exit 1; }

GCC=""
for g in \
  "$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64/bin/arm-linux-androideabi-gcc" \
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi16-clang" \
  "$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi21-clang"; do
  [ -x "$g" ] && GCC="$g" && break
done
[ -n "$GCC" ] || { echo "no arm gcc/clang under $NDK" >&2; exit 1; }

SYSROOT=""
for s in "$NDK/platforms/android-16/arch-arm" "$NDK/platforms/android-17/arch-arm"; do
  [ -d "$s" ] && SYSROOT="$s" && break
done

mkdir -p "$OUT_DIR"
# Cursor AppImage pollutes LD_LIBRARY_PATH — strip it for the compiler process.
run_cc() {
  env -u LD_LIBRARY_PATH -u LD_PRELOAD -u APPDIR -u APPIMAGE "$@"
}

echo "==> Building AirPods RTP proxy -> $OUT"
if [[ "$GCC" == *clang* ]]; then
  run_cc "$GCC" --target=armv7a-linux-androideabi16 -shared -fPIC -O2 \
    -DENABLE_BT_SETCONFIG_REWRITE=0 \
    -DENABLE_RTP_TIMESTAMP_FIX=1 \
    -DENABLE_VERBOSE_BT_MEDIA_LOG=0 \
    -Wl,-soname,libbluetoothdrv.so \
    -o "$OUT" "$SRC" -llog -ldl
else
  [ -n "$SYSROOT" ] || { echo "missing NDK sysroot android-16/arch-arm" >&2; exit 1; }
  run_cc "$GCC" --sysroot="$SYSROOT" -shared -fPIC -O2 \
    -DENABLE_BT_SETCONFIG_REWRITE=0 \
    -DENABLE_RTP_TIMESTAMP_FIX=1 \
    -DENABLE_VERBOSE_BT_MEDIA_LOG=0 \
    -Wl,-soname,libbluetoothdrv.so \
    -o "$OUT" "$SRC" -llog -ldl
fi
file "$OUT"
echo "OK: $(md5sum "$OUT")"
