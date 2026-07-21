#!/usr/bin/env bash
# 2026-07-20 — Rasterize Y1 hardware button SVGs to theme-tintable PNGs (height 48px).
# Layman: turns vector wheel-button art into the little pictures hints use.
# Tech: rsvg-convert; BACK/PLAY from full composites (back/options, play/pause/stop).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSET="$ROOT/app/src/main/assets/y1"
if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "render-y1-button-glyphs: rsvg-convert required" >&2
  exit 1
fi
H=48
render() {
  local svg="$1" png="$2"
  rsvg-convert -h "$H" "$ASSET/$svg" -o "$ASSET/$png"
  echo "render-y1-button-glyphs: $png"
}
render back_options.svg btn_back.png
render play_pause.svg btn_play_pause.png
render previous_track.svg btn_prev.png
render next_track.svg btn_next.png
render ok.svg btn_ok.png
render scroll_wheel.svg btn_wheel.png
echo "render-y1-button-glyphs: done (height=${H}px)"
