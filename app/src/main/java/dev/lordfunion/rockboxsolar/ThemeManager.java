package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class ThemeManager {
    private static final String PREFS = "rockbox_solar_theme";
    private static final String KEY = "active";

    private final Context context;
    private final ArrayList<Theme> themes = new ArrayList<Theme>();

    ThemeManager(Context context) {
        this.context = context.getApplicationContext();
        reload();
    }

    synchronized void reload() {
        themes.clear();
        themes.add(Theme.classic());
        themes.add(Theme.amber());
        themes.add(Theme.ice());
        File dir = themeDirectory();
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        writeExampleIfMissing(dir);
        File[] files = dir.listFiles();
        if (files != null) {
            ArrayList<File> jsonFiles = new ArrayList<File>();
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".json")) {
                    jsonFiles.add(file);
                }
            }
            Collections.sort(jsonFiles, new Comparator<File>() {
                @Override public int compare(File left, File right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            });
            for (File file : jsonFiles) {
                try {
                    StringBuilder builder = new StringBuilder();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) builder.append(line).append('\n');
                    reader.close();
                    themes.add(Theme.fromJson(new JSONObject(builder.toString())));
                } catch (Exception ignored) {
                    // Invalid theme files are skipped so a bad SD-card file cannot brick the launcher.
                }
            }
        }
    }

    synchronized List<Theme> all() {
        return new ArrayList<Theme>(themes);
    }

    synchronized Theme active() {
        String wanted = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "Classic");
        for (Theme theme : themes) {
            if (theme.name.equals(wanted)) return theme;
        }
        return themes.get(0);
    }

    synchronized void activate(Theme theme) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(KEY, theme.name).apply();
    }

    File themeDirectory() {
        return new File(new File(Environment.getExternalStorageDirectory(), "RockboxSolar"), "themes");
    }

    private static void writeExampleIfMissing(File dir) {
        File example = new File(dir, "example-purple.json");
        if (example.exists()) return;
        try {
            FileWriter writer = new FileWriter(example);
            writer.write("{\n" +
                    "  \"name\": \"Purple Parlor\",\n" +
                    "  \"background\": \"#120A1F\",\n" +
                    "  \"foreground\": \"#F5ECFF\",\n" +
                    "  \"accent\": \"#C68CFF\",\n" +
                    "  \"selected\": \"#3C205B\",\n" +
                    "  \"muted\": \"#A68DBD\",\n" +
                    "  \"fontScale\": 1.0\n" +
                    "}\n");
            writer.close();
        } catch (Exception ignored) {
        }
    }
}
