package com.solar.launcher.phone;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.solar.launcher.theme.ThemeManager;

import java.io.File;

/**
 * 2026-07-20 — Flip-back customize face: storage onboarding, colours, body/wheel photos.
 * Storage is required on Phone Solar (Y1/Y2/A5 keep hardcoded mounts — never shown here).
 * Was: optional storage + tiny texture hint. Now: onboarding-first + ideal skin size.
 * Reversal: hide panel — prefs keep last values until cleared.
 */
public final class PhoneChromeCustomizePanel extends ScrollView {

    public interface Listener {
        void onColorsChanged();
        void onTextureChanged();
        void onStorageChanged();
        void onClose();
        /** Request image pick; forBody true → body texture. */
        void requestImagePick(boolean forBody);
        /** Request storage folder pick (Solar browser or tree). */
        void requestStoragePick();
    }

    public static final int REQ_BODY_IMAGE = 0x5043; // 'PC'
    public static final int REQ_WHEEL_IMAGE = 0x5044;
    public static final int REQ_STORAGE_TREE = 0x5045;

    private Listener listener;
    private final LinearLayout column;
    private PhoneChromePolicy.LayoutMetrics metrics;

    public PhoneChromeCustomizePanel(Context context) {
        super(context);
        setFillViewport(true);
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        column.setPadding(pad, pad, pad, pad);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        rebuild();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Feed live chrome metrics so the ideal skin size line stays accurate. */
    public void setLayoutMetrics(PhoneChromePolicy.LayoutMetrics m) {
        metrics = m;
    }

    /** Rebuild rows from current prefs (call after pick results). */
    public void rebuild() {
        column.removeAllViews();
        Context ctx = getContext();
        int text = ThemeManager.getTextColorPrimary();
        int hint = ThemeManager.getTextColorSecondary();
        boolean needStorage = PhoneStorageRoots.needsStoragePrompt(ctx);

        if (needStorage) {
            // 2026-07-20 — First-run storage onboarding before look options.
            column.addView(header("Set up Storage", text));
            column.addView(hintRow(
                    "Phone Solar needs a folder for music, downloads, podcasts, and radio. "
                            + "Y1/Y2/A5 players use built-in paths — phones pick one here.",
                    hint));
            column.addView(hintRow(
                    "Solar creates Internal/ and MicroSD/ inside that folder.",
                    hint));
            column.addView(actionRow("Choose storage folder…", new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) listener.requestStoragePick();
                }
            }));
            column.addView(actionRow("Use SolarPhone on this device", new OnClickListener() {
                @Override
                public void onClick(View v) {
                    seedDefaultSolarPhone((Activity) (ctx instanceof Activity ? ctx : null));
                }
            }));
            column.addView(hintRow("Looks can wait — set Storage first so downloads work.", hint));
            return;
        }

        column.addView(header("Phone look", text));
        String skinHint = metrics != null
                ? PhoneSkinSize.idealBodySkinHint(metrics)
                : PhoneSkinSize.idealBodySkinHint(ctx);
        if (skinHint.length() > 0) {
            column.addView(hintRow(skinHint, hint));
        }
        column.addView(hintRow(
                "Body photo is scaled to fill the hardware face. Pick colours or your own image.",
                hint));

        column.addView(header("Colours", text));
        for (int i = 0; i < PhoneChromePrefs.CURATED_PAIRS.length; i++) {
            final int body = PhoneChromePrefs.CURATED_PAIRS[i][0];
            final int wheel = PhoneChromePrefs.CURATED_PAIRS[i][1];
            column.addView(colorPairRow(i + 1, body, wheel));
        }

        column.addView(header("Body texture", text));
        if (skinHint.length() > 0) {
            column.addView(hintRow(skinHint, hint));
        }
        column.addView(actionRow("Body photo…", new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.requestImagePick(true);
            }
        }));
        column.addView(actionRow("Wheel photo…", new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.requestImagePick(false);
            }
        }));
        column.addView(actionRow("Clear photos", new OnClickListener() {
            @Override
            public void onClick(View v) {
                PhoneChromePrefs.clearBodyTexture(ctx);
                PhoneChromePrefs.clearWheelTexture(ctx);
                if (listener != null) listener.onTextureChanged();
            }
        }));

        column.addView(header("Storage", text));
        String path = PhoneChromePrefs.storageRootPath(ctx);
        column.addView(hintRow(path != null ? path : "Not set", hint));
        column.addView(hintRow(
                "Internal/ and MicroSD/ live under this folder. Change anytime.",
                hint));
        column.addView(actionRow("Choose storage folder…", new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.requestStoragePick();
            }
        }));
        if (path != null && path.length() > 0) {
            column.addView(actionRow("Clear storage folder", new OnClickListener() {
                @Override
                public void onClick(View v) {
                    PhoneChromePrefs.setStorageRootPath(ctx, null);
                    if (listener != null) listener.onStorageChanged();
                    rebuild();
                }
            }));
        }

        column.addView(actionRow("Done", new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClose();
            }
        }));
    }

    /**
     * 2026-07-20 — One-tap default: try shared SolarPhone, else app-dir ladder.
     * Was: fail toast when Environment mkdirs failed — user stuck on onboarding.
     * Reversal: restore early return on mkdirs fail without PhoneStorageAccess.
     */
    private void seedDefaultSolarPhone(Activity activity) {
        Context ctx = getContext();
        if (activity == null && ctx instanceof Activity) {
            activity = (Activity) ctx;
        }
        File candidate = null;
        File base = Environment.getExternalStorageDirectory();
        if (base != null && base.isDirectory()) {
            candidate = new File(base, PhoneStorageAccess.APP_FOLDER);
        }
        // API 23–28: ask once for shared path; if denied, ladder still uses app dirs.
        if (activity != null && candidate != null
                && PhoneStorageRuntimePerms.requestIfNeeded(activity, candidate)) {
            // Pending grant — PhoneChromeHost retries seed after result.
            PhoneChromeHost.pendingSeedSolarPhone(activity);
            return;
        }
        applyResolvedStorage(ctx, candidate, true);
        if (listener != null) listener.onStorageChanged();
        rebuild();
    }

    /**
     * 2026-07-20 — Run ladder, persist winner, toast path or calm fallback copy.
     * Shared by seed / pick fallback / post-permission retry.
     */
    public static boolean applyResolvedStorage(Context ctx, File candidate, boolean showToast) {
        if (ctx == null) return false;
        File win = PhoneStorageAccess.resolveAndPersist(ctx, candidate);
        if (win == null) {
            if (showToast) {
                Toast.makeText(ctx, "No storage folder found", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        if (showToast) {
            if (PhoneStorageAccess.usedFallback(candidate, win)) {
                Toast.makeText(ctx, PhoneStorageAccess.FALLBACK_TOAST, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "Storage: " + win.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private View colorPairRow(int index, final int body, final int wheel) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(8);
        row.setPadding(pad, pad, pad, pad);

        View swatch = new View(getContext());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(36), dp(36));
        swatch.setLayoutParams(sp);
        swatch.setBackgroundColor(body);
        row.addView(swatch);

        View swatch2 = new View(getContext());
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(dp(24), dp(24));
        sp2.leftMargin = dp(6);
        swatch2.setLayoutParams(sp2);
        swatch2.setBackgroundColor(wheel);
        row.addView(swatch2);

        TextView tv = new TextView(getContext());
        tv.setText("  Pair " + index);
        tv.setTextColor(ThemeManager.getTextColorPrimary());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.addView(tv);

        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                PhoneChromePrefs.setColors(getContext(), body, wheel);
                if (listener != null) listener.onColorsChanged();
            }
        });
        return row;
    }

    private TextView header(String title, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(0, dp(10), 0, dp(4));
        return tv;
    }

    private TextView hintRow(String msg, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(msg);
        tv.setTextColor(color);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(0, 0, 0, dp(6));
        return tv;
    }

    private Button actionRow(String label, OnClickListener click) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(click);
        return b;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    /**
     * 2026-07-20 — Build an image-pick intent (API 17 GET_CONTENT; OPEN_DOCUMENT when present).
     */
    public static Intent imagePickIntent() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 19) {
            intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
        }
        return intent;
    }

    /**
     * 2026-07-20 — Storage pick: tree on API 21+, else shared SolarPhone via writable ladder.
     * Was: hard-fail toast when mkdirs failed. Now: fall through to app dirs so onboarding completes.
     */
    public static void pickStorageFallback(Activity activity, Listener listener) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                activity.startActivityForResult(i, REQ_STORAGE_TREE);
                return;
            } catch (Throwable ignored) {}
        }
        File candidate = null;
        File base = Environment.getExternalStorageDirectory();
        if (base != null && base.isDirectory()) {
            candidate = new File(base, PhoneStorageAccess.APP_FOLDER);
        }
        if (PhoneStorageRuntimePerms.requestIfNeeded(activity, candidate)) {
            PhoneChromeHost.pendingSeedSolarPhone(activity);
            return;
        }
        applyResolvedStorage(activity, candidate, true);
        if (listener != null) listener.onStorageChanged();
    }

    /**
     * 2026-07-20 — Apply SAF tree URI: persist URI, best-effort path, writable ladder.
     * Was: assume Environment + relative path always writable. Now: toast + app-dir fallback.
     * Reversal: restore naive colon-split + mkdirs without PhoneStorageAccess.
     */
    public static void applyTreeUri(Context ctx, Uri treeUri) {
        if (ctx == null || treeUri == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 19) {
                ctx.getContentResolver().takePersistableUriPermission(treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        } catch (Throwable ignored) {}
        // Keep URI for a future DocumentFile pass — File I/O still uses the ladder path.
        try {
            PhoneChromePrefs.setStorageTreeUri(ctx, treeUri.toString());
        } catch (Throwable ignored) {}

        File candidate = bestEffortTreeFile(treeUri);
        // Shared path on API 23–28 may need a runtime grant before mkdir works.
        if (ctx instanceof Activity) {
            Activity a = (Activity) ctx;
            if (PhoneStorageRuntimePerms.requestIfNeeded(a, candidate)) {
                PhoneChromeHost.pendingApplyTreeUri(a, treeUri);
                return;
            }
        }
        applyResolvedStorage(ctx, candidate, true);
    }

    /**
     * 2026-07-20 — Best-effort File from a document-tree URI (primary volume only).
     * May be unwritable on scoped storage — ladder handles that.
     */
    static File bestEffortTreeFile(Uri treeUri) {
        if (treeUri == null) return null;
        String path = treeUri.getPath();
        if (path == null || !path.contains(":")) return null;
        int colon = path.lastIndexOf(':');
        String rel = path.substring(colon + 1);
        File base = Environment.getExternalStorageDirectory();
        if (base == null) return null;
        return rel.length() == 0 ? base : new File(base, rel);
    }
}
