package com.solar.launcher;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2026-07-19/20 — Wheel nav ignores UP; repeats are candidates (held gate is WheelNavPolicy).
 * Uses primitive overload — KeyEvent methods are not mocked in JVM unit tests.
 */
public class Y1InputKeysWheelNavTest {

    @Test
    public void wheelNavAction_acceptsLiveDown() {
        assertTrue(Y1InputKeys.isWheelNavAction(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
    }

    @Test
    public void wheelNavAction_rejectsUp() {
        assertFalse(Y1InputKeys.isWheelNavAction(
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
    }

    @Test
    public void wheelNavAction_acceptsRepeatCandidate() {
        // 2026-07-20 — Continuous turn repeats must reach callers; held gate kills ghosts.
        assertTrue(Y1InputKeys.isWheelNavAction(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 3));
    }

    @Test
    public void wheelNavAction_rejectsNullEvent() {
        assertFalse(Y1InputKeys.isWheelNavAction(null));
    }
}
