package dev.lordfunion.rockboxsolar;

import android.graphics.Color;

import org.json.JSONObject;

final class Theme {
    final String name;
    final int background;
    final int foreground;
    final int accent;
    final int selected;
    final int muted;
    final float fontScale;

    Theme(String name, int background, int foreground, int accent, int selected, int muted, float fontScale) {
        this.name = name;
        this.background = background;
        this.foreground = foreground;
        this.accent = accent;
        this.selected = selected;
        this.muted = muted;
        this.fontScale = fontScale;
    }

    static Theme classic() {
        return new Theme("Classic", Color.rgb(8, 11, 13), Color.rgb(235, 241, 238),
                Color.rgb(159, 232, 112), Color.rgb(36, 58, 44), Color.rgb(137, 150, 145), 1.0f);
    }

    static Theme amber() {
        return new Theme("Amber LCD", Color.rgb(20, 13, 2), Color.rgb(255, 221, 142),
                Color.rgb(255, 174, 51), Color.rgb(73, 45, 4), Color.rgb(181, 139, 71), 1.0f);
    }

    static Theme ice() {
        return new Theme("Ice", Color.rgb(4, 16, 27), Color.rgb(225, 246, 255),
                Color.rgb(85, 208, 255), Color.rgb(12, 58, 82), Color.rgb(120, 163, 181), 1.0f);
    }

    static Theme fromJson(JSONObject json) {
        String name = json.optString("name", "External Theme");
        return new Theme(name,
                parseColor(json.optString("background", "#080B0D"), Color.rgb(8, 11, 13)),
                parseColor(json.optString("foreground", "#EBF1EE"), Color.WHITE),
                parseColor(json.optString("accent", "#9FE870"), Color.GREEN),
                parseColor(json.optString("selected", "#243A2C"), Color.DKGRAY),
                parseColor(json.optString("muted", "#899691"), Color.GRAY),
                (float) Math.max(0.75, Math.min(1.5, json.optDouble("fontScale", 1.0))));
    }

    private static int parseColor(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
