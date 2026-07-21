#!/usr/bin/env bash
# 2026-07-19 — Koensayr-style Bluetooth pairing conf into a mounted /system tree.
# Layman: unlock headset/car Just Works pairing that stock blacklists blocked.
# Technical: audio.conf Enable/Master, clear auto_pairing blacklists, drop scoSocket lines,
#   append CoD + A2DP/AVRCP profile props (never persist.bluetooth.avrcpversion).
# Soft-skips when /etc/bluetooth is absent (some trees). Idempotent.
# Reversal: restore stock conf from base ROM; remove ro.bluetooth.* lines from build.prop.
# Usage: install-bluetooth-pairing-conf.sh <mounted-system-root>
set -euo pipefail

SYS="${1:-}"
if [ -z "$SYS" ] || [ ! -d "$SYS" ]; then
  echo "install-bluetooth-pairing-conf: need mounted system root" >&2
  exit 1
fi

BT_DIR="$SYS/etc/bluetooth"
PROP="$SYS/build.prop"

if [ ! -d "$BT_DIR" ]; then
  echo "==> Bluetooth pairing conf: soft-skip (no $BT_DIR)"
  exit 0
fi

echo "==> Bluetooth pairing conf (Koensayr --bluetooth port)"

run_sed() {
  # Prefer sudo when writing into a loop-mounted system image.
  if command -v sudo >/dev/null 2>&1 && [ ! -w "$1" ]; then
    sudo sed -i "$2" "$1"
  else
    sed -i "$2" "$1"
  fi
}

append_prop() {
  local key="$1" val="$2"
  if [ ! -f "$PROP" ]; then
    echo "WARN: missing build.prop — skip $key" >&2
    return 0
  fi
  if grep -q "^${key}=" "$PROP" 2>/dev/null; then
    return 0
  fi
  if command -v sudo >/dev/null 2>&1 && [ ! -w "$PROP" ]; then
    echo "${key}=${val}" | sudo tee -a "$PROP" >/dev/null
  else
    echo "${key}=${val}" >> "$PROP"
  fi
}

if [ -f "$BT_DIR/audio.conf" ]; then
  if grep -q '^Enable=' "$BT_DIR/audio.conf" 2>/dev/null; then
    run_sed "$BT_DIR/audio.conf" 's/^Enable=.*/Enable=Source,Control,Target/'
  else
    if command -v sudo >/dev/null 2>&1 && [ ! -w "$BT_DIR/audio.conf" ]; then
      echo 'Enable=Source,Control,Target' | sudo tee -a "$BT_DIR/audio.conf" >/dev/null
    else
      echo 'Enable=Source,Control,Target' >> "$BT_DIR/audio.conf"
    fi
  fi
  if grep -q '^Master=' "$BT_DIR/audio.conf" 2>/dev/null; then
    run_sed "$BT_DIR/audio.conf" 's/^Master=.*/Master=true/'
  else
    if command -v sudo >/dev/null 2>&1 && [ ! -w "$BT_DIR/audio.conf" ]; then
      echo 'Master=true' | sudo tee -a "$BT_DIR/audio.conf" >/dev/null
    else
      echo 'Master=true' >> "$BT_DIR/audio.conf"
    fi
  fi
  echo "  audio.conf Enable=Source,Control,Target Master=true"
else
  echo "  WARN: no audio.conf"
fi

if [ -f "$BT_DIR/auto_pairing.conf" ]; then
  run_sed "$BT_DIR/auto_pairing.conf" 's/^AddressBlacklist=.*/AddressBlacklist=/'
  run_sed "$BT_DIR/auto_pairing.conf" 's/^ExactNameBlacklist=.*/ExactNameBlacklist=/'
  run_sed "$BT_DIR/auto_pairing.conf" 's/^PartialNameBlacklist=.*/PartialNameBlacklist=/'
  echo "  auto_pairing.conf blacklists cleared"
else
  echo "  WARN: no auto_pairing.conf"
fi

if [ -f "$BT_DIR/blacklist.conf" ]; then
  run_sed "$BT_DIR/blacklist.conf" '/^scoSocket/d'
  echo "  blacklist.conf scoSocket lines removed"
else
  echo "  WARN: no blacklist.conf"
fi

# Never set persist.bluetooth.avrcpversion — mtkbt cannot honor claimed version (Koensayr).
append_prop "ro.bluetooth.class" "10486812"
append_prop "ro.bluetooth.profiles.a2dp.source.enabled" "true"
append_prop "ro.bluetooth.profiles.avrcp.target.enabled" "true"
echo "  build.prop CoD + A2DP/AVRCP profile props ensured"

echo "==> Bluetooth pairing conf: OK"
exit 0
