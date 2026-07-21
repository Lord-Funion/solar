package com.solar.launcher;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Host checks for NP transport ownership ladder (gapless SolarTransport vs idle MediaPlayer).
 * Layman: status/chrome must follow the engine that actually makes sound.
 * Layers are inside transport — no solo-first rung. 2026-07-20
 */
public class ActiveAudioOwnershipTest {

    /** Transport owns playback → ignore idle MediaPlayer “not playing”. 2026-07-20 */
    @Test
    public void transportOwns_playingTruthIgnoresIdleMediaPlayer() {
        assertTrue(MainActivity.isActiveAudioPlayingForTest(
                /*transportOwns*/ true, /*transportPlaying*/ true,
                /*podcastActive*/ false, /*podcastPlaying*/ false,
                /*musicIjk*/ false, /*musicIjkPlaying*/ false,
                /*mediaPlayerPlaying*/ false));
        assertFalse(MainActivity.isActiveAudioPlayingForTest(
                true, false,
                false, false,
                false, false,
                false));
    }

    /**
     * Compat 9-arg form: obsolete soloReady is ignored (layers folded into transport).
     * Was: soloReady outranked transport. Reversal: assert soloReady wins again.
     * 2026-07-20
     */
    @Test
    public void soloReady_compatArgsIgnored_transportWins() {
        assertFalse(MainActivity.isActiveAudioPlayingForTest(
                /*soloReady*/ true, /*soloPlaying*/ true,
                /*transportOwns*/ true, /*transportPlaying*/ false,
                false, false,
                false, false,
                false));
        assertTrue(MainActivity.isActiveAudioPlayingForTest(
                true, false,
                true, true,
                false, false,
                false, false,
                true));
    }

    /**
     * Scrub/FF duration follows transport → IJK → MediaPlayer (layers via transport).
     * Layman: hold-seek and fine scrub know how long the song you hear is.
     * 2026-07-20
     */
    @Test
    public void scrubDuration_followsOwnershipLadderNotIdleMediaPlayer() {
        // Transport preferred over legacy soloDur slot. 2026-07-20
        assertEquals(200_000, MainActivity.scrubDurationMsForTest(
                /*solo*/ 180_000, /*transport*/ 200_000, /*ijk*/ 0, /*mp*/ 0));
        assertEquals(200_000, MainActivity.scrubDurationMsForTest(
                0, 200_000, 90_000, 0));
        assertEquals(90_000, MainActivity.scrubDurationMsForTest(
                0, 0, 90_000, 60_000));
        assertEquals(60_000, MainActivity.scrubDurationMsForTest(
                0, 0, 0, 60_000));
        assertEquals(0, MainActivity.scrubDurationMsForTest(0, 0, 0, 0));
        // soloDur only when transport absent (compat). 2026-07-20
        assertEquals(180_000, MainActivity.scrubDurationMsForTest(
                180_000, 0, 0, 0));
    }

    /**
     * Gapless promote requires ownsPlayback (layers no longer release ownership).
     * 2026-07-20
     */
    @Test
    public void transportPromote_requiresOwnsAndPreparedNext() {
        assertFalse(MainActivity.canPromoteTransportPreparedNextForTest(
                /*ownsPlayback*/ false, /*hasPreparedNext*/ true));
        assertTrue(MainActivity.canPromoteTransportPreparedNextForTest(true, true));
        assertFalse(MainActivity.canPromoteTransportPreparedNextForTest(true, false));
        assertFalse(MainActivity.canPromoteTransportPreparedNextForTest(false, false));
    }

    /** Without transport, MediaPlayer (or IJK) truth still applies. 2026-07-20 */
    @Test
    public void legacyMediaPlayer_usedWhenNothingElseOwns() {
        assertTrue(MainActivity.isActiveAudioPlayingForTest(
                false, false,
                false, false,
                false, false,
                true));
        assertTrue(MainActivity.isActiveAudioPlayingForTest(
                false, false,
                false, false,
                true, true,
                false));
    }

    /** Gapless promote of a different file must clear sticky solo origin. 2026-07-20 */
    @Test
    public void promoteDifferentFile_clearsSoloOrigin() {
        File origin = new File("/music/songA.mp3");
        File next = new File("/music/songB.mp3");
        assertTrue(MainActivity.shouldClearSoloSessionOnPromoteForTest(next, origin));
        assertFalse(MainActivity.shouldClearSoloSessionOnPromoteForTest(origin, origin));
        assertFalse(MainActivity.shouldClearSoloSessionOnPromoteForTest(next, null));
        assertTrue(MainActivity.shouldClearSoloSessionOnPromoteForTest(null, origin));
    }

    /**
     * Queue NP row shows playing when ladder says audible — even if MediaPlayer is idle.
     * 2026-07-20
     */
    @Test
    public void queueRowPlaying_followsAudibleLadderNotIdleMediaPlayer() {
        boolean audible = MainActivity.isActiveAudioPlayingForTest(
                true, true,
                false, false,
                false, false,
                /*mediaPlayerPlaying*/ false);
        assertTrue(audible);
        assertTrue(MainActivity.queueRowPlayingForTest(2, 2, audible));
        assertFalse(MainActivity.queueRowPlayingForTest(2, 1, audible));
        assertFalse(MainActivity.queueRowPlayingForTest(2, 2, false));
    }

    /**
     * Library glyph uses audible ladder, not isPausedByHand alone.
     * 2026-07-20
     */
    @Test
    public void libraryRowPlaying_followsAudibleWhenNowPlaying() {
        assertTrue(MainActivity.libraryRowPlayingForTest(true, true));
        assertFalse(MainActivity.libraryRowPlayingForTest(true, false));
        assertFalse(MainActivity.libraryRowPlayingForTest(false, true));
    }

    /**
     * Overlay queue: NP play glyph from persisted playing flag.
     * 2026-07-20
     */
    @Test
    public void overlayQueue_npPlayingRespectsPersistedFlag() {
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.music(new File("/music/a.mp3")));
        q.append(PlayQueue.QueueItem.music(new File("/music/b.mp3")));
        q.setIndex(1);
        ThemedContextMenu.QueueRowSpec[] paused = OverlayQueueHelper.buildRowSpecs(q, false);
        assertFalse(paused[1].playing);
        assertTrue(paused[1].nowPlaying);
        ThemedContextMenu.QueueRowSpec[] playing = OverlayQueueHelper.buildRowSpecs(q, true);
        assertTrue(playing[1].playing);
        assertFalse(playing[0].playing);
    }
}
