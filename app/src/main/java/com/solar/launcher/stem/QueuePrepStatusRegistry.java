package com.solar.launcher.stem;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * In-process map of track path → prep marquee key for queue rows.
 * Layman: remembers which songs are still cooking so the queue can show it.
 * Technical: static HashMap; cleared when idle; not persisted across process death.
 * Was: no shared prep state for overlay/MainActivity. Reversal: always idle.
 * 2026-07-21
 */
public final class QueuePrepStatusRegistry {
    private static final Map<String, String> BY_PATH = new HashMap<String, String>();

    private QueuePrepStatusRegistry() {}

    /** Set prep key for a file (null/idle clears). 2026-07-21 */
    public static void set(File file, String key) {
        if (file == null) return;
        setPath(file.getAbsolutePath(), key);
    }

    public static void setPath(String path, String key) {
        if (path == null || path.length() == 0) return;
        synchronized (BY_PATH) {
            if (key == null || key.length() == 0 || QueuePrepStatus.KEY_IDLE.equals(key)) {
                BY_PATH.remove(path);
            } else {
                BY_PATH.put(path, key);
            }
        }
    }

    public static String get(File file) {
        if (file == null) return QueuePrepStatus.KEY_IDLE;
        return getPath(file.getAbsolutePath());
    }

    public static String getPath(String path) {
        if (path == null) return QueuePrepStatus.KEY_IDLE;
        synchronized (BY_PATH) {
            String k = BY_PATH.get(path);
            return k != null ? k : QueuePrepStatus.KEY_IDLE;
        }
    }

    /** True if any track is busy (jam-face throbber mirror). 2026-07-21 */
    public static boolean anyBusy() {
        synchronized (BY_PATH) {
            return !BY_PATH.isEmpty();
        }
    }

    /** Test / session teardown. 2026-07-21 */
    public static void clearAll() {
        synchronized (BY_PATH) {
            BY_PATH.clear();
        }
    }
}
