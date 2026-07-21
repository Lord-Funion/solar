package com.solar.launcher.flow;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Now Playing album slot — slanted 3D cover + Flow floor reflection via {@link FlowAlbumArt3d}.
 * 2026-07-11 — Cover fills {@link FlowAlbumArt3d#PLAYER_CONTENT_H} (225); gloss uses leftover slot.
 * Shared Camera tilt keeps floor glued to the cover (was gap under art).
 * Reversal: drawCover(rotY) only, or separate flat drawReflectionFloor.
 */
public final class PlayerAlbumArt3dView extends View {

    private final Paint coverPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint reflectionPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private Bitmap cover;
    private int[] reflectTable;
    private float reflectTableForH = -1f;

    public PlayerAlbumArt3dView(Context context) {
        this(context, null);
    }

    public PlayerAlbumArt3dView(Context context, AttributeSet attrs) {
        super(context, attrs);
        coverPaint.setFilterBitmap(false);
        // Match FlowView — bilinear floor gloss.
        reflectionPaint.setFilterBitmap(true);
        setBackgroundColor(0x00000000);
    }

    /** Swap the resting NP cover bitmap and redraw. */
    public void setCoverBitmap(Bitmap bmp) {
        cover = bmp;
        invalidate();
    }

    /** Bitmap currently shown in the NP 3D slot (may be null). */
    public Bitmap getCoverBitmap() {
        return cover;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cover == null || cover.isRecycled()) return;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) return;
        FlowAlbumArt3d.AlbumArtPose pose = FlowAlbumArt3d.playerPose(w, h);
        float reflectH = FlowAlbumArt3d.playerReflectHeight(h, pose.drawRect);
        // 2026-07-20 — Cover full opacity; floor uses shared Flow gloss (Y1=Y2).
        float reflectAlpha = reflectH > 1f ? FlowView.flowReflectionBaseAlpha() : 0f;
        int[] table = reflectH > 1f ? reflectTableFor(reflectH) : null;
        FlowAlbumArt3d.drawPlayerCoverWithReflection(canvas, cover, pose.drawRect,
                pose.rotationYDeg, 1f, reflectAlpha, reflectH, table, coverPaint, reflectionPaint);
    }

    /** Rebuild Rockbox reflect table when NP floor height changes. */
    private int[] reflectTableFor(float reflectH) {
        if (reflectTable != null && Math.abs(reflectTableForH - reflectH) < 0.5f) {
            return reflectTable;
        }
        reflectTable = PictureFlowLayout.buildReflectTable(reflectH);
        reflectTableForH = reflectH;
        return reflectTable;
    }
}
