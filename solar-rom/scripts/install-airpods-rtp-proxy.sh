#!/usr/bin/env bash
# 2026-07-19 — Install AirPods RTP libbluetoothdrv proxy into mounted /system (Y1).
# Layout: libbluetoothdrv.so = proxy; libbluetoothdrv_real.so = stock (MD5-gated).
# Soft-skips on MD5 mismatch (Y2 / unknown stock). Idempotent when already proxied.
# Usage: install-airpods-rtp-proxy.sh <mounted-system-root>
# Reversal: mv libbluetoothdrv_real.so -> libbluetoothdrv.so; rm proxy.
set -euo pipefail

SYS="${1:-}"
[ -n "$SYS" ] && [ -d "$SYS" ] || { echo "usage: $0 <mounted-system-root>" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Prefer patches/bluetooth-rtp next to scripts via repo layout.
PATCH_DIR="$(cd "$SCRIPT_DIR/../patches/bluetooth-rtp" 2>/dev/null && pwd || true)"
if [ -z "${PATCH_DIR:-}" ]; then
  PATCH_DIR="$(cd "$SCRIPT_DIR/../vendor/bluetooth-rtp" 2>/dev/null && pwd || true)"
fi
# When invoked as solar-rom/scripts/install-airpods-rtp-proxy.sh
if [ -z "${PATCH_DIR:-}" ] || [ ! -d "$PATCH_DIR" ]; then
  PATCH_DIR="$(cd "$(dirname "$SCRIPT_DIR")/patches/bluetooth-rtp" && pwd)"
fi

PROXY="$PATCH_DIR/prebuilt/libbluetoothdrv.so"
# Official Innioasis Y1 3.0.7 / rockbox-y1 / Koensayr stock libbluetoothdrv.so
STOCK_MD5_Y1_307="32f1af87e46acaf1efa3f083340495cb"

LIB="$SYS/lib/libbluetoothdrv.so"
REAL="$SYS/lib/libbluetoothdrv_real.so"

run() {
  if command -v sudo >/dev/null 2>&1 && [ ! -w "$SYS/lib" ]; then
    sudo "$@"
  else
    "$@"
  fi
}

if [ ! -f "$PROXY" ]; then
  echo "==> AirPods RTP proxy: soft-skip (missing $PROXY — run build-airpods-rtp-proxy.sh)"
  exit 0
fi
if [ ! -f "$LIB" ]; then
  echo "==> AirPods RTP proxy: soft-skip (no $LIB)"
  exit 0
fi

# Already installed — proxy in place + real present.
if [ -f "$REAL" ] && strings "$LIB" 2>/dev/null | grep -q 'BTRTPFIX'; then
  echo "==> AirPods RTP proxy: already installed"
  exit 0
fi

CUR_MD5="$(md5sum "$LIB" | awk '{print $1}')"
if [ "$CUR_MD5" != "$STOCK_MD5_Y1_307" ]; then
  # Already our proxy without real? refuse.
  if strings "$LIB" 2>/dev/null | grep -q 'BTRTPFIX'; then
    echo "==> AirPods RTP proxy: WARN proxy without _real — refusing" >&2
    exit 0
  fi
  echo "==> AirPods RTP proxy: soft-skip (libbluetoothdrv MD5=$CUR_MD5 != Y1 3.0.7 $STOCK_MD5_Y1_307)"
  exit 0
fi

echo "==> AirPods RTP proxy: installing (stock MD5 matched Y1 3.0.7)"
run cp -a "$LIB" "$REAL"
run cp "$PROXY" "$LIB"
run chmod 644 "$LIB" "$REAL"
run chown root:root "$LIB" "$REAL" 2>/dev/null || true
echo "==> AirPods RTP proxy: OK (proxy=$(md5sum "$LIB" | awk '{print $1}'), real=$CUR_MD5)"
exit 0
