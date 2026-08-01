package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.Virtualizer;

final class AudioEffects {
    static final String PREFS = "rockbox_solar_dsp";
    private Equalizer equalizer;
    private BassBoost bass;
    private Virtualizer virtualizer;

    void attach(Context context, int sessionId) {
        release();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            equalizer = new Equalizer(0, sessionId);
            equalizer.setEnabled(prefs.getBoolean("eq_enabled", false));
            String preset = prefs.getString("preset", "Flat");
            short count = equalizer.getNumberOfPresets();
            for (short i = 0; i < count; i++) if (preset.equalsIgnoreCase(equalizer.getPresetName(i))) equalizer.usePreset(i);
        } catch (Throwable ignored) { equalizer = null; }
        try {
            bass = new BassBoost(0, sessionId);
            bass.setStrength((short) prefs.getInt("bass", 0)); bass.setEnabled(prefs.getInt("bass", 0) > 0);
        } catch (Throwable ignored) { bass = null; }
        try {
            virtualizer = new Virtualizer(0, sessionId);
            virtualizer.setStrength((short) prefs.getInt("virtualizer", 0)); virtualizer.setEnabled(prefs.getInt("virtualizer", 0) > 0);
        } catch (Throwable ignored) { virtualizer = null; }
    }

    void release() {
        if (equalizer != null) { equalizer.release(); equalizer = null; }
        if (bass != null) { bass.release(); bass = null; }
        if (virtualizer != null) { virtualizer.release(); virtualizer = null; }
    }
}
