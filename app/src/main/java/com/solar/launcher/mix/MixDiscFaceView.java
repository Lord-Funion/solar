package com.solar.launcher.mix;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.solar.launcher.stem.StemControls;
import com.solar.launcher.stem.StemMixSoftScrub;
import com.solar.launcher.theme.ThemeManager;

/**
 * Mix face — two floating discs (whole-track) or four stem discs when dig is open.
 * Layman: two vinyl-like discs; dig pans into four stem pads for the focused song.
 * Technical: Canvas circles + gain rings; digMode draws 4 zone discs; DNA from Stem mashup bubbles.
 * Was: MixFaderFaceView three faders. Reversal: MixPlayerHost.face = MixFaderFaceView again.
 * 2026-07-21 Stems/Mix sanity
 */
public class MixDiscFaceView extends View {
    /** Deck accents — coral / blue (Stem mashup song colours). 2026-07-21 */
    public static final int[] DECK_COLORS = {
            0xFFE85D4C,
            0xFF4C8FE8,
    };
    /** Stem dig accents — match StemFaceView STEM_COLORS order. 2026-07-21 */
    public static final int[] STEM_COLORS = {
            0xFFE85D4C, // vocals
            0xFF92E82A, // drums
            0xFF00C7BE, // bass
            0xFFFF9F0A, // melody
    };
    private static final int FIELD = 0xFF0A0A0C;
    private static final int EMPTY = 0xFF55555C;
    private static final int LABEL = 0xCCFFFFFF;
    private static final int LOADING_DIM = 0x66FFFFFF;

    private final Paint fieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] deckGains = new float[MixSession.DECK_COUNT];
    private final boolean[] deckOccupied = new boolean[MixSession.DECK_COUNT];
    private final String[] deckLetters = new String[] { "1", "2" };
    private final float[] stemGains = new float[4];
    private final boolean[] stemOccupied = new boolean[4];
    private int focusDeck;
    private int focusStem = -1;
    private boolean digMode;
    private boolean loading;
    private boolean scrubbing;
    /** Scrub cursor frac 0..1 on focused disc (whole-deck seek). 2026-07-21 */
    private float scrubFrac;
    private final Paint scrubCursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] scrubXy = new float[2];

    public MixDiscFaceView(Context context) {
        super(context);
        init();
    }

    public MixDiscFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** Wire paints once. 2026-07-21 */
    private void init() {
        fieldPaint.setColor(FIELD);
        fieldPaint.setStyle(Paint.Style.FILL);
        discPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        letterPaint.setStyle(Paint.Style.FILL);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setFakeBoldText(true);
        letterPaint.setColor(0xFFFFFFFF);
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(LABEL);
        focusDeck = 0;
        scrubCursorPaint.setStyle(Paint.Style.FILL);
        scrubCursorPaint.setColor(0xFFFFCC66);
    }

    /**
     * Push two-disc Mix state (dig closed).
     * Layman: update both floating discs’ loudness and which one glows.
     * 2026-07-21
     */
    public void setDeckState(float[] g, boolean[] hasTrack, int focus,
            boolean isLoading, boolean isScrubbing) {
        setDeckState(g, hasTrack, focus, isLoading, isScrubbing, 0f);
    }

    /**
     * Two-disc state + Hold-OK scrub cursor fraction (whole deck seek).
     * Layman: shrink the ring and show the seek ball on the focused disc.
     * Was: focus ring thicken only. Reversal: ignore scrubFrac.
     * 2026-07-21
     */
    public void setDeckState(float[] g, boolean[] hasTrack, int focus,
            boolean isLoading, boolean isScrubbing, float seekFrac) {
        digMode = false;
        if (g != null) {
            for (int i = 0; i < MixSession.DECK_COUNT && i < g.length; i++) {
                deckGains[i] = StemControls.clampGain(g[i]);
            }
        }
        if (hasTrack != null) {
            for (int i = 0; i < MixSession.DECK_COUNT && i < hasTrack.length; i++) {
                deckOccupied[i] = hasTrack[i];
            }
        }
        focusDeck = MixFaderMath.clampFocusDeck(focus);
        loading = isLoading;
        scrubbing = isScrubbing;
        scrubFrac = StemMixSoftScrub.clampFrac(seekFrac);
        invalidate();
    }

    /**
     * Push four-stem dig state for one Mix deck.
     * Layman: show Vocals/Drums/Bass/Melody discs while the other Mix song keeps playing.
     * Audio: host maps gains into StemMixer for that deck only (lockstep).
     * 2026-07-21
     */
    public void setDigState(float[] gains4, boolean[] has4, int focusZone,
            boolean isLoading) {
        digMode = true;
        if (gains4 != null) {
            for (int i = 0; i < 4 && i < gains4.length; i++) {
                stemGains[i] = StemControls.clampGain(gains4[i]);
            }
        }
        if (has4 != null) {
            for (int i = 0; i < 4 && i < has4.length; i++) {
                stemOccupied[i] = has4[i];
            }
        } else {
            for (int i = 0; i < 4; i++) stemOccupied[i] = true;
        }
        focusStem = focusZone;
        loading = isLoading;
        scrubbing = false;
        invalidate();
    }

    /** True while four-stem dig face is showing. 2026-07-21 */
    public boolean isDigMode() {
        return digMode;
    }

    public int getFocusDeck() {
        return focusDeck;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawRect(0, 0, w, h, fieldPaint);
        if (digMode) {
            drawStemDig(canvas, w, h);
        } else {
            drawTwoDiscs(canvas, w, h);
        }
        if (loading) {
            labelPaint.setColor(LOADING_DIM);
            labelPaint.setTextSize(Math.max(12f, h * 0.06f));
            canvas.drawText(digMode ? "Stem dig…" : "Loading…", w * 0.5f, h * 0.5f, labelPaint);
        }
    }

    /** Two whole-track floating discs. 2026-07-21 */
    private void drawTwoDiscs(Canvas canvas, int w, int h) {
        float cy = h * 0.48f;
        float r = Math.min(w, h) * 0.22f;
        float gap = w * 0.12f;
        float[] cx = new float[] {
                w * 0.5f - r - gap * 0.5f,
                w * 0.5f + r + gap * 0.5f
        };
        int ringColor = ThemeManager.getItemTextColorSelected();
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            int accent = deckOccupied[i] ? DECK_COLORS[i] : EMPTY;
            if (loading) accent = (accent & 0x00FFFFFF) | 0x66000000;
            discPaint.setColor(accent);
            canvas.drawCircle(cx[i], cy, r, discPaint);
            // Gain ring around disc. 2026-07-21
            float stroke = Math.max(4f, r * 0.08f);
            ringPaint.setStrokeWidth(stroke);
            ringPaint.setColor(0x44FFFFFF);
            canvas.drawCircle(cx[i], cy, r + stroke, ringPaint);
            float sweep = 360f * Math.max(0f, Math.min(1f, deckGains[i]));
            if (sweep > 1f) {
                ringPaint.setColor(0xEEFFFFFF);
                canvas.drawArc(cx[i] - r - stroke, cy - r - stroke,
                        cx[i] + r + stroke, cy + r + stroke,
                        -90f, sweep, false, ringPaint);
            }
            if (i == focusDeck) {
                // Shrink focus ring while scrubbing so the seek ball reads. 2026-07-21
                float haloMul = scrubbing ? StemMixSoftScrub.scrubFocusHaloScale() : 1f;
                ringPaint.setStrokeWidth(scrubbing ? 4f : 3.5f);
                ringPaint.setColor(scrubbing ? 0xFFFFCC66 : ringColor);
                canvas.drawCircle(cx[i], cy, (r + stroke * 2.2f) * haloMul, ringPaint);
                if (scrubbing) {
                    float rim = r + stroke;
                    StemMixSoftScrub.cursorXy(cx[i], cy, rim, scrubFrac, scrubXy);
                    float ball = Math.max(4f, r * StemMixSoftScrub.scrubCursorRadiusFrac());
                    scrubCursorPaint.setColor(0xFFFFCC66);
                    canvas.drawCircle(scrubXy[0], scrubXy[1], ball, scrubCursorPaint);
                    scrubCursorPaint.setColor(0xFFFFFFFF);
                    canvas.drawCircle(scrubXy[0], scrubXy[1], ball * 0.45f, scrubCursorPaint);
                }
            }
            letterPaint.setTextSize(r * 0.55f);
            canvas.drawText(deckLetters[i], cx[i], cy + r * 0.18f, letterPaint);
        }
        labelPaint.setColor(LABEL);
        labelPaint.setTextSize(Math.max(10f, h * 0.045f));
        canvas.drawText("PREV", cx[0], cy + r + labelPaint.getTextSize() * 1.6f, labelPaint);
        canvas.drawText("NEXT", cx[1], cy + r + labelPaint.getTextSize() * 1.6f, labelPaint);
    }

    /** Four stem discs in a diamond (N/W/E/S). 2026-07-21 */
    private void drawStemDig(Canvas canvas, int w, int h) {
        float cx = w * 0.5f;
        float cy = h * 0.48f;
        float r = Math.min(w, h) * 0.14f;
        float arm = Math.min(w, h) * 0.28f;
        // N=vocals, W=drums, E=bass, S=melody. 2026-07-21
        float[] xs = new float[] { cx, cx - arm, cx + arm, cx };
        float[] ys = new float[] { cy - arm, cy, cy, cy + arm };
        String[] labs = new String[] { "V", "D", "B", "M" };
        int ringColor = ThemeManager.getItemTextColorSelected();
        for (int i = 0; i < 4; i++) {
            int accent = stemOccupied[i] ? STEM_COLORS[i] : EMPTY;
            discPaint.setColor(accent);
            canvas.drawCircle(xs[i], ys[i], r, discPaint);
            float stroke = Math.max(3f, r * 0.1f);
            ringPaint.setStrokeWidth(stroke);
            ringPaint.setColor(0x44FFFFFF);
            canvas.drawCircle(xs[i], ys[i], r + stroke, ringPaint);
            float sweep = 360f * Math.max(0f, Math.min(1f, stemGains[i]));
            if (sweep > 1f) {
                ringPaint.setColor(0xEEFFFFFF);
                canvas.drawArc(xs[i] - r - stroke, ys[i] - r - stroke,
                        xs[i] + r + stroke, ys[i] + r + stroke,
                        -90f, sweep, false, ringPaint);
            }
            if (i == focusStem) {
                ringPaint.setStrokeWidth(3.5f);
                ringPaint.setColor(ringColor);
                canvas.drawCircle(xs[i], ys[i], r + stroke * 2f, ringPaint);
            }
            letterPaint.setTextSize(r * 0.7f);
            canvas.drawText(labs[i], xs[i], ys[i] + r * 0.22f, letterPaint);
        }
    }
}
