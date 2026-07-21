#!/usr/bin/env bash
# 2026-07-19 — One-shot lab stage: push+pm install, pm clear, launch, verify on all Y1/Y2.
# Uses adb -t transport_id (duplicate serials). Reversal: delete this helper.
set -u
set +e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
REMOTE="/data/local/tmp/solar-test.apk"
PKG="com.solar.launcher"

[[ -f "$APK" ]] || { echo "ERROR: missing $APK" >&2; exit 1; }

EXP_NAME="$(sed -n 's/.*versionName "\([^"]*\)".*/\1/p' app/build.gradle | head -1)"
EXP_CODE="$(sed -n 's/.*versionCode \([0-9][0-9]*\).*/\1/p' app/build.gradle | head -1)"
echo "EXPECTED versionName=$EXP_NAME versionCode=$EXP_CODE"
echo "APK=$APK ($(wc -c < "$APK") bytes)"

declare -a TIDS=()
declare -a FAMS=()

while IFS= read -r line; do
  [[ -z "$line" ]] && continue
  tid="$(printf '%s' "$line" | sed -n 's/.*transport_id:\([0-9][0-9]*\).*/\1/p')"
  [[ -z "$tid" ]] && continue
  cpu="$(adb -t "$tid" shell cat /proc/cpuinfo 2>/dev/null | tr -d '\r' \
    | awk -F: '/^[Hh]ardware/{gsub(/^ +/,"",$2); print $2; exit}')"
  fam=unknown
  printf '%s' "$cpu" | grep -qi mt6582 && fam=y2
  printf '%s' "$cpu" | grep -qi mt6572 && fam=y1
  TIDS+=("$tid")
  FAMS+=("$fam")
  echo "target tid=$tid family=$fam cpu=$cpu"
done < <(adb devices -l 2>/dev/null | awk '$2 == "device" { print }')

[[ ${#TIDS[@]} -gt 0 ]] || { echo "ERROR: no devices" >&2; exit 1; }

stage_one() {
  local tid="$1" fam="$2"
  echo ""
  echo "======== Staging tid=$tid family=$fam ========"

  timeout 20 adb -t "$tid" shell am force-stop "$PKG" >/dev/null 2>&1 || true

  echo "  push..."
  if ! timeout 120 adb -t "$tid" push "$APK" "$REMOTE" >/tmp/solar-push-$tid.log 2>&1; then
    echo "  FAIL push:"; tail -5 /tmp/solar-push-$tid.log | sed 's/^/    /'
    return 1
  fi
  echo "  push ok"

  echo "  pm install (su)..."
  timeout 240 adb -t "$tid" shell "su -c 'pm install -r -d -t $REMOTE'" \
    >/tmp/solar-pm-$tid.log 2>&1
  if ! grep -q Success /tmp/solar-pm-$tid.log; then
    echo "  su pm miss; trying user pm..."
    timeout 240 adb -t "$tid" shell "pm install -r -d -t $REMOTE" \
      >/tmp/solar-pm-$tid.log 2>&1
  fi
  if ! grep -q Success /tmp/solar-pm-$tid.log; then
    echo "  FAIL install:"
    tr -d '\r' </tmp/solar-pm-$tid.log | tail -8 | sed 's/^/    /'
    return 1
  fi
  echo "  install Success"
  sleep 2

  echo "  pm clear..."
  timeout 60 adb -t "$tid" shell "su -c 'pm clear $PKG'" >/tmp/solar-clear-$tid.log 2>&1
  if ! grep -q Success /tmp/solar-clear-$tid.log; then
    timeout 60 adb -t "$tid" shell "pm clear $PKG" >/tmp/solar-clear-$tid.log 2>&1
  fi
  if grep -q Success /tmp/solar-clear-$tid.log; then
    echo "  pm clear Success"
  else
    echo "  WARN pm clear:"
    tr -d '\r' </tmp/solar-clear-$tid.log | tail -4 | sed 's/^/    /'
  fi

  timeout 25 adb -t "$tid" shell am start -n com.solar.launcher/.MainActivity >/dev/null 2>&1
  echo "  launched"
  sleep 2

  local dump name code path
  dump="$(timeout 40 adb -t "$tid" shell dumpsys package "$PKG" 2>/dev/null | tr -d '\r')"
  name="$(printf '%s\n' "$dump" | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1)"
  code="$(printf '%s\n' "$dump" | sed -n 's/.*versionCode=\([^ ]*\).*/\1/p' | head -1)"
  path="$(timeout 15 adb -t "$tid" shell pm path "$PKG" 2>/dev/null | tr -d '\r' | grep '^package:' | head -1)"
  echo "  path=$path"
  echo "  installed versionName=$name versionCode=$code"
  if [[ "$name" == "$EXP_NAME" && "$code" == "$EXP_CODE" ]]; then
    echo "  VERIFY OK"
    return 0
  fi
  echo "  VERIFY FAIL (want $EXP_NAME / $EXP_CODE)"
  # Extra diagnostics when version stuck
  timeout 15 adb -t "$tid" shell "su -c 'ls -la /data/app/com.solar.launcher* /system/app/com.solar.launcher* 2>/dev/null'" \
    2>/dev/null | tr -d '\r' | sed 's/^/    /'
  return 1
}

FAIL=0
for i in "${!TIDS[@]}"; do
  if ! stage_one "${TIDS[$i]}" "${FAMS[$i]}"; then
    FAIL=1
  fi
done

echo ""
if [[ "$FAIL" -eq 0 ]]; then
  echo "==> All devices staged + reset + verified ($EXP_NAME)."
  exit 0
fi
echo "==> Completed with failures." >&2
exit 1
