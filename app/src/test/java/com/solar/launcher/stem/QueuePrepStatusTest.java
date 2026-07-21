package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.io.File;

/** Queue prep marquees + registry. 2026-07-21 */
public class QueuePrepStatusTest {

    @After
    public void tearDown() {
        QueuePrepStatusRegistry.clearAll();
    }

    @Test
    public void marqueeStrings() {
        assertEquals("", QueuePrepStatus.marqueeFor(null));
        assertEquals("", QueuePrepStatus.marqueeFor(""));
        assertEquals("", QueuePrepStatus.marqueeFor(QueuePrepStatus.KEY_IDLE));
        assertEquals("Loading stems…", QueuePrepStatus.marqueeFor(QueuePrepStatus.KEY_STEMS));
        assertEquals("Separating vocals…", QueuePrepStatus.marqueeFor(QueuePrepStatus.KEY_VOCALS));
        assertEquals("Separating instrumental…",
                QueuePrepStatus.marqueeFor(QueuePrepStatus.KEY_INSTRUMENTAL));
        assertEquals("Baking instrumental…",
                QueuePrepStatus.marqueeFor(QueuePrepStatus.KEY_BAKE_INSTRUMENTAL));
    }

    @Test
    public void mergeSubtitle() {
        assertEquals("Loading stems…",
                QueuePrepStatus.mergeSubtitle("Artist · Album", QueuePrepStatus.KEY_STEMS));
        assertEquals("Album", QueuePrepStatus.mergeSubtitle("Album", QueuePrepStatus.KEY_IDLE));
    }

    @Test
    public void registrySetClear() {
        File f = new File("/tmp/qprep.mp3");
        QueuePrepStatusRegistry.set(f, QueuePrepStatus.KEY_PREPARING_MIX);
        assertTrue(QueuePrepStatusRegistry.anyBusy());
        assertEquals(QueuePrepStatus.KEY_PREPARING_MIX, QueuePrepStatusRegistry.get(f));
        QueuePrepStatusRegistry.set(f, QueuePrepStatus.KEY_IDLE);
        assertFalse(QueuePrepStatusRegistry.anyBusy());
    }
}
