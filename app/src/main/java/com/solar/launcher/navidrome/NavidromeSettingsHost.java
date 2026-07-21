package com.solar.launcher.navidrome;

import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.solar.launcher.R;
import com.solar.launcher.RowKeys;
import com.solar.launcher.SettingsScreens;

/**
 * 2026-07-06: Settings → Navidrome server URL, user, password, connection test.
 */
public final class NavidromeSettingsHost {

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

    public static final int KEYBOARD_URL = 17;
    public static final int KEYBOARD_USER = 18;
    public static final int KEYBOARD_PASS = 19;

    private final Actions actions;

    public NavidromeSettingsHost(Actions actions) {
        this.actions = actions;
    }

    public void build() {
        actions.setSettingsSubScreen(SettingsScreens.NAVIDROME);
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

        addEditableRow(RowKeys.NAVIDROME_URL, R.string.navidrome_settings_url, KEYBOARD_URL);
        addEditableRow(RowKeys.NAVIDROME_USER, R.string.navidrome_settings_user, KEYBOARD_USER);
        addEditableRow(RowKeys.NAVIDROME_PASS, R.string.navidrome_settings_pass, KEYBOARD_PASS);

        LinearLayout test = actions.createSettingsRow(RowKeys.NAVIDROME_TEST, R.string.navidrome_settings_test, false);
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
        if (RowKeys.NAVIDROME_URL.equals(rowKey)) return p.getString("navidrome_url", "");
        if (RowKeys.NAVIDROME_USER.equals(rowKey)) return p.getString("navidrome_user", "");
        if (RowKeys.NAVIDROME_PASS.equals(rowKey)) return p.getString("navidrome_pass", "");
        return "";
    }

    public void finishKeyboard(int purpose, String text) {
        SharedPreferences p = actions.prefs();
        String url = p.getString("navidrome_url", "");
        String user = p.getString("navidrome_user", "");
        String pass = p.getString("navidrome_pass", "");
        if (purpose == KEYBOARD_URL) url = text;
        else if (purpose == KEYBOARD_USER) user = text;
        else if (purpose == KEYBOARD_PASS) pass = text;
        NavidromePrefs.save(actions.activity(), p, url, user, pass);
        if (purpose == KEYBOARD_URL) actions.refreshSettingsPreview(RowKeys.NAVIDROME_URL);
        else if (purpose == KEYBOARD_USER) actions.refreshSettingsPreview(RowKeys.NAVIDROME_USER);
        else if (purpose == KEYBOARD_PASS) actions.refreshSettingsPreview(RowKeys.NAVIDROME_PASS);
    }

    private void testConnection() {
        if (!NavidromeClient.getInstance().isConfigured()) {
            Toast.makeText(actions.activity(),
                    actions.activity().getString(R.string.navidrome_not_configured),
                    Toast.LENGTH_LONG).show();
            return;
        }
        NavidromeClient.getInstance().ping(new NavidromeClient.Callback<Boolean>() {
            @Override public void onSuccess(Boolean ok) {
                Toast.makeText(actions.activity(),
                        actions.activity().getString(ok != null && ok
                                ? R.string.navidrome_test_ok : R.string.navidrome_test_fail),
                        Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                Toast.makeText(actions.activity(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static String previewValue(SharedPreferences prefs, String rowKey) {
        if (prefs == null) return "";
        if (RowKeys.NAVIDROME_URL.equals(rowKey)) {
            String u = prefs.getString("navidrome_url", "");
            return u != null && !u.isEmpty() ? u : "-";
        }
        if (RowKeys.NAVIDROME_USER.equals(rowKey)) {
            String u = prefs.getString("navidrome_user", "");
            return u != null && !u.isEmpty() ? u : "-";
        }
        if (RowKeys.NAVIDROME_PASS.equals(rowKey)) {
            String p = prefs.getString("navidrome_pass", "");
            return p != null && !p.isEmpty() ? "••••" : "-";
        }
        return "";
    }
}
