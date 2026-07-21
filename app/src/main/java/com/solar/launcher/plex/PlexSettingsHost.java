package com.solar.launcher.plex;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.solar.launcher.R;
import com.solar.launcher.RowKeys;
import com.solar.launcher.SettingsScreens;

/**
 * 2026-07-06: Settings → Plex server URL, user, password, connection test.
 */
public final class PlexSettingsHost {

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

    /** 2026-07-14: Avoid colliding with Navidrome URL/USER keyboard purposes 17–19. */
    public static final int KEYBOARD_URL = 21;
    public static final int KEYBOARD_TOKEN = 22;

    private final Actions actions;

    public PlexSettingsHost(Actions actions) {
        this.actions = actions;
    }

    public void build() {
        actions.setSettingsSubScreen(SettingsScreens.PLEX);
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

        addEditableRow(RowKeys.PLEX_URL, R.string.plex_settings_url, KEYBOARD_URL);
        addEditableRow(RowKeys.PLEX_TOKEN, R.string.plex_settings_token, KEYBOARD_TOKEN);

        LinearLayout test = actions.createSettingsRow(RowKeys.PLEX_TEST, R.string.plex_settings_test, false);
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
        if (RowKeys.PLEX_URL.equals(rowKey)) return p.getString("plex_url", "");
        if (RowKeys.PLEX_TOKEN.equals(rowKey)) return p.getString("plex_token", "");
        return "";
    }

    public void finishKeyboard(int purpose, String text) {
        SharedPreferences p = actions.prefs();
        String url = p.getString("plex_url", "");
        String token = p.getString("plex_token", "");
        if (purpose == KEYBOARD_URL) url = text;
        else if (purpose == KEYBOARD_TOKEN) token = text;
        PlexPrefs.save(actions.activity(), p, url, token);
        if (purpose == KEYBOARD_URL) actions.refreshSettingsPreview(RowKeys.PLEX_URL);
        else if (purpose == KEYBOARD_TOKEN) actions.refreshSettingsPreview(RowKeys.PLEX_TOKEN);
    }

    private void testConnection() {
        if (!PlexClient.getInstance().isConfigured()) {
            Toast.makeText(actions.activity(),
                    actions.activity().getString(R.string.plex_not_configured),
                    Toast.LENGTH_LONG).show();
            return;
        }
        PlexClient.getInstance().ping(new PlexClient.Callback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                Toast.makeText(actions.activity(),
                        actions.activity().getString(ok != null && ok
                                ? R.string.plex_test_ok : R.string.plex_test_fail),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                Toast.makeText(actions.activity(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String previewValue(SharedPreferences prefs, String rowKey) {
        if (prefs == null) return "";
        if (RowKeys.PLEX_URL.equals(rowKey)) {
            String u = prefs.getString("plex_url", "");
            return u != null && !u.isEmpty() ? u : "-";
        }
        if (RowKeys.PLEX_TOKEN.equals(rowKey)) {
            String p = prefs.getString("plex_token", "");
            return p != null && !p.isEmpty() ? "••••" : "-";
        }
        return "";
    }
}
