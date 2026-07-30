package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public final class LauncherActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Rockbox Solar 0.2");
        title.setTextSize(26f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Player, YouTube metadata, remote SSH commands, and SCP retrieval");
        subtitle.setTextSize(15f);
        subtitle.setPadding(0, dp(4), 0, dp(8));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        ListView menu = new ListView(this);
        menu.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1,
                Arrays.asList("Rockbox Player", "YouTube Search", "Remote SSH + SCP", "Open Solar Reach", "Wi-Fi", "Bluetooth", "Android Settings")));
        menu.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        menu.setSelection(0);
        menu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (position == 0) startActivity(new Intent(LauncherActivity.this, MainActivity.class));
                    else if (position == 1) startActivity(new Intent(LauncherActivity.this, YouTubeActivity.class));
                    else if (position == 2) startActivity(new Intent(LauncherActivity.this, SshActivity.class));
                    else if (position == 3) {
                        if (!SolarBridge.open(LauncherActivity.this, "reach")) {
                            Toast.makeText(LauncherActivity.this, "Solar is not installed", Toast.LENGTH_LONG).show();
                        }
                    } else if (position == 4) startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    else if (position == 5) startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    else if (position == 6) startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(LauncherActivity.this, "That feature is unavailable on this firmware", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(menu, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        menu.requestFocus();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
