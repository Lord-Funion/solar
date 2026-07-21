package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Open path must not block on bass-body decode — only reuse a ready file.
 * Layman: after stems land, play right away; skip the slow bass thicken step.
 * 2026-07-20
 */
public class StemBassBodyTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    /** Missing body → null so Stem Player can open without MediaCodec mix. 2026-07-20 */
    @Test
    public void existingOrNullWhenMissing() throws Exception {
        File dir = tmp.newFolder("stems");
        assertNull(StemBassBody.existingOrNull(dir));
    }

    /** Null dir → null (fail-open). 2026-07-20 */
    @Test
    public void existingOrNullWhenDirNull() {
        assertNull(StemBassBody.existingOrNull(null));
    }

    /** Tiny placeholder is not usable — treat as missing. 2026-07-20 */
    @Test
    public void existingOrNullRejectsTinyFile() throws Exception {
        File dir = tmp.newFolder("tiny");
        File body = new File(dir, StemBassBody.BASS_BODY_WAV);
        FileOutputStream fos = new FileOutputStream(body);
        try {
            fos.write(new byte[] {1, 2, 3});
        } finally {
            fos.close();
        }
        assertNull(StemBassBody.existingOrNull(dir));
    }

    /** Cached bass_body.wav is reused without regenerate. 2026-07-20 */
    @Test
    public void existingOrNullReturnsReadyFile() throws Exception {
        File dir = tmp.newFolder("ready");
        File body = new File(dir, StemBassBody.BASS_BODY_WAV);
        FileOutputStream fos = new FileOutputStream(body);
        try {
            byte[] chunk = new byte[1200];
            fos.write(chunk);
        } finally {
            fos.close();
        }
        File got = StemBassBody.existingOrNull(dir);
        assertEquals(body.getAbsolutePath(), got.getAbsolutePath());
        assertEquals(1200, got.length());
    }
}
