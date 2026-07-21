package com.solar.launcher.stem;

import android.content.Context;
import android.content.SharedPreferences;

import com.solar.launcher.AudioTagWriter;
import com.solar.launcher.AudioTags;

import java.io.File;

/**
 * 2026-07-19 — Copy originating tags onto Instrumental / Acapella sibling files.
 * Layman: peeled file keeps the song’s artist/album/cover and a titled “(Instrumental)”.
 * Technical: AudioTags.read origin (with art) → SoloStemTitles → AudioTagWriter.tryEmbed.
 * Reversal: skip embed; rely on NP display override only.
 */
public final class SoloStemTagWriter {

    private SoloStemTagWriter() {}

    /**
     * Write title/artist/album/cover from origin into the solo file (best-effort).
     * 2026-07-19 — Passes origin embeddedArt so NP paints the real album, not instrumental.jpg.
     * Was: READ_SKIP_EMBEDDED_ART + null coverJpeg. Reversal: skip art arg again.
     */
    public static void writeFromOrigin(Context ctx, File origin, File soloFile, SoloMode mode) {
        if (origin == null || !origin.isFile() || soloFile == null || !soloFile.isFile()
                || mode == null) {
            return;
        }
        if (!AudioTagWriter.supportsEmbedding(soloFile)) return;
        SharedPreferences prefs = null;
        try {
            if (ctx != null) {
                prefs = ctx.getSharedPreferences(LalalAccount.PREFS_NAME, 0);
            }
        } catch (Exception ignored) {}
        // Read with art so we can stamp the same cover onto the peel. 2026-07-19
        AudioTags.Info meta = AudioTags.read(origin, prefs);
        String baseTitle = meta.title != null && meta.title.trim().length() > 0
                ? meta.title.trim()
                : SoloStemPaths.trackBaseName(origin);
        meta.title = SoloStemTitles.displayTitle(baseTitle, mode);
        byte[] coverJpeg = meta.embeddedArt;
        AudioTagWriter.tryEmbed(soloFile, meta, coverJpeg);
    }

    /**
     * Tag both sibling solos when present after ensure.
     * 2026-07-19
     */
    public static void writeBothIfPresent(Context ctx, File origin) {
        if (origin == null) return;
        File instr = SoloStemPaths.findReadySibling(origin, SoloMode.INSTRUMENTAL);
        if (instr != null) writeFromOrigin(ctx, origin, instr, SoloMode.INSTRUMENTAL);
        File acap = SoloStemPaths.findReadySibling(origin, SoloMode.ACAPELLA);
        if (acap != null) writeFromOrigin(ctx, origin, acap, SoloMode.ACAPELLA);
    }
}
