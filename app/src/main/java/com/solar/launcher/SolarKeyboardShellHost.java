package com.solar.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.solar.launcher.theme.ThemeManager;

/**
 * 2026-07-05 — Binds theme backdrop + status bar + wheel keyboard shell.
 * Layman: paints the same full-screen typing look as Wi‑Fi/search inside Solar.
 * Technical: shared by MainActivity STATE_WIFI_KEYBOARD and system IME overlay.
 */
public final class SolarKeyboardShellHost {

    private final Context context;
    private final View shellRoot;
    private final ImageView ivBg;
    private final ImageView ivMask;
    private final View tint;
    private final TextView tvStatusTitle;
    private final SolarWheelKeyboardUi keyboardUi;

    public SolarKeyboardShellHost(Context context, View shellRoot, String enterLabel) {
        this.context = context.getApplicationContext();
        this.shellRoot = shellRoot;
        ivBg = (ImageView) shellRoot.findViewById(R.id.iv_keyboard_shell_bg);
        ivMask = (ImageView) shellRoot.findViewById(R.id.iv_keyboard_shell_mask);
        tint = shellRoot.findViewById(R.id.view_keyboard_shell_tint);
        View statusBar = shellRoot.findViewById(R.id.layout_keyboard_status_bar);
        tvStatusTitle = statusBar != null
                ? (TextView) statusBar.findViewById(R.id.tv_status_clock) : null;
        View keyboardRoot = shellRoot.findViewById(R.id.layout_solar_wheel_keyboard);
        keyboardUi = new SolarWheelKeyboardUi(context, keyboardRoot, enterLabel);
    }

    /** Paint wallpaper/tint and status title — call on show and theme change. */
    public void applyShellTheme(String statusTitle) {
        applyShellTheme(statusTitle, false);
    }

    /** @param overlayPanelOnly true for WM overlay tiers — no full-screen dim tint. */
    public void applyShellTheme(String statusTitle, boolean overlayPanelOnly) {
        ThemeManager.ensureOverlayPaintableMinimum(context);
        Bitmap wall = ThemeManager.getWallpaper(false);
        if (wall == null) wall = ThemeManager.getWallpaper(true);
        if (ivBg != null) {
            if (wall != null && !overlayPanelOnly) {
                ivBg.setImageBitmap(wall);
                if (tint != null) tint.setBackgroundColor(0x00000000);
            } else {
                ivBg.setImageDrawable(null);
                if (tint != null) {
                    tint.setBackgroundColor(overlayPanelOnly ? 0x00000000
                            : ThemeManager.getOverlayBackgroundColor());
                }
            }
        }
        if (tvStatusTitle != null) {
            // 2026-07-20 — Clock in keyboard status (was purpose title, duplicated with field title).
            // Layman: status strip shows the time while you type.
            // statusTitle arg kept for callers; unused for chrome. Reversal: setText(statusTitle).
            boolean is24 = android.text.format.DateFormat.is24HourFormat(context);
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                    is24 ? "HH:mm" : "h:mm a", java.util.Locale.US);
            tvStatusTitle.setText(fmt.format(new java.util.Date()));
            ThemeManager.applyThemedTextStyle(tvStatusTitle, ThemeManager.getStatusBarTextColor());
            View statusBar = shellRoot.findViewById(R.id.layout_keyboard_status_bar);
            if (statusBar != null) {
                statusBar.setBackgroundColor(ThemeManager.getStatusBarBackgroundColor());
            }
        }
        keyboardUi.applyTheme();
    }

    public SolarWheelKeyboardUi getKeyboardUi() {
        return keyboardUi;
    }

    public View getShellRoot() {
        return shellRoot;
    }
}
