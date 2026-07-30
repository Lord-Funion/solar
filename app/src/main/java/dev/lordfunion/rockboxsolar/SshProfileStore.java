package dev.lordfunion.rockboxsolar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class SshProfileStore {
    private static final String PREFS = "rockbox_solar_ssh";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_HISTORY = "command_history";
    private final SharedPreferences preferences;

    SshProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    List<SshProfile> all() {
        ArrayList<SshProfile> profiles = new ArrayList<SshProfile>();
        String raw = preferences.getString(KEY_PROFILES, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                SshProfile profile = SshProfile.fromJson(array.optJSONObject(i));
                if (profile.host.length() > 0 && profile.user.length() > 0) profiles.add(profile);
            }
        } catch (Exception ignored) { }
        return profiles;
    }

    void save(SshProfile profile) {
        List<SshProfile> profiles = all();
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) profiles.add(profile);
        writeProfiles(profiles);
    }

    void delete(String id) {
        List<SshProfile> profiles = all();
        for (int i = profiles.size() - 1; i >= 0; i--) {
            if (profiles.get(i).id.equals(id)) profiles.remove(i);
        }
        writeProfiles(profiles);
    }

    List<String> history() {
        ArrayList<String> commands = new ArrayList<String>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String command = array.optString(i, "").trim();
                if (command.length() > 0) commands.add(command);
            }
        } catch (Exception ignored) { }
        return commands;
    }

    void rememberCommand(String command) {
        command = command == null ? "" : command.trim();
        if (command.length() == 0) return;
        List<String> history = history();
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i).equals(command)) history.remove(i);
        }
        history.add(0, command);
        while (history.size() > 20) history.remove(history.size() - 1);
        JSONArray array = new JSONArray();
        for (String value : history) array.put(value);
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    private void writeProfiles(List<SshProfile> profiles) {
        JSONArray array = new JSONArray();
        for (SshProfile profile : profiles) {
            try { array.put(profile.toJson()); } catch (Exception ignored) { }
        }
        preferences.edit().putString(KEY_PROFILES, array.toString()).apply();
    }
}
