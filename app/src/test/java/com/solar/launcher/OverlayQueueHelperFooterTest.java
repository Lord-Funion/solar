package com.solar.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

/** Overlay queue footer + prep marquee wiring. 2026-07-21 */
public class OverlayQueueHelperFooterTest {

    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = File.createTempFile("oqfoot", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
        com.solar.launcher.stem.QueuePrepStatusRegistry.clearAll();
    }

    private File track(String name) throws Exception {
        File f = new File(dir, name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(1);
        out.close();
        return f;
    }

    @Test
    public void footerAppendedAndHiddenDuringMove() throws Exception {
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.music(track("a.mp3")));
        q.append(PlayQueue.QueueItem.music(track("b.mp3")));
        ThemedContextMenu.QueueRowSpec[] with =
                OverlayQueueHelper.buildRowSpecs(q, false, true, false);
        assertEquals(3, with.length);
        assertEquals(OverlayQueueHelper.FOOTER_ADD_SONG, with[2].title);
        ThemedContextMenu.QueueRowSpec[] moving =
                OverlayQueueHelper.buildRowSpecs(q, false, true, true);
        assertEquals(2, moving.length);
        assertFalse(OverlayQueueHelper.FOOTER_ADD_SONG.equals(moving[0].title));
    }

    @Test
    public void prepMarqueeOnSubtitle() throws Exception {
        File f = track("busy.mp3");
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.music(f));
        com.solar.launcher.stem.QueuePrepStatusRegistry.set(f,
                com.solar.launcher.stem.QueuePrepStatus.KEY_STEMS);
        ThemedContextMenu.QueueRowSpec[] rows =
                OverlayQueueHelper.buildRowSpecs(q, false, false, false);
        assertEquals(1, rows.length);
        assertTrue(rows[0].subtitle.contains("Loading stems"));
    }
}
