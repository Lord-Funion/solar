package com.solar.launcher.mix;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.solar.launcher.stem.StemControls;
import com.solar.launcher.theme.ThemeManager;
import com.solar.launcher.ui.HardwareButtonGlyph;

/**
 * Mix face — three vertical DJ faders with hardware-button glyphs under each.
 * Layman: three volume sliders; the lit ring shows which song the wheel turns.
 * Technical: Canvas rails + knobs; focus stroke; Prev/Next/Play-Pause ImageDrawables.
 * Was: StemFaceView puck arms for Mix. Reversal: MixPlayerHost.face = new StemFaceView again.
 * 2026-07-20
 */
public class MixFaderFaceView extends View {
    /** Deck accent colours (Prev / Next / Play) — match Stem arm DNA. */
    public static final int[] DECK_COLORS = {
            0xFF92E82A, // Prev — drums green
            0xFF00C7BE, // Next — bass teal
            0xFFFF9F0A, // Play — melody orange
    };

    private static final int FIELD = 0xFF0A0A0C;
    private static final int RAIL = 0xFF2A2A30;
    private static final int RAIL_EMPTY = 0xFF1A1A1E;
    private static final int KNOB = 0xFFF0F0F4;
    private static final int EMPTY_GLYPH = 0xFF55555C;
    private static final int LOADING_DIM = 0x66FFFFFF;

    private final Paint fieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();

    private final float[] gains = new float[MixSession.DECK_COUNT];
    private final boolean[] occupied = new boolean[MixSession.DECK_COUNT];
    private int focusDeck;
    private boolean loading;
    private boolean scrubbing;

    /** Cached tinted glyphs under each fader — rebuild when size changes. 2026-07-20 */
    private final Drawable[] glyphDrawables = new Drawable[MixSession.DECK_COUNT];
    private int glyphCacheH;
    private int glyphCacheTint = Integer.MIN_VALUE;

    public MixFaderFaceView(Context context) {
        super(context);
        init();
    }

    public MixFaderFaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** Wire paints once. 2026-07-20 */
    private void init() {
        fieldPaint.setColor(FIELD);
        fieldPaint.setStyle(Paint.Style.FILL);
        railPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(KNOB);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);
        focusDeck = 0;
    }

    /**
     * Push deck gains + focus into the face.
     * Layman: update how high each slider sits and which one glows.
     * Technical: gains[0..2], focusDeck, loading/scrub flags; occupied marks empty slots dim.
     * 2026-07-20
     */
    public void setState(float[] g, boolean[] hasTrack, int focus, boolean isLoading, boolean isScrubbing) {
        if (g != null) {
            for (int i = 0; i < MixSession.DECK_COUNT && i < g.length; i++) {
                gains[i] = StemControls.clampGain(g[i]);
            }
        }
        boolean occChanged = false;
        if (hasTrack != null) {
            for (int i = 0; i < MixSession.DECK_COUNT && i < hasTrack.length; i++) {
                if (occupied[i] != hasTrack[i]) occChanged = true;
                occupied[i] = hasTrack[i];
            }
        }
        // Empty↔filled swaps glyph tint — bust cache. 2026-07-20
        if (occChanged) glyphCacheH = -1;
        focusDeck = MixFaderMath.clampFocusDeck(focus);
        loading = isLoading;
        scrubbing = isScrubbing;
        invalidate();
    }

    /** Focused deck index (0..2). 2026-07-20 */
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

        float pad = Math.max(8f, Math.min(w, h) * 0.04f);
        float glyphBand = Math.max(28f, h * 0.14f);
        float top = pad;
        float bottom = h - pad - glyphBand;
        if (bottom <= top + 24f) {
            bottom = h - pad;
            glyphBand = 0f;
        }

        ensureGlyphs((int) Math.max(16f, glyphBand * 0.55f));

        float colW = w / (float) MixSession.DECK_COUNT;
        float railW = Math.max(10f, colW * 0.18f);
        float knobR = Math.max(railW * 0.85f, 8f);
        float ringPad = Math.max(6f, colW * 0.08f);

        int ringColor = ThemeManager.getItemTextColorSelected();
        ringPaint.setColor(scrubbing ? 0xFFFFCC66 : ringColor);
        ringPaint.setStrokeWidth(scrubbing ? 4f : 3f);

        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            float cx = MixFaderMath.columnCenterX(i, w);
            float trackTop = top + knobR + 4f;
            float trackBottom = bottom - knobR - 4f;
            float railL = cx - railW * 0.5f;
            float railR = cx + railW * 0.5f;

            // Empty rail trough. 2026-07-20
            railPaint.setColor(RAIL_EMPTY);
            tmpRect.set(railL, trackTop, railR, trackBottom);
            canvas.drawRoundRect(tmpRect, railW * 0.4f, railW * 0.4f, railPaint);

            float fillH = MixFaderMath.fillHeight(gains[i], trackTop, trackBottom);
            int accent = DECK_COLORS[i];
            if (!occupied[i]) {
                accent = EMPTY_GLYPH;
            }
            if (loading) {
                accent = (accent & 0x00FFFFFF) | 0x66000000;
            }
            // Lit fill from bottom up. 2026-07-20
            if (fillH > 0.5f) {
                fillPaint.setColor(accent);
                tmpRect.set(railL, trackBottom - fillH, railR, trackBottom);
                canvas.drawRoundRect(tmpRect, railW * 0.4f, railW * 0.4f, fillPaint);
            }

            // Outer rail outline. 2026-07-20
            railPaint.setColor(RAIL);
            railPaint.setStyle(Paint.Style.STROKE);
            railPaint.setStrokeWidth(2f);
            tmpRect.set(railL, trackTop, railR, trackBottom);
            canvas.drawRoundRect(tmpRect, railW * 0.4f, railW * 0.4f, railPaint);
            railPaint.setStyle(Paint.Style.FILL);

            float ky = MixFaderMath.knobCenterY(gains[i], trackTop, trackBottom);
            knobPaint.setColor(occupied[i] ? KNOB : EMPTY_GLYPH);
            canvas.drawCircle(cx, ky, knobR, knobPaint);

            // Focus ring around the lit fader column. 2026-07-20
            if (i == focusDeck) {
                tmpRect.set(cx - colW * 0.5f + ringPad, top,
                        cx + colW * 0.5f - ringPad, bottom + (glyphBand > 0 ? glyphBand * 0.35f : 0f));
                canvas.drawRoundRect(tmpRect, 10f, 10f, ringPaint);
            }

            // Hardware glyph under the rail. 2026-07-20
            Drawable glyph = glyphDrawables[i];
            if (glyph != null && glyphBand > 0f) {
                int gw = glyph.getBounds().width();
                int gh = glyph.getBounds().height();
                int left = Math.round(cx - gw * 0.5f);
                int gTop = Math.round(bottom + (glyphBand - gh) * 0.45f);
                glyph.setBounds(left, gTop, left + gw, gTop + gh);
                // Dim empty slots; brighten focused deck glyph. 2026-07-20
                int alpha = !occupied[i] ? 0x66 : (i == focusDeck ? 0xFF : 0xCC);
                glyph.setAlpha(loading ? 0x55 : alpha);
                glyph.draw(canvas);
            }
        }

        if (loading) {
            labelPaint.setColor(LOADING_DIM);
            labelPaint.setTextSize(Math.max(12f, h * 0.06f));
            canvas.drawText("Loading…", w * 0.5f, h * 0.5f, labelPaint);
        }
    }

    /**
     * Build/reuse tinted Prev/Next/Play-Pause drawables for the glyph band.
     * Layman: little button pictures under each slider, theme-coloured.
     * 2026-07-20
     */
    private void ensureGlyphs(int sizePx) {
        int tint = ThemeManager.getItemTextColorNormal();
        if (glyphCacheH == sizePx && glyphCacheTint == tint && glyphDrawables[0] != null) {
            return;
        }
        Context ctx = getContext();
        HardwareButtonGlyph.Button[] buttons = {
                HardwareButtonGlyph.Button.PREV,
                HardwareButtonGlyph.Button.NEXT,
                HardwareButtonGlyph.Button.PLAY_PAUSE
        };
        for (int i = 0; i < MixSession.DECK_COUNT; i++) {
            int color = occupied[i] ? DECK_COLORS[i] : EMPTY_GLYPH;
            // Prefer deck accent so glyphs match their fader. 2026-07-20
            glyphDrawables[i] = HardwareButtonGlyph.tintedDrawable(ctx, buttons[i], color, sizePx);
        }
        glyphCacheH = sizePx;
        glyphCacheTint = tint;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Force glyph rebuild at new band height. 2026-07-20
        glyphCacheH = -1;
    }
}
