# Approach 3 — SEGMENTED residency (lifted 2026-07-20)

**Status:** Lifted. Real devices page the catalog at **≥300 tracks** (or earlier under heap pressure). Dial contract is **index-first** (one detent → one row; paint may lag). Infinite-list harden (same day): windowed play, SQL `collectTracks`, `trimTo`, prefetch on ~4MB free.

## What shipped

| Piece | Behavior |
|-------|----------|
| `LibraryMemoryBudget.SEGMENTED_MIN_TRACKS` | `300` — OR with existing heap/estimate/low-free gates |
| Hydrate | SEGMENTED: `countTracks` + `listDistinctArtists/Albums` → `LibraryRamCache.rebuildFromDistinct`; **no** `loadAll` into `customLibrary` |
| All Songs / Artist / Album drills | `MusicLibraryStore.loadRange` / `loadTracksByArtist` / `loadTracksByAlbum` / `loadTracksByArtistAlbum` + `songBrowseSegments` |
| Prefetch | `SongBrowsePrefetch.blocksAround`; ±2 when quiet + `EXTRA_NEIGHBOR_FREE_BYTES` (~4MB) |
| Play / Play Now | `playWindowStart/End` (~2 blocks) — not full library |
| Add artist/album to playlist | `collectTracksForQuerySegmented` pages File paths from SQL |
| Trim | `LibrarySegmentCache.trimTo` + `MainActivity.trimSongBrowseSegments` |
| Dial | `ListWheelCoalescer` no idle-clear on offer; Home/Settings ±1 selection immediate; anim queue uncapped |
| Chores | Album-art ingest + Flow precook yield via `InputPriorityGate` (share-scan pattern) |

## Out of this pass

- MicroSD↔MMC DB mirror (`LibraryDbLocator`)
- Genre/year Tier-0 from SQL when SEGMENTED (lists may be empty until FULL or a later DISTINCT)
- Full Flow album precook when `libraryRows()` is empty (builds on Flow open)
- Soft-cap 50k File paths in segmented collect (pathological add-to-playlist)

## Device smoke checklist (no APK install required for agents)

1. Home/Settings: N detents → N rows (USB tether OK)
2. Library ≥300 tracks: Artists → album → play without OOM
3. Spin wheel during post-scan art: menus stay live
4. Y1 + Y2 when staging is requested

## Reversal

- `SEGMENTED_MIN_TRACKS = Integer.MAX_VALUE` (heap gates only)
- Always `loadAll` + `applyCachedTracks`
- Restore idle-clear in `ListWheelCoalescer.offerSteps`
- Context Play Now / play use full `virtualSongList` again

See also: `.agents/rockbox-ui-performance-model.md` (dial = index-first; 256MB/~64MB-avail).
