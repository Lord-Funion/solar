package com.solar.launcher;

import android.content.Context;

import com.solar.launcher.stem.QueuePrepStatus;
import com.solar.launcher.stem.QueuePrepStatusRegistry;
import com.solar.launcher.stem.StemMixQueuePolicy;

import java.io.File;

/**
 * Build now-playing queue rows for the system overlay — reads {@link PlayQueueStore} directly.
 * Play glyph uses persisted playing flag (overlay process has no SolarTransport). 2026-07-20
 * Footer Add song + prep marquees (Stem/Mix/Music parity). 2026-07-21
 */
public final class OverlayQueueHelper {

    /** Footer title — not a PlayQueue index. 2026-07-21 */
    public static final String FOOTER_ADD_SONG = "Add song";

    private OverlayQueueHelper() {}

    /** Load queue from disk (overlay process may not share MainActivity memory). */
    public static PlayQueue loadQueue(Context ctx) {
        PlayQueue q = new PlayQueue();
        if (ctx != null) {
            PlayQueueStore.restore(ctx.getApplicationContext(), q);
        }
        q.clampIndex();
        return q;
    }

    /**
     * Row specs after {@link #loadQueue} — NP play glyph follows disk “playing” flag.
     * Was: i == np always “playing” → pause never shown in overlay queue.
     * Reversal: last arg always (i == np).
     * 2026-07-20
     */
    public static ThemedContextMenu.QueueRowSpec[] buildRowSpecs(PlayQueue q) {
        return buildRowSpecs(q, PlayQueueStore.lastRestoredPlaying);
    }

    /** Host-testable: audible from PlayQueueStore or injected. 2026-07-20 */
    static ThemedContextMenu.QueueRowSpec[] buildRowSpecs(PlayQueue q, boolean audible) {
        return buildRowSpecs(q, audible, false);
    }

    /**
     * Queue rows + optional footer Add song; prep marquees on subtitles.
     * Layman: last row adds a song; busy tracks say what they’re cooking.
     * Technical: footer not a queue index; hide when moveActive.
     * Was: track rows only. Reversal: buildRowSpecs(q, audible) without footer.
     * 2026-07-21
     */
    public static ThemedContextMenu.QueueRowSpec[] buildRowSpecs(PlayQueue q, boolean audible,
            boolean includeFooter) {
        return buildRowSpecs(q, audible, includeFooter, false);
    }

    /**
     * @param moveActive when true, footer is omitted (fade/hide during ribbon move).
     * 2026-07-21
     */
    public static ThemedContextMenu.QueueRowSpec[] buildRowSpecs(PlayQueue q, boolean audible,
            boolean includeFooter, boolean moveActive) {
        if (q == null || q.isEmpty()) {
            if (includeFooter && StemMixQueuePolicy.footerVisible(moveActive)) {
                return new ThemedContextMenu.QueueRowSpec[] {
                        new ThemedContextMenu.QueueRowSpec(FOOTER_ADD_SONG, "", false, false)
                };
            }
            return new ThemedContextMenu.QueueRowSpec[0];
        }
        int size = q.size();
        boolean showFooter = includeFooter && StemMixQueuePolicy.footerVisible(moveActive);
        int rowCount = showFooter ? StemMixQueuePolicy.adapterCountWithFooter(size) : size;
        int np = q.index();
        ThemedContextMenu.QueueRowSpec[] rows = new ThemedContextMenu.QueueRowSpec[rowCount];
        for (int i = 0; i < size; i++) {
            PlayQueue.QueueItem item = q.items().get(i);
            boolean playing = audible && i == np && np >= 0;
            String sub = subtitleFor(item);
            String prep = prepKeyFor(item);
            sub = QueuePrepStatus.mergeSubtitle(sub, prep);
            rows[i] = new ThemedContextMenu.QueueRowSpec(
                    titleFor(item), sub, i == np, playing);
        }
        if (showFooter) {
            rows[size] = new ThemedContextMenu.QueueRowSpec(FOOTER_ADD_SONG, "", false, false);
        }
        return rows;
    }

    /** Prep registry key for a queue item (idle when unknown). 2026-07-21 */
    static String prepKeyFor(PlayQueue.QueueItem item) {
        if (item == null || item.file == null) return QueuePrepStatus.KEY_IDLE;
        return QueuePrepStatusRegistry.get(item.file);
    }

    private static String titleFor(PlayQueue.QueueItem item) {
        if (item == null) return "";
        if (item.kind == PlayQueue.ItemKind.MUSIC_FILE && item.file != null) {
            String n = item.file.getName();
            int dot = n.lastIndexOf('.');
            return dot > 0 ? n.substring(0, dot) : n;
        }
        if (item.episode != null && item.episode.title != null) return item.episode.title;
        if (item.reachMeta != null) return item.reachMeta;
        if (item.deezerMeta != null) return item.deezerMeta;
        if (item.fmLabel != null) return item.fmLabel;
        if (item.radioName != null) return item.radioName;
        return "";
    }

    private static String subtitleFor(PlayQueue.QueueItem item) {
        if (item == null) return "";
        if (item.kind == PlayQueue.ItemKind.MUSIC_FILE && item.file != null) {
            File parent = item.file.getParentFile();
            return parent != null ? parent.getName() : "";
        }
        if (item.podcastShowTitle != null) return item.podcastShowTitle;
        if (item.radioSubtitle != null) return item.radioSubtitle;
        return "";
    }
}
