package com.solar.launcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Artist/album browse rules driven by {@link LibraryBrowsePrefs}.
 * 2026-07-19 — {@link AlbumOwnerIndex} precomputes owners so guest checks stay O(1).
 */
public final class ArtistBrowsePolicy {

    public static final class Track {
        public final String artist;
        public final String album;
        public final String albumArtist;
        public final long lastModified;

        public Track(String artist, String album, String albumArtist, long lastModified) {
            this.artist = artist != null ? artist : "";
            this.album = album != null ? album : "";
            this.albumArtist = albumArtist != null ? albumArtist.trim() : "";
            this.lastModified = lastModified;
        }
    }

    /**
     * 2026-07-19 — One-pass album-owner / guest index for large libraries.
     * Layman: after a scan we remember who “owns” each album so lists do not re-walk every song.
     * Technical: O(n) build; albumOwner/hasOwnAlbum/subtitle become map lookups.
     * Reversal: nested walks in hasOwnAlbum/albumOwnerForBrowse over the full track list.
     */
    public static final class AlbumOwnerIndex {
        private final LibraryBrowsePrefs prefs;
        /** albumRackKey → (ownerMatchKey → track count) */
        private final Map<String, Map<String, Integer>> ownerCountsByAlbum =
                new HashMap<String, Map<String, Integer>>();
        /** albumRackKey → (ownerMatchKey → display name) */
        private final Map<String, Map<String, String>> ownerDisplayByAlbum =
                new HashMap<String, Map<String, String>>();
        /** artistMatchKey → true when artist owns ≥1 album they are credited on */
        private final Set<String> artistsWithOwnAlbum = new HashSet<String>();
        /** artistMatchKey → albumRackKeys where that artist is credited */
        private final Map<String, Set<String>> albumsByCreditedArtist =
                new HashMap<String, Set<String>>();

        private AlbumOwnerIndex(LibraryBrowsePrefs prefs) {
            this.prefs = prefs;
        }

        /** Build owner maps from the full policy track list (one linear pass). */
        public static AlbumOwnerIndex build(List<Track> library, LibraryBrowsePrefs prefs) {
            AlbumOwnerIndex idx = new AlbumOwnerIndex(prefs);
            if (library == null || library.isEmpty()) return idx;
            for (Track song : library) {
                if (song == null || song.album == null || song.album.trim().isEmpty()) continue;
                String albumKey = albumRackKey(song, prefs);
                if (albumKey.isEmpty()) continue;
                String owner = trackOwner(song, prefs);
                if (!owner.isEmpty() && !isUnknownArtist(owner)) {
                    String ownerKey = ArtistNames.matchKey(owner);
                    Map<String, Integer> counts = idx.ownerCountsByAlbum.get(albumKey);
                    if (counts == null) {
                        counts = new HashMap<String, Integer>();
                        idx.ownerCountsByAlbum.put(albumKey, counts);
                    }
                    counts.put(ownerKey, counts.containsKey(ownerKey) ? counts.get(ownerKey) + 1 : 1);
                    Map<String, String> displays = idx.ownerDisplayByAlbum.get(albumKey);
                    if (displays == null) {
                        displays = new HashMap<String, String>();
                        idx.ownerDisplayByAlbum.put(albumKey, displays);
                    }
                    if (!displays.containsKey(ownerKey)) displays.put(ownerKey, owner);
                }
                List<String> credited = creditedArtists(song.artist, prefs);
                for (String raw : credited) {
                    String display = displayArtist(raw, prefs);
                    if (display.isEmpty() || isUnknownArtist(display)) continue;
                    String aKey = ArtistNames.matchKey(display);
                    Set<String> albums = idx.albumsByCreditedArtist.get(aKey);
                    if (albums == null) {
                        albums = new HashSet<String>();
                        idx.albumsByCreditedArtist.put(aKey, albums);
                    }
                    albums.add(albumKey);
                }
            }
            // 2026-07-19 — Precompute owns-album flags so collectArtists filter is O(1) per name.
            for (Map.Entry<String, Set<String>> e : idx.albumsByCreditedArtist.entrySet()) {
                String artistKey = e.getKey();
                for (String albumKey : e.getValue()) {
                    if (idx.ownerForAlbumKey(albumKey, artistKey) == null) {
                        idx.artistsWithOwnAlbum.add(artistKey);
                        break;
                    }
                }
            }
            return idx;
        }

        /**
         * Null when browsed artist owns (or ties) the album; else majority owner display.
         * prefs arg kept for call-site parity (index already baked with prefs). 2026-07-19
         */
        public String albumOwnerForBrowse(String album, String browsedArtist, LibraryBrowsePrefs prefs) {
            if (album == null || browsedArtist == null) return null;
            String browsedKey = ArtistNames.matchKey(displayArtist(browsedArtist, this.prefs));
            String albumKey = resolveAlbumKey(album, browsedArtist);
            return ownerForAlbumKey(albumKey, browsedKey);
        }

        public boolean hasOwnAlbum(String artist) {
            if (artist == null || artist.trim().isEmpty()) return false;
            return artistsWithOwnAlbum.contains(
                    ArtistNames.matchKey(displayArtist(artist, prefs)));
        }

        public String albumBrowseSubtitle(String album, String browsedArtist, LibraryBrowsePrefs prefs) {
            LibraryBrowsePrefs p = prefs != null ? prefs : this.prefs;
            if (p == null || !p.albumOwnerSubtitles()) return "";
            String owner = albumOwnerForBrowse(album, browsedArtist, p);
            if (owner == null || owner.trim().isEmpty()) return "";
            if (ArtistNames.equals(owner, browsedArtist)) return "";
            return owner;
        }

        private String resolveAlbumKey(String album, String browsedArtist) {
            if (AlbumNames.isUnknownAlbum(album)) {
                return AlbumNames.unknownAlbumRackKey(displayArtist(browsedArtist, prefs));
            }
            return AlbumNames.matchKey(album.trim());
        }

        /** Same majority / tie rules as legacy albumOwnerForBrowse. */
        private String ownerForAlbumKey(String albumKey, String browsedKey) {
            if (albumKey == null || albumKey.isEmpty()) return null;
            Map<String, Integer> counts = ownerCountsByAlbum.get(albumKey);
            if (counts == null || counts.isEmpty()) return null;
            String bestKey = null;
            int bestCount = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    bestKey = e.getKey();
                }
            }
            if (bestKey == null || bestKey.equals(browsedKey)) return null;
            Integer browsedCount = counts.get(browsedKey);
            if (browsedCount != null && browsedCount >= bestCount) return null;
            Map<String, String> displays = ownerDisplayByAlbum.get(albumKey);
            return displays != null ? displays.get(bestKey) : null;
        }
    }

    private ArtistBrowsePolicy() {}

    public static List<String> collectArtists(List<Track> library, LibraryBrowsePrefs prefs) {
        if (library == null || library.isEmpty()) return Collections.emptyList();
        // 2026-07-19 — Build index once; filter used to call hasOwnAlbum (O(n²)) per artist.
        AlbumOwnerIndex idx = AlbumOwnerIndex.build(library, prefs);
        Map<String, String> displayByKey = new HashMap<String, String>();
        Map<String, Integer> trackCounts = new HashMap<String, Integer>();
        Map<String, Long> recentByKey = new HashMap<String, Long>();
        for (Track song : library) {
            List<String> names = creditedArtists(song.artist, prefs);
            for (String raw : names) {
                String display = displayArtist(raw, prefs);
                if (display.isEmpty() || isUnknownArtist(display)) continue;
                String key = ArtistNames.matchKey(display);
                if (!displayByKey.containsKey(key)) {
                    displayByKey.put(key, display);
                } else {
                    displayByKey.put(key, ArtistNames.preferCanonical(displayByKey.get(key), display));
                }
                trackCounts.put(key, trackCounts.containsKey(key) ? trackCounts.get(key) + 1 : 1);
                long lm = song.lastModified;
                Long prev = recentByKey.get(key);
                if (prev == null || lm > prev) recentByKey.put(key, lm);
            }
        }
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, String> e : displayByKey.entrySet()) {
            String key = e.getKey();
            String name = e.getValue();
            if (!passesArtistFilter(name, idx, prefs, trackCounts.get(key))) continue;
            out.add(name);
        }
        sortArtistNames(out, prefs, trackCounts, recentByKey);
        return out;
    }

    public static boolean shouldSkipAlbumPicker(String artist, LibraryBrowsePrefs prefs, List<Track> library) {
        if (artist == null || artist.trim().isEmpty()) return false;
        int mode = prefs != null ? prefs.guestBrowseMode() : LibraryBrowsePrefs.GUEST_BROWSE_AUTO;
        if (mode == LibraryBrowsePrefs.GUEST_BROWSE_ALWAYS_SONGS) return true;
        if (mode == LibraryBrowsePrefs.GUEST_BROWSE_ALWAYS_ALBUMS) return false;
        return !hasOwnAlbum(artist, library, prefs);
    }

    public static boolean hasOwnAlbum(String artist, List<Track> library, LibraryBrowsePrefs prefs) {
        if (artist == null || artist.trim().isEmpty() || library == null) return false;
        return AlbumOwnerIndex.build(library, prefs).hasOwnAlbum(artist);
    }

    /** Album owner when browsing as a guest credit; null when browsed artist owns the album. */
    public static String albumOwnerForBrowse(String album, String browsedArtist,
            List<Track> library, LibraryBrowsePrefs prefs) {
        if (album == null || browsedArtist == null || library == null) return null;
        return AlbumOwnerIndex.build(library, prefs).albumOwnerForBrowse(album, browsedArtist, prefs);
    }

    public static String albumBrowseSubtitle(String album, String browsedArtist,
            List<Track> library, LibraryBrowsePrefs prefs) {
        if (prefs == null || !prefs.albumOwnerSubtitles()) return "";
        return AlbumOwnerIndex.build(library, prefs).albumBrowseSubtitle(album, browsedArtist, prefs);
    }

    /**
     * 2026-07-19 — Subtitle using a shared index (Flow/rack loops must not rebuild per album).
     */
    public static String albumBrowseSubtitle(String album, String browsedArtist,
            AlbumOwnerIndex index, LibraryBrowsePrefs prefs) {
        if (index == null) return "";
        return index.albumBrowseSubtitle(album, browsedArtist, prefs);
    }

    public static boolean isGuestOnlyArtistBrowse(String artist, String queryType,
            List<Track> library, LibraryBrowsePrefs prefs) {
        if (!"ARTIST".equals(queryType) || artist == null || artist.trim().isEmpty()) return false;
        if (prefs == null) return !hasOwnAlbum(artist, library, null);
        int mode = prefs.guestBrowseMode();
        if (mode == LibraryBrowsePrefs.GUEST_BROWSE_ALWAYS_SONGS) return true;
        if (mode == LibraryBrowsePrefs.GUEST_BROWSE_ALWAYS_ALBUMS) return false;
        return !hasOwnAlbum(artist, library, prefs);
    }

    public static String guestSongSubtitleOwner(String album, String browsedArtist,
            List<Track> library, LibraryBrowsePrefs prefs) {
        if (prefs == null || !prefs.guestSongSubtitles()) return "";
        return albumOwnerForBrowse(album, browsedArtist, library, prefs);
    }

    private static List<String> creditedArtists(String artistField, LibraryBrowsePrefs prefs) {
        if (prefs != null && !prefs.splitCredits()) {
            List<String> one = new ArrayList<String>();
            String primary = ArtistParser.primaryArtist(artistField);
            if (primary != null && !primary.trim().isEmpty()) one.add(primary.trim());
            else if (artistField != null && !artistField.trim().isEmpty()) one.add(artistField.trim());
            return one;
        }
        List<String> parts = ArtistParser.splitArtists(artistField);
        if (parts.isEmpty() && artistField != null && !artistField.trim().isEmpty()) {
            parts = Collections.singletonList(artistField.trim());
        }
        return parts;
    }

    private static String displayArtist(String raw, LibraryBrowsePrefs prefs) {
        if (raw == null) return "";
        return raw.trim();
    }

    private static String trackOwner(Track song, LibraryBrowsePrefs prefs) {
        if (song.albumArtist != null && !song.albumArtist.isEmpty()
                && !isUnknownArtist(song.albumArtist)) {
            return displayArtist(song.albumArtist, prefs);
        }
        return displayArtist(ArtistParser.primaryArtist(song.artist), prefs);
    }

    /** Album rack key — artist-scoped for Unknown Album, title-only otherwise. */
    private static String albumRackKey(Track song, LibraryBrowsePrefs prefs) {
        if (song == null || song.album == null || song.album.trim().isEmpty()) return "";
        if (AlbumNames.isUnknownAlbum(song.album)) {
            return AlbumNames.unknownAlbumRackKey(trackOwner(song, prefs));
        }
        return AlbumNames.matchKey(song.album.trim());
    }

    private static boolean passesArtistFilter(String artist, AlbumOwnerIndex idx,
            LibraryBrowsePrefs prefs, Integer trackCount) {
        if (prefs == null) return true;
        int filter = prefs.artistFilter();
        if (filter == LibraryBrowsePrefs.FILTER_ALL) return true;
        if (filter == LibraryBrowsePrefs.FILTER_MIN_TWO_TRACKS) {
            return trackCount != null && trackCount >= 2;
        }
        boolean owns = idx != null && idx.hasOwnAlbum(artist);
        if (filter == LibraryBrowsePrefs.FILTER_OWNERS_ONLY) return owns;
        if (filter == LibraryBrowsePrefs.FILTER_HIDE_GUEST_ONLY) return owns;
        return true;
    }

    private static void sortArtistNames(List<String> names, LibraryBrowsePrefs prefs,
            Map<String, Integer> trackCounts, Map<String, Long> recentByKey) {
        final int sort = prefs != null ? prefs.artistSort() : LibraryBrowsePrefs.ARTIST_SORT_NAME;
        Collections.sort(names, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                if (sort == LibraryBrowsePrefs.ARTIST_SORT_TRACK_COUNT) {
                    int ca = countFor(a, trackCounts);
                    int cb = countFor(b, trackCounts);
                    if (ca != cb) return cb - ca;
                } else if (sort == LibraryBrowsePrefs.ARTIST_SORT_RECENT) {
                    long ra = recentFor(a, recentByKey);
                    long rb = recentFor(b, recentByKey);
                    if (ra != rb) return ra > rb ? -1 : 1;
                }
                return a.compareToIgnoreCase(b);
            }
        });
    }

    private static int countFor(String name, Map<String, Integer> trackCounts) {
        Integer c = trackCounts.get(ArtistNames.matchKey(name));
        return c != null ? c : 0;
    }

    private static long recentFor(String name, Map<String, Long> recentByKey) {
        Long r = recentByKey.get(ArtistNames.matchKey(name));
        return r != null ? r : 0L;
    }

    private static boolean isUnknownArtist(String name) {
        return "Unknown Artist".equalsIgnoreCase(name)
                || "Various Artists".equalsIgnoreCase(name);
    }
}
