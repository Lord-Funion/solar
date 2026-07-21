package com.solar.launcher.stem;

import java.io.File;

/**
 * Lossless duration warning — warn, never block stem prep on FLAC/ALAC/WAV/AIFF.
 * Layman: big uncompressed files can take ages to split; we ask once before cooking.
 * Technical: extension sniff; ready stems skip dialog; Continue proceeds / Cancel aborts cook only.
 * Was: always start Lalal with no warn. Reversal: skip shouldWarnLossless checks.
 * 2026-07-21
 */
public final class StemMixLossless {
    private StemMixLossless() {}

    /**
     * True when path looks like lossless audio that can make stem prep very slow.
     * Layman: FLAC, Apple Lossless, WAV, AIFF — big files, long waits.
     * 2026-07-21
     */
    public static boolean isLosslessFile(File file) {
        if (file == null) return false;
        return isLosslessName(file.getName());
    }

    /** Extension check (case-insensitive). 2026-07-21 */
    public static boolean isLosslessName(String name) {
        if (name == null || name.length() < 4) return false;
        String n = name.toLowerCase();
        return n.endsWith(".flac")
                || n.endsWith(".alac")
                || n.endsWith(".wav")
                || n.endsWith(".aiff")
                || n.endsWith(".aif")
                || n.endsWith(".wv")
                || n.endsWith(".ape");
    }

    /**
     * Show confirm when prep would start and stems are not already ready.
     * Layman: only nag if we still need to cook and the file is lossless.
     * 2026-07-21
     */
    public static boolean shouldWarnLossless(File file, boolean stemsAlreadyReady) {
        if (stemsAlreadyReady) return false;
        return isLosslessFile(file);
    }

    /**
     * True if any file in the batch needs the Pre-Save lossless confirm.
     * 2026-07-21
     */
    public static boolean shouldWarnLosslessBatch(File[] files, boolean[] stemsReadyByIndex) {
        if (files == null) return false;
        for (int i = 0; i < files.length; i++) {
            boolean ready = stemsReadyByIndex != null && i < stemsReadyByIndex.length
                    && stemsReadyByIndex[i];
            if (shouldWarnLossless(files[i], ready)) return true;
        }
        return false;
    }

    /** Confirm title copy (short for 480×360). 2026-07-21 */
    public static String warnTitle() {
        return "Lossless file";
    }

    /** Confirm body — Continue anyway is the proceed action. 2026-07-21 */
    public static String warnBody() {
        return "Stem prep may take too long on lossless files.";
    }

    public static String continueLabel() {
        return "Continue anyway";
    }

    public static String cancelLabel() {
        return "Cancel";
    }
}
