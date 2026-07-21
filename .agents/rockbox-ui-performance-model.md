# Rockbox UI performance model → Solar

2026-07-18 — Shared mental model for agents. Source tree: `~/Documents/Y2 Rockbox Workspace/rockbox-y2`.
2026-07-20 — Product + 256MB/~64MB-avail residency (Workstream B / plan `256mb_hardware_opt`).

## Product aims (what Solar is)

Solar is an **iPod-class home OS layer** for Innioasis Y1/Y2 (~480×360, **wheel-first, no touch**):

- Local music → Now Playing + play queue; Flow cover carousel; Reach / Get Music; media suite; Stem/Mix; Global Options; themes + device settings.

**UX contract (do not redesign):** one primary scroll axis; visible focus; Back means back; **index-first dial** (one detent = one row; paint may lag); calm iPod/set-top metaphors. Perf work makes that **feel instant**.

**Hardware planning floor (no `DevicePerfProfile`):** same MT65xx software path for Y1+Y2. Assume **256MB total RAM**, often **~64MB free** for apps. Optimize for the weaker SoC; Y2’s extra cores/DRAM show up as **throughput**, not larger hardcoded LRUs.

## Rockbox loop (selection machine)

```
wheel / REPEAT → button_queue_try_post (drop if busy)
             → gui_synclist index += step (± accel modifier ≤64)
             → if queue_depth < 6: list_draw(visible rows only)
             → yield()
```

Key files: `apps/gui/list.c` (`FRAMEDROP_TRIGGER 6`, `gui_synclist_do_button`), `apps/gui/bitmap/list.c` (`list_draw`), `firmware/drivers/button.c` (soft repeat 300ms → 160→50ms), `button_queue_try_post` (refuse enqueue when UI behind).

Y1/Y2 Android port shares this list path; kinetic touch is compiled out on `PLATFORM_ANDROID`.

## Why it feels fast

1. Integer selection + callbacks — never N row objects
2. Drop input when busy — no afterscroll
3. Frame-drop paint — selection always advances
4. Accel by stride, not more paints
5. `yield()` every step
6. Defer voice/art (~HZ/5)
7. Partial viewport flush
8. No wrap during REPEAT

## Solar mapping

| Rockbox | Solar |
|---------|--------|
| Coalesce / one apply per frame | `ListWheelCoalescer` (lists) + `menuWheelCoalescer` (home/settings/browser, 2026-07-19) |
| Soft accel | `WheelPhysics` |
| Instant ensure-visible | `FocusScrollHelper` (duration 0) |
| Ghost-stop | KEY_UP / `dropPending` / reverse only — **no** idle-clear on offer (2026-07-20 dial integrity) |
| Frame-drop paint | `ScrollIdleGate` — skip preview/bind while spinning; settings preview deferred 2026-07-19 |
| RAM catalog | FULL: `customLibrary`; SEGMENTED (≥300): DISTINCT indexes + `songBrowseSegments` pages (Approach 3) |
| Large-lib paging | `LibrarySegmentCache` + `LibraryMemoryBudget.SEGMENTED_MIN_TRACKS=300` |
| Prefetch around dial | `SongBrowsePrefetch.blocksAround` (±1; ±2 when quiet + ~4MB free) |
| Play queue | SEGMENTED: `playWindowStart/End` (~2 blocks), not whole library |
| Defer heavy open | `UiBusy.REASON_LIBRARY_LOAD` + post-frame bind; inline `RowBusyChrome` |
| Queue input during busy | `pendingAnimWheelSignedSteps` uncapped across ScreenTransition (2026-07-20) |
| Library chores yield | Art ingest + Flow precook wait on `InputPriorityGate` IDLE_MS=1500 |
| Trim under pressure | `MemoryRelease` ladder (Flow bake→cover→theme `ByteBudgetLruMap`→segments→YT pause) |
| Artwork window | ~7 visible, ±5 prefetch; Cover≤32; Bake≤56; NP=1 — see **Artwork residency** |
| Budget / pressure | `LibraryMemoryBudget` soft free 12MB (not 64MB gate); `LowMemoryGate` ~28MB/16MB, DEFER 6s |
| Hold timings | `GlobalInputPolicy` modal/context ~280ms; POWER tap max 220ms (POLICY_REV 27) |

## Artwork residency (windowed — not album-count)

2026-07-20 — Workstream E. Catalog size ≠ art in RAM. Flow feels smooth at hundreds of albums because covers are **windowed**. Never warm-all-albums.

| Scenario | Bitmaps in RAM (design) |
|--|--|
| Flow on screen | ~**7** visible (`CoverFlowLayout.SIDE_SLIDES` ranks + center) |
| Flow prefetch | center ±**5** (`FlowView.PREFETCH_RADIUS` ≈ 11 warm) |
| `FlowCoverCache` | LRU **≤32**, distance-priority evict beyond 4 |
| `FlowCoverBakeCache` | LRU **≤56** (fatter reflection composites) |
| Now Playing | **1** cover (kept on `MemoryRelease` cover trim) |
| Dual-pane preview | **1** |
| Song lists | no per-row album art on hot path |

Decode: bounds + `inSampleSize` + RGB_565 (~96–144px). Source JPEGs may be multi‑MB — never keep full-res on the Flow path.

**Pressure:** `MemoryRelease` drops bake **before** cover; `CoverLoadGovernor` stays **1** concurrent decode and under `LowMemoryGate` only accepts focus ±1; warm radius clamps to 0; after bake lands under pressure, raw cover may be dropped (prefer not bake+raw double residency).

## Effectively infinite lists (indexes in RAM + SQLite pages)

| In RAM (hot) | On SQLite / disk (cold) |
|--|--|
| Tier-0: distinct artists, albums, counts (`LibraryRamCache`) | Full track rows / paths |
| LRU segment pages around focus (`songBrowseSegments`) | Rest of catalog |
| Now-playing + nearby covers | Full art library |

**Do not** `loadAll` into `customLibrary` when SEGMENTED. Hydrate via `countTracks` + `listDistinct*` + `rebuildFromDistinct`. Song drills: `loadRange` / `loadTracksByArtist|Album|ArtistAlbum`. Target: **50k+ local tracks** without OOM.

## Contract for Solar changes

- **Index-first dial:** one detent = one selection step; paint may lag (Home/Settings ±1 immediate; coalescer paints chrome only).
- Logical selection tracks the dial; never drop notches to “catch up” paint.
- While scrolling: no art decode, no preview refresh, no DB prefetch under spin (`ScrollIdleGate`).
- SEGMENTED: Tier-0 from SQL DISTINCT; song drills from `loadRange` / `loadTracksBy*`; do not `loadAll` into RAM.
- Playback: resolve a **window** around the chosen track (`SongBrowsePrefetch.playWindow*`), not the whole library.
- OK into heavy menus: placeholder + `UiBusy.REASON_LIBRARY_LOAD` first frame; fill after Choreographer.
- No menu wrap while `WheelPhysics.suppressWrapAround()` (Rockbox REPEAT).
- Home/settings: never sync dual-pane preview from `onFocusChange` — idle-gate only (2026-07-19).
- **No `DevicePerfProfile` / per-family warm ceilings** — one APK path; workers may use `availableProcessors()` only where pools already exist.

## Status

2026-07-20: Approach 3 + 256MB plan — SEGMENTED ≥300; soft free-heap 12MB (dropped 64MB near-hard gate); `MemoryRelease` real eviction; LMG floors/DEFER retuned; holds ~280ms; IDLE 1500; liberal `UiBusy` + `RowBusyChrome`; infinite-list (prefetch, windowed play, `trimTo`); art window doc + pressure decode/warm clamp (Workstream E).

2026-07-19 Solar-only product note: do not spend cycles on Rockbox/JJ install or launcher-matrix coexistence. Selection-machine speed is the goal; Rockbox list.c is the reference for *feel*, not a dependency to ship.

## Reversal (SEGMENTED / infinite-list)

- `SEGMENTED_MIN_TRACKS = Integer.MAX_VALUE`; always `loadAll` + `applyCachedTracks`
- Prefetch extra neighbor gated on `TARGET_FREE_HEAP_BYTES` again
- Play / context Play Now uses full `virtualSongList`
- Delete `LibrarySegmentCache.trimTo` / `trimSongBrowseSegments`

## Reversal (artwork pressure)

- Restore bake/cover max entries; allow dual bake+raw residency; remove `CoverLoadGovernor` pressure distance gate and warm radius clamp to 0.
