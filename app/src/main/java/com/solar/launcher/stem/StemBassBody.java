package com.solar.launcher.stem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Offline bass body — low-pass mix of non-bass stems so Bass pad has weight when stem is thin.
 * Layman: bleed low thump from the rest of the song into the Bass knob.
 * Technical: decode→mix like StemOtherPremix, then one-pole LPF @ ~150 Hz → bass_body.wav.
 * Was: bass.mp3 only. Reversal: skip body file; mixer ignores missing body.
 * 2026-07-19
 */
public final class StemBassBody {
    public static final String BASS_BODY_WAV = "bass_body.wav";
    /** ~150 Hz at 22.05 kHz — soft low-pass for body. */
    private static final float LP_ALPHA = 0.042f;
    public static final float BODY_GAIN_K = 0.7f;

    private StemBassBody() {}

    /**
     * Peek for a ready bass_body.wav — never decode or write.
     * Layman: if the thick-bass file is already there, use it; otherwise skip.
     * Was: open path called ensure() (full MediaCodec mix) and stuck on “Bass body…”.
     * Reversal: call ensure(stemDir, sources, cancelled) again from startJob.
     * 2026-07-20
     */
    public static File existingOrNull(File stemDir) {
        if (stemDir == null) return null;
        File out = new File(stemDir, BASS_BODY_WAV);
        if (out.isFile() && out.length() > 1000) return out;
        return null;
    }

    /**
     * Build bass_body.wav from vocals+drums+melody files in stem dir (or explicit list).
     * Returns existing file if already present and non-empty.
     * Heavy on Y1/Y2 — do not call from Stem Player open path (use existingOrNull).
     * 2026-07-19
     */
    public static File ensure(File stemDir, List<File> nonBassSources, AtomicBoolean cancelled)
            throws Exception {
        if (stemDir == null) throw new IOException("stem dir missing");
        File out = existingOrNull(stemDir);
        if (out != null) return out;
        out = new File(stemDir, BASS_BODY_WAV);
        List<File> ok = new ArrayList<File>();
        if (nonBassSources != null) {
            for (int i = 0; i < nonBassSources.size(); i++) {
                File f = nonBassSources.get(i);
                if (f != null && f.isFile() && f.length() >= 100) ok.add(f);
            }
        }
        if (ok.isEmpty()) throw new IOException("No sources for bass body");
        // Mix to temp wav then LPF into out.
        File mixed = new File(stemDir, "bass_body_raw.wav");
        try {
            StemOtherPremix.mixToMonoWav(ok, mixed, cancelled, null);
            lowPassWav(mixed, out, cancelled);
            return out;
        } finally {
            try {
                if (mixed.exists()) mixed.delete();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Collect non-bass stem files from a ready stem list.
     * 2026-07-19
     */
    public static List<File> nonBassFiles(List<LalalClient.StemFile> stems) {
        List<File> out = new ArrayList<File>();
        if (stems == null) return out;
        for (int i = 0; i < stems.size(); i++) {
            LalalClient.StemFile s = stems.get(i);
            if (s == null || s.file == null || !s.file.isFile()) continue;
            if (s.zone == 2) continue; // skip bass pad
            out.add(s.file);
        }
        return out;
    }

    /** One-pole LPF on mono 16-bit WAV @ StemOtherPremix.OUT_HZ. */
    static void lowPassWav(File inWav, File outWav, AtomicBoolean cancelled) throws IOException {
        if (inWav == null || !inWav.isFile()) throw new IOException("raw wav missing");
        FileInputStream fin = null;
        BufferedInputStream bin = null;
        FileOutputStream fout = null;
        DataOutputStream dout = null;
        try {
            fin = new FileInputStream(inWav);
            bin = new BufferedInputStream(fin);
            // Skip 44-byte WAV header (StemOtherPremix writes standard PCM).
            long skipped = 0;
            while (skipped < 44) {
                long n = bin.skip(44 - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            File parent = outWav.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmpPcm = new File(outWav.getAbsolutePath() + ".lp.pcm");
            fout = new FileOutputStream(tmpPcm);
            BufferedOutputStream bout = new BufferedOutputStream(fout);
            byte[] buf = new byte[8192];
            float y = 0f;
            int totalSamples = 0;
            while (true) {
                if (cancelled != null && cancelled.get()) throw new IOException("Cancelled");
                int n = bin.read(buf);
                if (n < 2) break;
                if ((n & 1) == 1) n--;
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN);
                byte[] outb = new byte[n];
                ByteBuffer ob = ByteBuffer.wrap(outb).order(ByteOrder.LITTLE_ENDIAN);
                while (bb.remaining() >= 2) {
                    short x = bb.getShort();
                    y = y + LP_ALPHA * (x - y);
                    short o = (short) Math.max(-32768, Math.min(32767, Math.round(y)));
                    ob.putShort(o);
                    totalSamples++;
                }
                bout.write(outb, 0, n);
            }
            bout.flush();
            bout.close();
            fout.close();
            fout = null;
            StemOtherPremix.pcmToWav(tmpPcm, totalSamples, outWav);
            try {
                tmpPcm.delete();
            } catch (Exception ignored) {}
        } finally {
            if (bin != null) try { bin.close(); } catch (Exception ignored) {}
            if (fin != null) try { fin.close(); } catch (Exception ignored) {}
            if (dout != null) try { dout.close(); } catch (Exception ignored) {}
            if (fout != null) try { fout.close(); } catch (Exception ignored) {}
        }
    }
}
