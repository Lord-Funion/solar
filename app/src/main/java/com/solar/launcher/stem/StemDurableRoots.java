package com.solar.launcher.stem;

import java.io.File;

/**
 * Pick a durable stem vault root — internal MMC first, then MicroSD, then app cache.
 * Layman: keep long-term stem copies on the faster chip when it has room.
 * Technical: {@link #pick} is pure/injectable for unit tests; no Primary-pref bias.
 * Was: {@code durableStemDir} overflow via {@code getNewMediaRoot} (often MicroSD).
 * Reversal: restore getNewMediaRoot overflow; delete this helper.
 * 2026-07-21
 */
public final class StemDurableRoots {

    private StemDurableRoots() {}

    /**
     * Build {@code …/Android/data/<pkg>/cache/lalal_stems} under a public volume root.
     * Layman: app’s stem shelf on that storage card/chip.
     * 2026-07-21
     */
    public static File volumeVault(File volumeRoot, String packageName) {
        if (volumeRoot == null || packageName == null || packageName.length() == 0) return null;
        return new File(volumeRoot, "Android/data/" + packageName + "/cache/lalal_stems");
    }

    /**
     * Choose which vault directory to use for a projected byte budget.
     * Layman: try internal chip, then SD card, then the app’s own cache folder.
     * Technical: first root with {@link com.solar.launcher.StreamCacheRoot#hasSpace}; app last.
     * 2026-07-21
     */
    public static File pick(File internalVault, File microVault, File appVault, long needBytes) {
        if (com.solar.launcher.StreamCacheRoot.hasSpace(internalVault, needBytes)) {
            return internalVault;
        }
        if (com.solar.launcher.StreamCacheRoot.hasSpace(microVault, needBytes)) {
            return microVault;
        }
        return appVault;
    }

    /** Live multistem budget (~80 MiB). 2026-07-21 */
    public static long needBytesLive() {
        return 80L * 1024L * 1024L;
    }

    /** Premix budget (~48 MiB). 2026-07-21 */
    public static long needBytesPremix() {
        return 48L * 1024L * 1024L;
    }

    /** Band for premix flag. 2026-07-21 */
    public static long needBytes(boolean premix) {
        return premix ? needBytesPremix() : needBytesLive();
    }
}
