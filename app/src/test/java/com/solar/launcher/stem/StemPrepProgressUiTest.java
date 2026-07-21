package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Stem prep progress UI helpers — throttle + phase map. 2026-07-21 */
public class StemPrepProgressUiTest {

    @Test
    public void registryKeyForPhase() {
        assertEquals(QueuePrepStatus.KEY_IDLE, StemPrepProgressUi.registryKeyForPhase("ready"));
        assertEquals(QueuePrepStatus.KEY_DOWNLOADING, StemPrepProgressUi.registryKeyForPhase("upload"));
        assertEquals(QueuePrepStatus.KEY_DOWNLOADING, StemPrepProgressUi.registryKeyForPhase("download"));
        assertEquals(QueuePrepStatus.KEY_VOCALS, StemPrepProgressUi.registryKeyForPhase("split"));
        assertEquals(QueuePrepStatus.KEY_STEMS, StemPrepProgressUi.registryKeyForPhase("mix"));
    }

    @Test
    public void toastMilestonesThrottle() {
        assertTrue(StemPrepProgressUi.shouldToastMilestone(null, -1, "start", 0));
        assertTrue(StemPrepProgressUi.shouldToastMilestone("start", 0, "upload", 5));
        assertFalse(StemPrepProgressUi.shouldToastMilestone("upload", 5, "upload", 10));
        assertTrue(StemPrepProgressUi.shouldToastMilestone("upload", 5, "upload", 25));
        assertTrue(StemPrepProgressUi.shouldToastMilestone("upload", 25, "download", 0));
        assertTrue(StemPrepProgressUi.shouldToastMilestone("download", 90, "ready", 100));
    }

    @Test
    public void statusAndMarquee() {
        assertTrue(StemPrepProgressUi.statusLine("split", 40, "vocals").contains("40%"));
        assertTrue(StemPrepProgressUi.marqueeWithPercent(QueuePrepStatus.KEY_STEMS, 40)
                .contains("40%"));
        assertEquals("", StemPrepProgressUi.marqueeWithPercent(QueuePrepStatus.KEY_IDLE, 40));
    }
}
