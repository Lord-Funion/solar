package com.solar.launcher.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * 2026-07-20 — Small indeterminate spinner beside a list-row title (buffer/download/stem).
 * Layman: little spinning wheel next to the song name while that row is still working.
 * Technical: Deezer/ThemedContextMenu {@code progressBarStyleSmall} pattern, reusable.
 * Reversal: drop helper; callers add ProgressBar inline again (or status UiBusy only).
 */
public final class RowBusyChrome {

    /** findViewWithTag key for the inline spinner. */
    public static final Integer TAG_SPIN = Integer.valueOf(0x70ca00b1);
    /** Spinner edge length in dp (matches context-menu small spin). */
    public static final int SPIN_DP = 18;

    private RowBusyChrome() {}

    /**
     * Build a small indeterminate ProgressBar (non-focusable).
     * Layman: the tiny spinning icon itself.
     */
    public static ProgressBar newSmallSpinner(Context ctx) {
        ProgressBar spin = new ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall);
        spin.setTag(TAG_SPIN);
        spin.setFocusable(false);
        spin.setClickable(false);
        spin.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int px = dp(ctx, SPIN_DP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.leftMargin = dp(ctx, 8);
        spin.setLayoutParams(lp);
        return spin;
    }

    /**
     * Horizontal title + spinner row for ScrollView / browser lists.
     * Layman: put the title on the left and the spinner on the right.
     * Technical: title keeps MATCH_PARENT weight; spinner GONE when idle.
     */
    public static LinearLayout wrapTitleWithSpinner(Context ctx, View titleView, boolean busy) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        if (titleView.getParent() instanceof ViewGroup) {
            ((ViewGroup) titleView.getParent()).removeView(titleView);
        }
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(titleLp);
        row.addView(titleView);
        ProgressBar spin = newSmallSpinner(ctx);
        spin.setVisibility(busy ? View.VISIBLE : View.GONE);
        row.addView(spin);
        return row;
    }

    /**
     * Show or hide the row's tagged spinner.
     * Layman: turn the little wheel on or off for this line.
     */
    public static void setBusy(View row, boolean busy) {
        if (row == null) return;
        ProgressBar spin = findSpinner(row);
        if (spin == null) return;
        spin.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    /** True when this row (or child) shows the busy spinner. */
    public static boolean isBusyVisible(View row) {
        ProgressBar spin = findSpinner(row);
        return spin != null && spin.getVisibility() == View.VISIBLE;
    }

    private static ProgressBar findSpinner(View row) {
        if (row == null) return null;
        if (TAG_SPIN.equals(row.getTag()) && row instanceof ProgressBar) {
            return (ProgressBar) row;
        }
        if (row instanceof ViewGroup) {
            View found = ((ViewGroup) row).findViewWithTag(TAG_SPIN);
            if (found instanceof ProgressBar) return (ProgressBar) found;
        }
        return null;
    }

    private static int dp(Context ctx, int dps) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dps, ctx.getResources().getDisplayMetrics());
    }
}
