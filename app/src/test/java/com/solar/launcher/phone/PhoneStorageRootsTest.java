package com.solar.launcher.phone;

import org.junit.Test;

import java.io.File;

/**
 * 2026-07-20 — Internal/ + MicroSD/ children + writable-parent ladder under temp dirs.
 */
public class PhoneStorageRootsTest {

    private static final String INTERNAL_NAME = PhoneStorageRoots.INTERNAL_DIR;
    private static final String MICRO_NAME = PhoneStorageRoots.MICRO_SD_DIR;

    @Test
    public void ensureChildrenCreatesBoth() throws Exception {
        File tmp = File.createTempFile("solar_phone_stor", "");
        if (!tmp.delete()) throw new AssertionError("cleanup");
        if (!tmp.mkdirs()) throw new AssertionError("mkdir parent");
        try {
            if (!PhoneStorageRoots.ensureChildren(tmp)) {
                throw new AssertionError("ensureChildren failed");
            }
            File internal = PhoneStorageRoots.internalChild(tmp);
            File micro = PhoneStorageRoots.microSdChild(tmp);
            if (internal == null || !internal.isDirectory()) {
                throw new AssertionError("Internal missing");
            }
            if (micro == null || !micro.isDirectory()) {
                throw new AssertionError("MicroSD missing");
            }
            if (!INTERNAL_NAME.equals(internal.getName())) {
                throw new AssertionError("name " + internal.getName());
            }
            if (!MICRO_NAME.equals(micro.getName())) {
                throw new AssertionError("name " + micro.getName());
            }
            // Idempotent
            if (!PhoneStorageRoots.ensureChildren(tmp)) {
                throw new AssertionError("second ensure failed");
            }
        } finally {
            deleteTree(tmp);
        }
    }

    @Test
    public void nullParentFailsOpen() {
        if (PhoneStorageRoots.ensureChildren(null)) {
            throw new AssertionError("null must fail");
        }
        if (PhoneStorageRoots.internalChild(null) != null) {
            throw new AssertionError("internal null");
        }
        if (PhoneStorageRoots.microSdChild(null) != null) {
            throw new AssertionError("micro null");
        }
    }

    @Test
    public void sampleSizeCapsLongEdge() {
        int s = PhoneChromePrefs.sampleSizeFor(2048, 1536, 512);
        if (s < 4) throw new AssertionError("sample too small: " + s);
        // Decoded long edge roughly ≤ 512
        int longEdge = 2048 / s;
        if (longEdge > 512) throw new AssertionError("still too big: " + longEdge);
        if (PhoneChromePrefs.sampleSizeFor(400, 300, 512) != 1) {
            throw new AssertionError("small images stay 1");
        }
    }

    /**
     * 2026-07-20 — Writable candidate wins over fallbacks (shared SolarPhone path works).
     */
    @Test
    public void ladderPrefersWritableCandidate() throws Exception {
        File candidate = tempDir("cand");
        File ext = tempDir("ext");
        File priv = tempDir("priv");
        try {
            File win = PhoneStorageAccess.resolveWritableParent(candidate, ext, priv);
            if (win == null || !win.getAbsolutePath().equals(candidate.getAbsolutePath())) {
                throw new AssertionError("expected candidate, got " + win);
            }
            if (PhoneStorageAccess.usedFallback(candidate, win)) {
                throw new AssertionError("should not mark fallback");
            }
            if (!PhoneStorageRoots.ensureChildren(win)) {
                throw new AssertionError("children missing on winner");
            }
        } finally {
            deleteTree(candidate);
            deleteTree(ext);
            deleteTree(priv);
        }
    }

    /**
     * 2026-07-20 — Dead shared path (file-as-parent) falls to externalApp then private.
     * Mimics API 17 emu / scoped mkdirs failure on Environment/SolarPhone.
     */
    @Test
    public void ladderFallsToExternalWhenCandidateBlocked() throws Exception {
        // Candidate is a plain file — ensureChildren cannot turn it into Internal/MicroSD parent.
        File blocked = File.createTempFile("solar_blocked", ".bin");
        File ext = tempDir("ext_ok");
        File priv = tempDir("priv_ok");
        try {
            File win = PhoneStorageAccess.resolveWritableParent(blocked, ext, priv);
            if (win == null || !win.getAbsolutePath().equals(ext.getAbsolutePath())) {
                throw new AssertionError("expected externalApp, got " + win);
            }
            if (!PhoneStorageAccess.usedFallback(blocked, win)) {
                throw new AssertionError("must report fallback");
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            blocked.delete();
            deleteTree(ext);
            deleteTree(priv);
        }
    }

    /**
     * 2026-07-20 — When external also blocked, private filesDir rung wins.
     */
    @Test
    public void ladderFallsToPrivateWhenExternalBlocked() throws Exception {
        File blockedCand = File.createTempFile("solar_bc", ".bin");
        File blockedExt = File.createTempFile("solar_be", ".bin");
        File priv = tempDir("priv_last");
        try {
            File win = PhoneStorageAccess.resolveWritableParent(blockedCand, blockedExt, priv);
            if (win == null || !win.getAbsolutePath().equals(priv.getAbsolutePath())) {
                throw new AssertionError("expected private, got " + win);
            }
            if (!new File(win, INTERNAL_NAME).isDirectory()
                    || !new File(win, MICRO_NAME).isDirectory()) {
                throw new AssertionError("Internal/MicroSD missing under private");
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            blockedCand.delete();
            //noinspection ResultOfMethodCallIgnored
            blockedExt.delete();
            deleteTree(priv);
        }
    }

    @Test
    public void usedFallbackNulls() {
        if (!PhoneStorageAccess.usedFallback(null, null)) {
            throw new AssertionError("null winner is fallback");
        }
        File f = new File("/tmp/x");
        if (!PhoneStorageAccess.usedFallback(null, f)) {
            throw new AssertionError("null candidate is fallback");
        }
    }

    /**
     * 2026-07-20 — SDK gate for runtime READ/WRITE: only API 23–28.
     */
    @Test
    public void runtimePermSdkGate() {
        if (PhoneStorageRuntimePerms.sdkWantsLegacyRuntimePerms(17)) {
            throw new AssertionError("API 17 no runtime");
        }
        if (PhoneStorageRuntimePerms.sdkWantsLegacyRuntimePerms(22)) {
            throw new AssertionError("API 22 no runtime");
        }
        if (!PhoneStorageRuntimePerms.sdkWantsLegacyRuntimePerms(23)) {
            throw new AssertionError("API 23 wants runtime");
        }
        if (!PhoneStorageRuntimePerms.sdkWantsLegacyRuntimePerms(28)) {
            throw new AssertionError("API 28 wants runtime");
        }
        if (PhoneStorageRuntimePerms.sdkWantsLegacyRuntimePerms(29)) {
            throw new AssertionError("API 29+ no legacy runtime");
        }
    }

    private static File tempDir(String prefix) throws Exception {
        File tmp = File.createTempFile("solar_" + prefix + "_", "");
        if (!tmp.delete()) throw new AssertionError("cleanup " + prefix);
        if (!tmp.mkdirs()) throw new AssertionError("mkdir " + prefix);
        return tmp;
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteTree(k);
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
