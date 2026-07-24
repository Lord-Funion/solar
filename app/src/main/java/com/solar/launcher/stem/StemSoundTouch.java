package com.solar.launcher.stem;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * IJK SoundTouch options for Stem tempo match (no chipmunk).
 * Layman: stretch time so other songs match Song 1’s pulse without going squeaky.
 * Technical: soundtouch=1 + audio-only before prepare; same idea as PodcastIjkPlayer.
 * Was: MediaPlayer @ 1.0 only. Reversal: skip applyStemPlayerOptions.
 * 2026-07-19
 */
public final class StemSoundTouch {
    private StemSoundTouch() {}

    /** Wire pitch-preserving rate options on an IJK player before setDataSource. */
    public static void applyStemPlayerOptions(IjkMediaPlayer player) {
        if (player == null) return;
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "vn", 1);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
        // 2026-07-21 — OpenSL ES hardware audio track bypasses Java JNI / AudioTrack buffering on MTK.
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 1);
        // 2026-07-21 — Low latency packet buffering for responsive stem real-time mixing.
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 131072);
        player.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "infbuf", 1);
    }

    /** True when option list matches stem SoundTouch contract (unit-test hook). */
    public static boolean isSoundTouchEnabled(long soundtouchOptionValue) {
        return soundtouchOptionValue == 1L;
    }
}
