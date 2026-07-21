package com.solar.launcher.stem;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 2026-07-19 — Sibling hidden folders for Instrumental / Acapella next to the source track.
 * Layman: Song.mp3 keeps a matching file under .instrumentals / .acapellas beside it.
 * Technical: parent/.instrumentals/&lt;basename&gt;.ext — library skips these folder names.
 * Was: app-cache stem_solo/lalal/v1_* only. Reversal: use LalalClient.soloDir alone.
 */
public final class SoloStemPaths {

    public static final String DIR_INSTRUMENTALS = ".instrumentals";
    public static final String DIR_ACAPELLAS = ".acapellas";
    private static final String NOMEDIA = ".nomedia";

    private SoloStemPaths() {}

    /**
     * Folder name for this solo mode (.instrumentals / .acapellas).
     * 2026-07-19
     */
    public static String siblingDirName(SoloMode mode) {
        if (mode == SoloMode.ACAPELLA) return DIR_ACAPELLAS;
        return DIR_INSTRUMENTALS;
    }

    /**
     * Basename of track without extension (Song from Song.mp3).
     * 2026-07-19
     */
    public static String trackBaseName(File track) {
        if (track == null) return "";
        String name = track.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Sibling dir next to the track: …/.instrumentals or …/.acapellas.
     * 2026-07-19
     */
    public static File siblingDir(File track, SoloMode mode) {
        if (track == null || mode == null) return null;
        File parent = track.getParentFile();
        if (parent == null) return null;
        return new File(parent, siblingDirName(mode));
    }

    /**
     * Target file for a solo mode — same basename, preferred .mp3 (or .wav for bake).
     * 2026-07-19
     */
    public static File siblingSoloFile(File track, SoloMode mode) {
        return siblingSoloFile(track, mode, "mp3");
    }

    /**
     * Sibling solo file with explicit extension (no leading dot).
     * 2026-07-19
     */
    public static File siblingSoloFile(File track, SoloMode mode, String ext) {
        File dir = siblingDir(track, mode);
        if (dir == null) return null;
        String base = trackBaseName(track);
        if (base.length() == 0) return null;
        String e = ext != null && ext.length() > 0 ? ext : "mp3";
        if (e.startsWith(".")) e = e.substring(1);
        return new File(dir, base + "." + e);
    }

    /**
     * Prefer .mp3 then .wav under the sibling folder for this mode.
     * 2026-07-19
     */
    public static File findReadySibling(File track, SoloMode mode) {
        if (track == null || !track.isFile() || mode == null) return null;
        File mp3 = siblingSoloFile(track, mode, "mp3");
        if (mp3 != null && mp3.isFile() && mp3.length() >= 100) return mp3;
        File wav = siblingSoloFile(track, mode, "wav");
        if (wav != null && wav.isFile() && wav.length() >= 100) return wav;
        return null;
    }

    /**
     * Ensure sibling folder exists and has .nomedia so media scanners stay away.
     * 2026-07-19
     */
    public static File ensureSiblingDir(File track, SoloMode mode) {
        File dir = siblingDir(track, mode);
        if (dir == null) return null;
        if (!dir.isDirectory()) dir.mkdirs();
        touchNomedia(dir);
        return dir;
    }

    /**
     * True when this path sits under .instrumentals or .acapellas.
     * 2026-07-19
     */
    public static boolean isSiblingSoloPath(File f) {
        if (f == null) return false;
        File cur = f;
        while (cur != null) {
            String name = cur.getName();
            if (DIR_INSTRUMENTALS.equals(name) || DIR_ACAPELLAS.equals(name)) return true;
            File parent = cur.getParentFile();
            if (parent == null || parent.equals(cur)) break;
            cur = parent;
        }
        return false;
    }

    /**
     * Recover the library track from a sibling solo file (same basename beside the folder).
     * Layman: from .instrumentals/Song.mp3 find Song.mp3 next door.
     * 2026-07-19
     */
    public static File originatingTrackFromSolo(File soloFile) {
        if (soloFile == null || !soloFile.isFile()) return null;
        File soloDir = soloFile.getParentFile();
        if (soloDir == null) return null;
        String dirName = soloDir.getName();
        if (!DIR_INSTRUMENTALS.equals(dirName) && !DIR_ACAPELLAS.equals(dirName)) return null;
        File musicParent = soloDir.getParentFile();
        if (musicParent == null) return null;
        String base = trackBaseName(soloFile);
        if (base.length() == 0) return null;
        String[] exts = {".mp3", ".flac", ".m4a", ".wav", ".ogg", ".opus", ".aac"};
        for (int i = 0; i < exts.length; i++) {
            File candidate = new File(musicParent, base + exts[i]);
            if (candidate.isFile() && candidate.length() >= 100) return candidate;
        }
        return null;
    }

    private static void touchNomedia(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        File nomedia = new File(dir, NOMEDIA);
        if (nomedia.exists()) return;
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(nomedia);
            out.write(new byte[0]);
        } catch (Exception ignored) {
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }
}
