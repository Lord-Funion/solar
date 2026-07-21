package com.solar.launcher;

import android.view.KeyEvent;

/**
 * Select all / Clear all headers atop Stem + Mix pick song lists.
 * Layman: two shortcut rows at the top of the mark list — grab everything or wipe marks.
 * Technical: adapter offset {@link #HEADER_COUNT}; mirrors {@link HierarchySearchChrome}.
 * Was: Search… only (or none) on pick lists. Reversal: drop headers; HEADER_COUNT=0 callers.
 * 2026-07-21 Stems/Mix sanity
 */
public final class StemMixPickChrome {

    /** Select all @0, Clear all @1. */
    public static final int HEADER_COUNT = 2;

    public static final int VIEW_TYPE_SELECT_ALL = 0;
    public static final int VIEW_TYPE_CLEAR_ALL = 1;
    public static final int VIEW_TYPE_ITEM = 2;

    private StemMixPickChrome() {}

    /** True when adapter position is Select all. 2026-07-21 */
    public static boolean isSelectAllPosition(int position) {
        return position == 0;
    }

    /** True when adapter position is Clear all. 2026-07-21 */
    public static boolean isClearAllPosition(int position) {
        return position == 1;
    }

    /** True when position is either bulk header. 2026-07-21 */
    public static boolean isHeaderPosition(int position) {
        return position >= 0 && position < HEADER_COUNT;
    }

    /**
     * Map ListView position → song data index (−1 for headers).
     * 2026-07-21
     */
    public static int dataIndex(int position) {
        if (position < HEADER_COUNT) return -1;
        return position - HEADER_COUNT;
    }

    /** Data index → ListView position when headers sit at 0..1. 2026-07-21 */
    public static int adapterPosition(int dataIndex) {
        if (dataIndex < 0) return -1;
        return dataIndex + HEADER_COUNT;
    }

    /** Adapter getCount = data size + headers. 2026-07-21 */
    public static int countWithHeader(int dataSize) {
        if (dataSize < 0) dataSize = 0;
        return dataSize + HEADER_COUNT;
    }

    /** True when count matches data + headers. 2026-07-21 */
    public static boolean hasHeader(int adapterCount, int dataSize) {
        if (dataSize < 0) dataSize = 0;
        return adapterCount == countWithHeader(dataSize);
    }

    /**
     * List focus → song-data index when Select/Clear headers may sit at top.
     * 2026-07-21
     */
    public static int songDataIndex(int listPos, int dataSize, int adapterCount) {
        if (listPos < 0 || dataSize <= 0) return -1;
        if (adapterCount == countWithHeader(dataSize)) {
            int di = dataIndex(listPos);
            return (di >= 0 && di < dataSize) ? di : -1;
        }
        return listPos < dataSize ? listPos : -1;
    }

    /** Center / OK activates a focused Select/Clear header. 2026-07-21 */
    public static boolean isConfirmKey(int keyCode) {
        return Y1InputKeys.isCenterKey(keyCode)
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
    }
}
