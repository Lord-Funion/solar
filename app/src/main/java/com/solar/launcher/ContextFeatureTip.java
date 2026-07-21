package com.solar.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Queue Editor–style context-modal tips for input onboarding.
 * Layman: first time you open a feature, a calm panel teaches the buttons — not a toast flash.
 * Technical: SharedPreferences gate + ThemedContextMenu scrollable detail + Got it (+ optional Don't show again).
 * Was: per-feature toasts / status-line walls / MainActivity queue_tutorial one-off.
 * Reversal: delete this class; restore toast/status call sites + inline queue tutorial rebuild.
 * 2026-07-21
 */
public final class ContextFeatureTip {

    /** Queue editor hold/OK lesson (legacy pref key kept). 2026-07-21 */
    public static final String PREF_QUEUE_TUTORIAL = "queue_tutorial_seen";
    /** Stem pick Prev/Next/Play lesson. 2026-07-21 */
    public static final String PREF_STEM_PICK = "stem_pick_tip_seen";
    /** Mix assign Prev/Next/Play bind lesson (same key as MixPlayerHost). 2026-07-21 */
    public static final String PREF_MIX_ASSIGN = "mix_assign_onboarding_seen";
    /** Mix face fader lesson (same key as MixPlayerHost). 2026-07-21 */
    public static final String PREF_MIX_FADER = "mix_fader_onboarding_seen";
    /** Stem/Mix jam queue journey (same key as StemMixOnboardingPrefs). 2026-07-21 */
    public static final String PREF_STEM_MIX_JOURNEY = "stem_mix_queue_journey_seen";
    /** Now Playing first tip — volume arrows. 2026-07-21 */
    public static final String PREF_NP_VOLUME = "np_volume_tip_seen";
    /** Single-track Stem face — wheel = pad volume. 2026-07-21 */
    public static final String PREF_STEM_FACE = "stem_face_volume_tip_seen";

    /** Legacy queue tutorial tier id (Back/OK finish + rebuild). 2026-07-21 */
    public static final String TIER_QUEUE_TUTORIAL = "queue_tutorial";
    /** Generic feature tip tier prefix — full id e.g. feature_tip_stem_pick. 2026-07-21 */
    public static final String TIER_PREFIX = "feature_tip_";

    /** Builds glyph body after detail TextView font size is known. 2026-07-21 */
    public interface BodyFactory {
        CharSequence create(int sizePx);
    }

    /**
     * MainActivity (or overlay host) paints the modal; tip logic stays here.
     * Layman: the app screen that can open the options panel.
     * 2026-07-21
     */
    public interface Presenter {
        SharedPreferences prefs();
        Context appContext();
        ThemedContextMenu menu();
        void pushTier(String tierId);
        void showTierInPlace(String title, ArrayList<String> labels, ArrayList<String> states,
                ArrayList<Boolean> headers, ArrayList<Runnable> actions, boolean focusList);
        String labelGotIt();
        /** Null/empty → no Don't show again row. 2026-07-21 */
        String labelDontShowAgain();
        int detailSizePxFallback();
    }

    /** Live tip so Back/OK / rebuild can finish or re-paint. 2026-07-21 */
    private static Pending pending;

    private ContextFeatureTip() {}

    /** True when this tip has not been acknowledged yet. 2026-07-21 */
    public static boolean needs(SharedPreferences prefs, String prefKey) {
        if (prefKey == null || prefKey.length() == 0) return false;
        if (prefs == null) return true;
        return !prefs.getBoolean(prefKey, false);
    }

    /** Mark tip seen (Got it or Don't show again). 2026-07-21 */
    public static void mark(SharedPreferences prefs, String prefKey) {
        if (prefs == null || prefKey == null || prefKey.length() == 0) return;
        try {
            prefs.edit().putBoolean(prefKey, true).apply();
        } catch (Exception ignored) {}
    }

    /** Clear seen so Settings → Help can re-show. 2026-07-21 */
    public static void clear(SharedPreferences prefs, String prefKey) {
        if (prefs == null || prefKey == null || prefKey.length() == 0) return;
        try {
            prefs.edit().putBoolean(prefKey, false).apply();
        } catch (Exception ignored) {}
    }

    /** Queue tutorial or feature_tip_* tiers use scrollable detail chrome. 2026-07-21 */
    public static boolean isTipTier(String tier) {
        if (tier == null) return false;
        if (TIER_QUEUE_TUTORIAL.equals(tier)) return true;
        return tier.startsWith(TIER_PREFIX);
    }

    /** Stable tier id for a pref key. 2026-07-21 */
    public static String tierIdForPref(String prefKey) {
        if (PREF_QUEUE_TUTORIAL.equals(prefKey)) return TIER_QUEUE_TUTORIAL;
        String safe = prefKey != null ? prefKey : "tip";
        return TIER_PREFIX + safe;
    }

    /** True while a tip modal owns the context menu top tier. 2026-07-21 */
    public static boolean isShowing(Presenter host) {
        if (host == null || pending == null) return false;
        ThemedContextMenu menu = host.menu();
        if (menu == null || !menu.isShowing()) return false;
        return isTipTier(pending.tierId);
    }

    /**
     * Show tip if needed — no-op when already seen.
     * Does not pause Stem/Mix/NP audio (modal over live sound).
     * 2026-07-21
     */
    public static boolean showIfNeeded(Presenter host, String prefKey, String title,
            String plainStub, BodyFactory body, boolean offerDontShowAgain, Runnable onFinished) {
        if (host == null || !needs(host.prefs(), prefKey)) return false;
        show(host, tierIdForPref(prefKey), title, plainStub, body, prefKey,
                offerDontShowAgain, onFinished);
        return true;
    }

    /**
     * Force-show tip chrome (caller already gated, e.g. queue before open).
     * Layman: open the lesson panel now.
     * 2026-07-21
     */
    public static void show(Presenter host, String tierId, String title, String plainStub,
            BodyFactory body, String prefKey, boolean offerDontShowAgain, Runnable onFinished) {
        if (host == null) return;
        Pending p = new Pending();
        p.tierId = tierId != null ? tierId : tierIdForPref(prefKey);
        p.title = title != null ? title : "";
        p.plainStub = plainStub != null ? plainStub : "";
        p.body = body;
        p.prefKey = prefKey;
        p.offerDontShowAgain = offerDontShowAgain;
        p.onFinished = onFinished;
        pending = p;
        host.pushTier(p.tierId);
        paint(host, true);
    }

    /** Re-paint after Back stack restore. 2026-07-21 */
    public static void rebuild(Presenter host, boolean focusList) {
        if (host == null || pending == null) return;
        paint(host, focusList);
    }

    /**
     * OK or Back on an open tip — mark + finish callback (opens real UI when set).
     * 2026-07-21
     */
    public static boolean finishFromInput(Presenter host) {
        if (host == null || pending == null) return false;
        ThemedContextMenu menu = host.menu();
        if (menu == null || !menu.isShowing()) return false;
        if (!isTipTier(pending.tierId)) return false;
        finishInternal(host);
        return true;
    }

    /** Active pref key while tip is up (tests / diagnostics). 2026-07-21 */
    public static String activePrefKey() {
        return pending != null ? pending.prefKey : null;
    }

    /** Clear pending without marking (detach / tests). 2026-07-21 */
    public static void clearPending() {
        pending = null;
    }

    private static void paint(final Presenter host, boolean focusList) {
        final Pending p = pending;
        if (p == null || host == null) return;
        ArrayList<String> labels = new ArrayList<String>();
        ArrayList<String> states = new ArrayList<String>();
        ArrayList<Boolean> headers = new ArrayList<Boolean>();
        ArrayList<Runnable> actions = new ArrayList<Runnable>();

        // Scrollable detail stub — glyphs overwrite after show. 2026-07-21
        labels.add(p.plainStub.length() > 0 ? p.plainStub : " ");
        states.add(null);
        headers.add(Boolean.TRUE);
        actions.add(null);

        labels.add(host.labelGotIt());
        states.add(null);
        headers.add(Boolean.FALSE);
        actions.add(new Runnable() {
            @Override
            public void run() {
                finishInternal(host);
            }
        });

        String dont = host.labelDontShowAgain();
        if (p.offerDontShowAgain && dont != null && dont.length() > 0) {
            labels.add(dont);
            states.add(null);
            headers.add(Boolean.FALSE);
            actions.add(new Runnable() {
                @Override
                public void run() {
                    finishInternal(host);
                }
            });
        }

        host.showTierInPlace(p.title, labels, states, headers, actions, focusList);
        ThemedContextMenu menu = host.menu();
        if (menu == null) return;
        int sizePx = Math.max(1, host.detailSizePxFallback());
        TextView detailBody = menu.getScrollableDetailBody();
        if (detailBody != null) {
            try {
                sizePx = com.solar.launcher.ui.HardwareButtonGlyph.sizePxMatchingTextView(detailBody);
            } catch (Exception ignored) {}
        }
        CharSequence body = p.body != null
                ? p.body.create(sizePx)
                : p.plainStub;
        if (body != null) {
            menu.setScrollableDetailContent(body);
        }
        // Focus Got it (row 1) — detail is header row 0. 2026-07-21
        menu.focusTierRow(1);
        menu.requestOverlayFocus();
    }

    private static void finishInternal(Presenter host) {
        Pending p = pending;
        pending = null;
        if (p == null) return;
        mark(host != null ? host.prefs() : null, p.prefKey);
        // Pop tip tier when still on top. 2026-07-21
        if (p.onFinished != null) {
            try {
                p.onFinished.run();
            } catch (Exception ignored) {}
        }
    }

    /** Snapshot of the open tip. 2026-07-21 */
    private static final class Pending {
        String tierId;
        String title;
        String plainStub;
        BodyFactory body;
        String prefKey;
        boolean offerDontShowAgain;
        Runnable onFinished;
    }
}
