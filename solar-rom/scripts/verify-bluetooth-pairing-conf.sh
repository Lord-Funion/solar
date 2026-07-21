#!/usr/bin/env bash
# 2026-07-19 — Assert Koensayr bluetooth pairing conf baked into a ROM zip or mounted system.
# Usage: verify-bluetooth-pairing-conf.sh <rom.zip|system-root>
# Soft-pass when /system/etc/bluetooth is absent.
set -euo pipefail

TARGET="${1:-}"
[ -n "$TARGET" ] || { echo "usage: $0 <rom.zip|system-root>" >&2; exit 1; }

TMP=""
cleanup() {
  if [ -n "$TMP" ] && [ -d "$TMP" ]; then rm -rf "$TMP"; fi
}
trap cleanup EXIT

SYS=""
if [ -d "$TARGET" ]; then
  SYS="$TARGET"
elif [ -f "$TARGET" ]; then
  TMP="$(mktemp -d)"
  if unzip -l "$TARGET" 2>/dev/null | grep -q "etc/bluetooth/audio.conf"; then
    unzip -qo "$TARGET" "system/etc/bluetooth/*" "system/build.prop" -d "$TMP" 2>/dev/null || true
    if [ -d "$TMP/system/etc/bluetooth" ]; then
      SYS="$TMP/system"
    else
      FOUND="$(find "$TMP" -type d -path "*/etc/bluetooth" 2>/dev/null | head -1 || true)"
      if [ -n "${FOUND:-}" ]; then
        SYS="$(dirname "$(dirname "$FOUND")")"
      fi
    fi
  else
    echo "verify-bluetooth-pairing-conf: no etc/bluetooth in zip — soft pass"
    exit 0
  fi
else
  echo "verify-bluetooth-pairing-conf: not found: $TARGET" >&2
  exit 1
fi

BT="$SYS/etc/bluetooth"
if [ ! -d "$BT" ]; then
  echo "verify-bluetooth-pairing-conf: no etc/bluetooth — soft pass"
  exit 0
fi

fail=0
check_file_re() {
  local f="$1" re="$2" label="$3"
  if [ ! -f "$f" ]; then
    echo "FAIL: missing $f ($label)"
    fail=1
    return
  fi
  if ! grep -qE "$re" "$f"; then
    echo "FAIL: $f missing pattern ($label)"
    fail=1
  else
    echo "OK: $label"
  fi
}

check_file_re "$BT/audio.conf" "^Enable=Source,Control,Target" "audio.conf Enable"
check_file_re "$BT/audio.conf" "^Master=true" "audio.conf Master"
if [ -f "$BT/auto_pairing.conf" ]; then
  check_file_re "$BT/auto_pairing.conf" "^AddressBlacklist=$" "auto_pairing AddressBlacklist empty"
fi
if [ -f "$BT/blacklist.conf" ] && grep -q "^scoSocket" "$BT/blacklist.conf" 2>/dev/null; then
  echo "FAIL: blacklist.conf still has scoSocket lines"
  fail=1
else
  echo "OK: blacklist.conf no scoSocket (or absent)"
fi

PROP="$SYS/build.prop"
if [ -f "$PROP" ]; then
  check_file_re "$PROP" "^ro\\.bluetooth\\.class=10486812" "build.prop CoD"
  check_file_re "$PROP" "^ro\\.bluetooth\\.profiles\\.a2dp\\.source\\.enabled=true" "build.prop a2dp"
  check_file_re "$PROP" "^ro\\.bluetooth\\.profiles\\.avrcp\\.target\\.enabled=true" "build.prop avrcp"
  if grep -q "^persist\\.bluetooth\\.avrcpversion=" "$PROP" 2>/dev/null; then
    echo "FAIL: persist.bluetooth.avrcpversion must not be set"
    fail=1
  else
    echo "OK: no persist.bluetooth.avrcpversion"
  fi
fi

[ "$fail" -eq 0 ] || exit 1
echo "verify-bluetooth-pairing-conf: OK"
exit 0
