package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

final class AppUi {
    interface TextCallback { void onText(String value); }
    interface ConfirmCallback { void onConfirm(); }

    static final class Screen {
        final LinearLayout root;
        final TextView title;
        final TextView subtitle;
        final ListView list;
        Screen(LinearLayout root, TextView title, TextView subtitle, ListView list) {
            this.root = root; this.title = title; this.subtitle = subtitle; this.list = list;
        }
    }

    private AppUi() { }

    static Screen screen(Activity activity, String titleText, String subtitleText) {
        activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 12);
        root.setPadding(p, p, p, p);
        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = new TextView(activity);
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(activity, 3), 0, dp(activity, 7));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(activity);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        activity.setContentView(root);
        list.requestFocus();
        return new Screen(root, title, subtitle, list);
    }

    static void prompt(Context context, String title, String hint, String initial,
                       int inputType, final TextCallback callback) {
        final EditText input = new EditText(context);
        input.setHint(hint);
        input.setText(initial == null ? "" : initial);
        input.setInputType(inputType);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context).setTitle(title).setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        callback.onText(input.getText().toString().trim());
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void promptText(Context context, String title, String hint, String initial,
                           TextCallback callback) {
        prompt(context, title, hint, initial, InputType.TYPE_CLASS_TEXT, callback);
    }

    static void promptPassword(Context context, String title, TextCallback callback) {
        prompt(context, title, "Password", "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, callback);
    }

    static void confirm(Context context, String title, String message, final ConfirmCallback callback) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Continue", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { callback.onConfirm(); }
                }).setNegativeButton("Cancel", null).show();
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView text(Context context, String value, float size) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        return text;
    }

    static void hideKeyboard(Activity activity, View view) {
        try {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception ignored) { }
    }
}
