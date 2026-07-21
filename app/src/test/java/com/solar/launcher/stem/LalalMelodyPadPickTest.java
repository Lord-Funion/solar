package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 2026-07-20 — Classico-style collapse: named acoustic vs residual Melody pick.
 * Writes NDJSON to session debug log for hypothesis A.
 */
public class LalalMelodyPadPickTest {

    private static final String DEBUG_LOG =
            "/home/deck/Documents/Cursor Workspaces/TheSolarProject/solar/.cursor/debug-75a361.log";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /**
     * Acoustic+vocals track shape: residual preferred today → guitar discarded.
     * 2026-07-20
     */
    @Test
    public void classicoShapePrefersResidualOverAcoustic() throws Exception {
        File dir = tmp.newFolder("classico_stems");
        List<LalalClient.StemFile> raw = new ArrayList<LalalClient.StemFile>();
        raw.add(stem(dir, "vocals", 0, 800_000));
        raw.add(stem(dir, "drum", 1, 1200)); // near-empty pad
        raw.add(stem(dir, "bass", 2, 1200));
        raw.add(stem(dir, "piano", 3, 1200));
        raw.add(stem(dir, "electric_guitar", 3, 1200));
        raw.add(stem(dir, "acoustic_guitar", 3, 900_000)); // the real guitar
        raw.add(stem(dir, "no_multistem", 3, 5000)); // leftover scrap
        List<LalalClient.StemFile> out = LalalClient.collapseToOnePadPerZone(raw);
        assertEquals(4, out.size());
        String picked = null;
        long pickedBytes = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i).zone == 3) {
                picked = out.get(i).id;
                pickedBytes = out.get(i).file.length();
            }
        }
        // #region agent log
        JSONObject d = new JSONObject();
        d.put("pickedId", picked);
        d.put("pickedBytes", pickedBytes);
        d.put("acousticBytes", 900_000);
        d.put("residualBytes", 5000);
        d.put("discardedGuitar", "no_multistem".equals(picked));
        JSONObject o = new JSONObject();
        o.put("sessionId", "75a361");
        o.put("timestamp", System.currentTimeMillis());
        o.put("location", "LalalMelodyPadPickTest.classicoShape");
        o.put("message", "unit: Classico-shaped collapse pick");
        o.put("hypothesisId", "A");
        o.put("runId", "unit-classico-shape");
        o.put("data", d);
        FileWriter w = new FileWriter(DEBUG_LOG, true);
        w.write(o.toString());
        w.write('\n');
        w.close();
        // #endregion
        // Documents current (broken for Classico) preference — residual wins. 2026-07-20
        assertEquals("no_multistem", picked);
        assertTrue(pickedBytes < 900_000);
    }

    private static LalalClient.StemFile stem(File dir, String id, int zone, int bytes)
            throws Exception {
        File f = new File(dir, id + ".mp3");
        byte[] buf = new byte[Math.max(100, bytes)];
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(buf);
        } finally {
            out.close();
        }
        return new LalalClient.StemFile(id, id, f, zone);
    }
}
