package com.solar.launcher;

import android.view.KeyEvent;

/**
 * 2026-07-19 — Shared helpers for “Search…” rows atop long library lists.
 * Layman: every long catalog gets a search shortcut at the top so you type instead of spinning.
 * Technical: adapters offset by {@link #HEADER_COUNT}; OK opens the existing wheel keyboard.
 * Reversal: remove header offsets and call sites; browse-only lists return.
 */
public final class HierarchySearchChrome {

    /** One Search… row at index 0 on ListView adapters. */
    public static final int HEADER_COUNT = 1;

    /** View type for the search header (avoid recycling into song/category rows). */
    public static final int VIEW_TYPE_SEARCH = 0;
    public static final int VIEW_TYPE_ITEM = 1;

    private HierarchySearchChrome() {}

    /** True when adapter position is the Search… header. */
    public static boolean isSearchPosition(int position) {
        return position == 0;
    }

    /** Map ListView position → underlying data index (−1 for the search header). */
    public static int dataIndex(int position) {
        if (position < HEADER_COUNT) return -1;
        return position - HEADER_COUNT;
    }

    /**
     * 2026-07-20 — Map data index → ListView position when Search… sits at row 0.
     * Layman: put the highlight back on the same artist/song after you leave and return.
     * Was: callers used data index as ListView pos → landed one row above.
     */
    public static int adapterPosition(int dataIndex) {
        if (dataIndex < 0) return -1;
        return dataIndex + HEADER_COUNT;
    }

    /**
     * 2026-07-20 — Adapter pos → data index, or leave alone when no Search chrome.
     * Layman: context menus and play use the song under the highlight, not the next one.
     */
    public static int dataIndexOrNeg(int adapterPosition, boolean hasHeader) {
        if (!hasHeader) return adapterPosition;
        return dataIndex(adapterPosition);
    }

    /**
     * 2026-07-20 — True when ListView count is data size + Search… header.
     * Technical: SongListAdapter / CategoryListAdapter getCount check without casting.
     */
    public static boolean hasHeader(int adapterCount, int dataSize) {
        if (dataSize < 0) dataSize = 0;
        return adapterCount == countWithHeader(dataSize);
    }

    /** Adapter getCount = data size + header. */
    public static int countWithHeader(int dataSize) {
        if (dataSize < 0) dataSize = 0;
        return dataSize + HEADER_COUNT;
    }

    /**
     * 2026-07-20 — List focus → song-data index when Search… may sit at row 0.
     * Layman: the Search shortcut doesn’t count as a song, so context menus skip it.
     * Technical: if adapterCount == dataSize+HEADER, use {@link #dataIndex}; else 1:1.
     * Was: callers used raw ListView position → Instrumental/Acapella hit the next track.
     * Reversal: return listPos unchanged (ignore adapterCount).
     */
    public static int songDataIndex(int listPos, int dataSize, int adapterCount) {
        if (listPos < 0 || dataSize <= 0) return -1;
        if (adapterCount == countWithHeader(dataSize)) {
            int di = dataIndex(listPos);
            return (di >= 0 && di < dataSize) ? di : -1;
        }
        return listPos < dataSize ? listPos : -1;
    }

    /**
     * 2026-07-19 — True when this key should activate the focused Search… row (center / OK).
     * Layman: press the middle button on Search to open the keyboard.
     */
    public static boolean isConfirmKey(int keyCode) {
        return Y1InputKeys.isCenterKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }
}
