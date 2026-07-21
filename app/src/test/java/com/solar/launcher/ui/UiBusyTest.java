package com.solar.launcher.ui;

import org.junit.After;
import org.junit.Test;

/** 2026-07-18 — UiBusy refcount for status throbber. */
public class UiBusyTest {

    @After
    public void tearDown() {
        UiBusy.resetForTest();
    }

    @Test
    public void beginEndNesting() {
        if (UiBusy.isBusy()) throw new AssertionError("start idle");
        UiBusy.begin(UiBusy.REASON_TRACK_CHANGE);
        if (!UiBusy.isBusy(UiBusy.REASON_TRACK_CHANGE)) throw new AssertionError("busy");
        UiBusy.begin(UiBusy.REASON_TRACK_CHANGE);
        if (UiBusy.countForTest(UiBusy.REASON_TRACK_CHANGE) != 2) {
            throw new AssertionError("nested count");
        }
        UiBusy.end(UiBusy.REASON_TRACK_CHANGE);
        if (!UiBusy.isBusy(UiBusy.REASON_TRACK_CHANGE)) throw new AssertionError("still busy");
        UiBusy.end(UiBusy.REASON_TRACK_CHANGE);
        if (UiBusy.isBusy()) throw new AssertionError("idle after ends");
    }

    @Test
    public void mediaBufferAndDownloadReasons() {
        UiBusy.begin(UiBusy.REASON_MEDIA_BUFFER);
        UiBusy.begin(UiBusy.REASON_DOWNLOAD);
        if (!UiBusy.isBusy(UiBusy.REASON_MEDIA_BUFFER)) throw new AssertionError("buffer");
        if (!UiBusy.isBusy(UiBusy.REASON_DOWNLOAD)) throw new AssertionError("download");
        UiBusy.clear(UiBusy.REASON_MEDIA_BUFFER);
        if (UiBusy.isBusy(UiBusy.REASON_MEDIA_BUFFER)) throw new AssertionError("buffer cleared");
        if (!UiBusy.isBusy(UiBusy.REASON_DOWNLOAD)) throw new AssertionError("download remains");
        UiBusy.clear(UiBusy.REASON_DOWNLOAD);
        if (UiBusy.isBusy()) throw new AssertionError("idle");
    }

    /** 2026-07-20 — Cold-start throbber reason must arm/clear like other busy tokens. */
    @Test
    public void startupReason() {
        UiBusy.begin(UiBusy.REASON_STARTUP);
        if (!UiBusy.isBusy(UiBusy.REASON_STARTUP)) throw new AssertionError("startup busy");
        UiBusy.clear(UiBusy.REASON_STARTUP);
        if (UiBusy.isBusy(UiBusy.REASON_STARTUP)) throw new AssertionError("startup cleared");
    }

    /** 2026-07-20 — Stem prep shares status throbber. */
    @Test
    public void stemPrepareReason() {
        UiBusy.begin(UiBusy.REASON_STEM_PREPARE);
        if (!UiBusy.isBusy(UiBusy.REASON_STEM_PREPARE)) throw new AssertionError("stem busy");
        UiBusy.clear(UiBusy.REASON_STEM_PREPARE);
        if (UiBusy.isBusy(UiBusy.REASON_STEM_PREPARE)) throw new AssertionError("stem cleared");
    }
}
