package com.solar.launcher.mix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Mix disc geometry + two-deck focus. Was: three fader columns. 2026-07-20 / 2026-07-21
 */
public class MixFaderMathTest {

    @Test
    public void clampFocusDeckTwoDiscs() {
        assertEquals(0, MixFaderMath.clampFocusDeck(-3));
        assertEquals(0, MixFaderMath.clampFocusDeck(0));
        assertEquals(1, MixFaderMath.clampFocusDeck(1));
        assertEquals(1, MixFaderMath.clampFocusDeck(99));
        assertEquals(2, MixSession.DECK_COUNT);
    }

    @Test
    public void isValidDeck() {
        assertTrue(MixFaderMath.isValidDeck(0));
        assertTrue(MixFaderMath.isValidDeck(1));
        assertFalse(MixFaderMath.isValidDeck(-1));
        assertFalse(MixFaderMath.isValidDeck(2));
    }

    @Test
    public void knobCenterYLoudAtTop() {
        assertEquals(10f, MixFaderMath.knobCenterY(1f, 10f, 110f), 0.01f);
        assertEquals(110f, MixFaderMath.knobCenterY(0f, 10f, 110f), 0.01f);
        assertEquals(60f, MixFaderMath.knobCenterY(0.5f, 10f, 110f), 0.01f);
    }

    @Test
    public void fillHeightScalesWithGain() {
        assertEquals(0f, MixFaderMath.fillHeight(0f, 10f, 110f), 0.01f);
        assertEquals(100f, MixFaderMath.fillHeight(1f, 10f, 110f), 0.01f);
        assertEquals(50f, MixFaderMath.fillHeight(0.5f, 10f, 110f), 0.01f);
    }

    @Test
    public void columnCenterXTwoHalves() {
        // 300px / 2 → centres at 75, 225. 2026-07-21
        assertEquals(75f, MixFaderMath.columnCenterX(0, 300f), 0.01f);
        assertEquals(225f, MixFaderMath.columnCenterX(1, 300f), 0.01f);
    }

    @Test
    public void sessionFocusMatchesTwoDiscs() {
        MixSession s = new MixSession();
        assertTrue(s.onDeckKey(1));
        assertEquals(1, s.activeDeck());
        assertEquals(1, MixFaderMath.clampFocusDeck(s.activeDeck()));
        assertFalse(s.onDeckKey(1));
        assertFalse(s.onDeckKey(2)); // no third deck
        assertTrue(s.onDeckKey(0));
        assertEquals(0, s.activeDeck());
    }
}
