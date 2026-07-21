package com.solar.launcher.jellyfin;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.solar.launcher.R;
import com.solar.launcher.RowKeys;
import com.solar.launcher.SettingsScreens;

/**
 * 2026-07-14: Settings → Jellyfin server URL, user, password, connection test.
 */
public final class JellyfinSettingsHost {

    public interface Actions {
        android.app.Activity activity();
        SharedPreferences prefs();
        void clickFeedback();
        LinearLayout createSettingsRow(String rowKey, int labelRes, boolean submenu);
        LinearLayout createSettingsRow(String rowKey, CharSequence title, boolean submenu);
        Button createListButton(String label);
        void styleSecondaryLabel(Button btn);
        void addSettingsRow(View row);
        void clearSettingsRows();
        void setSettingsSubScreen(String key);
        void updateStatusBarTitle();
        void applyReachBrowseLayoutMode();
        void refreshSettingsPreview(String rowKey);
        void drillSettingsBack(Runnable back);
        void openKeyboard(int purpose, String prefill);
        String previewForRow(String rowKey);
    }

    /** Keyboard purposes — offset so they do not collide with Navidrome 17–19 / Plex 17–18. */
    public static final int KEYBOARD_URL = 30;
    public static final int KEYBOARD_USER = 31;
    public static final int KEYBOARD_PASS = 32;

    private final Actions actions;

    public JellyfinSettingsHost(Actions actions) {
        this.actions = actions;
    }

    public void build() {
        actions.setSettingsSubScreen(SettingsScreens.JELLYFIN);
        actions.updateStatusBarTitle();
        actions.applyReachBrowseLayoutMode();
        actions.clearSettingsRows();

        Button back = actions.createListButton(actions.activity().getString(R.string.common_cancel_back));
        actions.styleSecondaryLabel(back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                actions.clickFeedback();
                actions.drillSettingsBack(null);
            }
        });
        actions.addSettingsRow(back);

        addEditableRow(RowKeys.JELLYFIN_URL, R.string.jellyfin_settings_url, KEYBOARD_URL);
        addEditableRow(RowKeys.JELLYFIN_USER, R.string.jellyfin_settings_user, KEYBOARD_USER);
        addEditableRow(RowKeys.JELLYFIN_PASS, R.string.jellyfin_settings_pass, KEYBOARD_PASS);

        LinearLayout test = actions.createSettingsRow(RowKeys.JELLYFIN_TEST, R.string.jellyfin_settings_test, false);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                actions.clickFeedback();
                testConnection();
            }
        });
        actions.addSettingsRow(test);
    }

    private void addEditableRow(final String rowKey, int labelRes, final int keyboardPurpose) {
        LinearLayout row = actions.createSettingsRow(rowKey, labelRes, false);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                actions.clickFeedback();
                actions.openKeyboard(keyboardPurpose, prefillFor(rowKey));
            }
        });
        actions.addSettingsRow(row);
    }

    private String prefillFor(String rowKey) {
        SharedPreferences p = actions.prefs();
        if (RowKeys.JELLYFIN_URL.equals(rowKey)) return p.getString("jellyfin_url", "");
        if (RowKeys.JELLYFIN_USER.equals(rowKey)) return p.getString("jellyfin_user", "");
        if (RowKeys.JELLYFIN_PASS.equals(rowKey)) return p.getString("jellyfin_pass", "");
        return "";
    }

    public void finishKeyboard(int purpose, String text) {
        SharedPreferences p = actions.prefs();
        String url = p.getString("jellyfin_url", "");
        String user = p.getString("jellyfin_user", "");
        String pass = p.getString("jellyfin_pass", "");
        if (purpose == KEYBOARD_URL) url = text;
        else if (purpose == KEYBOARD_USER) user = text;
        else if (purpose == KEYBOARD_PASS) pass = text;
        JellyfinPrefs.save(actions.activity(), p, url, user, pass);
        if (purpose == KEYBOARD_URL) actions.refreshSettingsPreview(RowKeys.JELLYFIN_URL);
        else if (purpose == KEYBOARD_USER) actions.refreshSettingsPreview(RowKeys.JELLYFIN_USER);
        else if (purpose == KEYBOARD_PASS) actions.refreshSettingsPreview(RowKeys.JELLYFIN_PASS);
    }

    private void testConnection() {
        if (!JellyfinClient.getInstance().isConfigured()) {
            Toast.makeText(actions.activity(),
                    actions.activity().getString(R.string.jellyfin_not_configured),
                    Toast.LENGTH_LONG).show();
            return;
        }
        JellyfinClient.getInstance().ping(new JellyfinClient.Callback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                Toast.makeText(actions.activity(),
                        actions.activity().getString(ok != null && ok
                                ? R.string.jellyfin_test_ok : R.string.jellyfin_test_fail),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                Toast.makeText(actions.activity(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String previewValue(SharedPreferences prefs, String rowKey) {
        if (prefs == null) return "";
        if (RowKeys.JELLYFIN_URL.equals(rowKey)) {
            String u = prefs.getString("jellyfin_url", "");
            return u != null && !u.isEmpty() ? u : "-";
        }
        if (RowKeys.JELLYFIN_USER.equals(rowKey)) {
            String u = prefs.getString("jellyfin_user", "");
            return u != null && !u.isEmpty() ? u : "-";
        }
        if (RowKeys.JELLYFIN_PASS.equals(rowKey)) {
            String p = prefs.getString("jellyfin_pass", "");
            return p != null && !p.isEmpty() ? "••••" : "-";
        }
        return "";
    }
}
