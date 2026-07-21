package com.solar.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;

import com.solar.launcher.stem.StemMixOnboardingAndScrubTest;
import com.solar.launcher.stem.StemPadMutePolicy;

import org.junit.After;
import org.junit.Test;

/**
 * ContextFeatureTip gate/mark + Stem pad mute policy (no seek on mute cycle).
 * 2026-07-21
 */
public class ContextFeatureTipTest {

    @After
    public void tearDown() {
        ContextFeatureTip.clearPending();
    }

    @Test
    public void needsMarkClearRoundTrip() {
        StemMixOnboardingAndScrubTest.MemPrefs prefs = new StemMixOnboardingAndScrubTest.MemPrefs();
        assertTrue(ContextFeatureTip.needs(prefs, ContextFeatureTip.PREF_STEM_PICK));
        ContextFeatureTip.mark(prefs, ContextFeatureTip.PREF_STEM_PICK);
        assertFalse(ContextFeatureTip.needs(prefs, ContextFeatureTip.PREF_STEM_PICK));
        ContextFeatureTip.clear(prefs, ContextFeatureTip.PREF_STEM_PICK);
        assertTrue(ContextFeatureTip.needs(prefs, ContextFeatureTip.PREF_STEM_PICK));
    }

    @Test
    public void nullPrefsNeedsTrueMarkNoop() {
        assertTrue(ContextFeatureTip.needs(null, ContextFeatureTip.PREF_QUEUE_TUTORIAL));
        ContextFeatureTip.mark(null, ContextFeatureTip.PREF_QUEUE_TUTORIAL);
        assertTrue(ContextFeatureTip.needs(null, ContextFeatureTip.PREF_QUEUE_TUTORIAL));
    }

    @Test
    public void emptyPrefKeyNeverNeeded() {
        StemMixOnboardingAndScrubTest.MemPrefs prefs = new StemMixOnboardingAndScrubTest.MemPrefs();
        assertFalse(ContextFeatureTip.needs(prefs, null));
        assertFalse(ContextFeatureTip.needs(prefs, ""));
    }

    @Test
    public void tipTierRecognized() {
        assertTrue(ContextFeatureTip.isTipTier(ContextFeatureTip.TIER_QUEUE_TUTORIAL));
        assertTrue(ContextFeatureTip.isTipTier(ContextFeatureTip.tierIdForPref(
                ContextFeatureTip.PREF_MIX_FADER)));
        assertFalse(ContextFeatureTip.isTipTier("queue"));
        assertFalse(ContextFeatureTip.isTipTier(null));
    }

    @Test
    public void queuePrefMapsToLegacyTierId() {
        assertEquals(ContextFeatureTip.TIER_QUEUE_TUTORIAL,
                ContextFeatureTip.tierIdForPref(ContextFeatureTip.PREF_QUEUE_TUTORIAL));
    }

    @Test
    public void finishMarksPrefViaPresenter() {
        final StemMixOnboardingAndScrubTest.MemPrefs prefs =
                new StemMixOnboardingAndScrubTest.MemPrefs();
        final boolean[] finished = new boolean[] { false };
        ContextFeatureTip.Presenter host = new FakePresenter(prefs) {
            @Override
            public void pushTier(String tierId) {
                // no-op shell
            }

            @Override
            public void showTierInPlace(String title, java.util.ArrayList<String> labels,
                    java.util.ArrayList<String> states, java.util.ArrayList<Boolean> headers,
                    java.util.ArrayList<Runnable> actions, boolean focusList) {
                // Simulate Got it row — index 1. 2026-07-21
                if (actions != null && actions.size() > 1 && actions.get(1) != null) {
                    actions.get(1).run();
                }
            }
        };
        ContextFeatureTip.show(host, ContextFeatureTip.tierIdForPref(ContextFeatureTip.PREF_NP_VOLUME),
                "NP", "Volume",
                new ContextFeatureTip.BodyFactory() {
                    @Override
                    public CharSequence create(int sizePx) {
                        return "Volume tip";
                    }
                },
                ContextFeatureTip.PREF_NP_VOLUME,
                false,
                new Runnable() {
                    @Override
                    public void run() {
                        finished[0] = true;
                    }
                });
        assertTrue(finished[0]);
        assertFalse(ContextFeatureTip.needs(prefs, ContextFeatureTip.PREF_NP_VOLUME));
        assertNull(ContextFeatureTip.activePrefKey());
    }

    @Test
    public void padMuteCycleDoesNotSeekOrRestart() {
        assertFalse(StemPadMutePolicy.shouldPauseWhenSilent());
        assertFalse(StemPadMutePolicy.shouldSeekOnUnmute());
        assertFalse(StemPadMutePolicy.shouldRestartOnUnmute());
        int before = 12_345;
        assertEquals(before, StemPadMutePolicy.positionAfterMuteCycle(before, false, false));
        assertEquals(-1, StemPadMutePolicy.positionAfterMuteCycle(before, true, false));
        assertEquals(-1, StemPadMutePolicy.positionAfterMuteCycle(before, false, true));
    }

    /** Minimal presenter — menu null so finishFromInput stays false unless show paints. 2026-07-21 */
    static class FakePresenter implements ContextFeatureTip.Presenter {
        final SharedPreferences prefs;

        FakePresenter(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public SharedPreferences prefs() {
            return prefs;
        }

        @Override
        public android.content.Context appContext() {
            return null;
        }

        @Override
        public ThemedContextMenu menu() {
            return null;
        }

        @Override
        public void pushTier(String tierId) {}

        @Override
        public void showTierInPlace(String title, java.util.ArrayList<String> labels,
                java.util.ArrayList<String> states, java.util.ArrayList<Boolean> headers,
                java.util.ArrayList<Runnable> actions, boolean focusList) {}

        @Override
        public String labelGotIt() {
            return "Got it";
        }

        @Override
        public String labelDontShowAgain() {
            return "Don't show again";
        }

        @Override
        public int detailSizePxFallback() {
            return 14;
        }
    }
}
