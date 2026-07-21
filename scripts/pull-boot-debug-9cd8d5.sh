#!/usr/bin/env bash
# 2026-07-20 — Pull boot perf NDJSON from device after cold-start repro (session 9cd8d5).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/.cursor/debug-9cd8d5.log"
SERIAL="${1:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then ADB=(adb -s "$SERIAL"); fi

"${ADB[@]}" reverse tcp:7386 tcp:7386 2>/dev/null || true

TMP="$(mktemp)"
PULLED=0
for path in \
  /data/data/com.solar.launcher/files/debug-9cd8d5.log \
  /storage/sdcard0/.solar/debug-a1f293.log \
  /storage/sdcard1/.solar/debug-a1f293.log; do
  if "${ADB[@]}" shell "test -f '$path'" 2>/dev/null; then
    "${ADB[@}" pull "$path" "$TMP" >/dev/null 2>&1 && cat "$TMP" >> "$OUT" && PULLED=1
  fi
done
rm -f "$TMP"

if [[ "$PULLED" -eq 1 ]]; then
  echo "Merged device logs → $OUT"
  wc -l "$OUT"
else
  echo "No device log found; check HTTP ingest wrote $OUT during repro."
  [[ -f "$OUT" ]] && wc -l "$OUT" || echo "(host log missing)"
fi
