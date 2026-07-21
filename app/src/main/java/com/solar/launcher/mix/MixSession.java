package com.solar.launcher.mix;

import java.io.File;

/**
 * Two-deck Mix jam state — full tracks, optional stem dig per disc.
 * Layman: two songs floating as discs; Prev/Next own a deck; dig opens four stem pads.
 * Technical: slots[0..1] file/gain/bpm/rate; activeDeck for wheel/scrub.
 * Was: DECK_COUNT=3 faders. Reversal: DECK_COUNT = 3.
 * 2026-07-19 / 2026-07-21 Stems/Mix sanity
 */
public final class MixSession {
    public static final int DECK_COUNT = 2;
    /** Gain near mute — hold pad opens scrub. */
    public static final float SCRUB_GAIN_EPS = 0.02f;

    public static final class DeckState {
        public File track;
        public float gain;
        public float bpm = 120f;
        public float rate = 1f;
        public String displayName = "";

        public void clear() {
            track = null;
            gain = 0f;
            bpm = 120f;
            rate = 1f;
            displayName = "";
        }

        public boolean hasTrack() {
            return track != null && track.isFile();
        }
    }

    private final DeckState[] decks = new DeckState[DECK_COUNT];
    private int activeDeck = -1;
    private int filledCount;

    public MixSession() {
        for (int i = 0; i < DECK_COUNT; i++) decks[i] = new DeckState();
    }

    /** Bind up to {@link #DECK_COUNT} files; clears empty slots. 2026-07-19 / 2026-07-21 */
    public void bindTracks(File[] tracks) {
        filledCount = 0;
        activeDeck = -1;
        for (int i = 0; i < DECK_COUNT; i++) {
            decks[i].clear();
            if (tracks != null && i < tracks.length && tracks[i] != null && tracks[i].isFile()) {
                decks[i].track = tracks[i];
                decks[i].displayName = stripExt(tracks[i].getName());
                filledCount++;
            }
        }
    }

    public void setSlot(int index, File track) {
        if (index < 0 || index >= DECK_COUNT) return;
        decks[index].clear();
        if (track != null && track.isFile()) {
            decks[index].track = track;
            decks[index].displayName = stripExt(track.getName());
        }
        recount();
    }

    private void recount() {
        filledCount = 0;
        for (int i = 0; i < DECK_COUNT; i++) {
            if (decks[i].hasTrack()) filledCount++;
        }
    }

    public int filledCount() {
        return filledCount;
    }

    public DeckState deck(int index) {
        if (index < 0 || index >= DECK_COUNT) return null;
        return decks[index];
    }

    public int activeDeck() {
        return activeDeck;
    }

    public void setActiveDeck(int index) {
        if (index < 0 || index >= DECK_COUNT) return;
        activeDeck = index;
    }

    public void clearActiveDeck() {
        activeDeck = -1;
    }

    /**
     * Short pad: focus deck, or no-op if already focused (scrub is hold).
     * @return true if focus changed
     */
    public boolean onDeckKey(int deck) {
        if (deck < 0 || deck >= DECK_COUNT) return false;
        if (activeDeck == deck) return false;
        activeDeck = deck;
        return true;
    }

    public static String stripExt(String name) {
        if (name == null) return "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) return name.substring(0, dot);
        return name;
    }
}
