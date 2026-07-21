package com.solar.launcher.stem;

import java.io.File;
import java.util.List;

/**
 * Melody catch-all for NP Stems — premix so all pads up ≈ full song.
 * Layman: piano/guitars/leftovers blend into one Melody pad so nothing is dropped.
 * Technical: force premix when zone-3 has multiple parts; melody.wav already = catch-all.
 * Was: collapseToOnePadPerZone (drops named others). Reversal: always collapse only.
 * 2026-07-21
 */
public final class NpStemMelodyCatchAll {

    private NpStemMelodyCatchAll() {}

    /**
     * NP Stems path always prefers Melody premix (not experimental opt-in).
     * 2026-07-21
     */
    public static boolean forcePremixForNp() {
        return true;
    }

    /**
     * Count Melody/Other (zone 3) files in a stem list.
     * 2026-07-21
     */
    public static int melodyPartCount(List<LalalClient.StemFile> stems) {
        if (stems == null) return 0;
        int n = 0;
        for (int i = 0; i < stems.size(); i++) {
            LalalClient.StemFile s = stems.get(i);
            if (s != null && s.zone == 3 && s.file != null && s.file.isFile()) n++;
        }
        return n;
    }

    /**
     * True when Melody is already a single premixed WAV catch-all.
     * 2026-07-21
     */
    public static boolean hasPremixedMelody(List<LalalClient.StemFile> stems) {
        if (stems == null) return false;
        for (int i = 0; i < stems.size(); i++) {
            LalalClient.StemFile s = stems.get(i);
            if (s == null || s.file == null) continue;
            if (s.zone != 3) continue;
            String name = s.file.getName();
            if (StemOtherPremix.MELODY_WAV.equals(name)) return true;
        }
        return false;
    }

    /**
     * Resolve pads for NP playback — premix multi-Melody so sum ≈ whole song.
     * Layman: glue leftover instruments into one Melody strip before play.
     * Technical: {@link LalalClient#premixToFourPadsStatic} when &gt;1 zone-3; else collapse.
     * 2026-07-21
     */
    public static List<LalalClient.StemFile> padsForPlayback(List<LalalClient.StemFile> raw,
            File workOrStemDir) {
        if (raw == null || raw.isEmpty()) return raw;
        if (hasPremixedMelody(raw) || melodyPartCount(raw) <= 1) {
            return LalalClient.collapseToOnePadPerZone(raw);
        }
        if (workOrStemDir == null) {
            return LalalClient.collapseToOnePadPerZone(raw);
        }
        try {
            return LalalClient.premixToFourPadsStatic(raw, workOrStemDir, null, null);
        } catch (Exception e) {
            // Fail-open: one Melody pick so pads still load. 2026-07-21
            return LalalClient.collapseToOnePadPerZone(raw);
        }
    }
}
