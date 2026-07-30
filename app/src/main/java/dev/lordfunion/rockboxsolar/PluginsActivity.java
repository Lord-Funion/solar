package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import java.util.Arrays;

public final class PluginsActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppUi.Screen ui = AppUi.screen(this, "Plugins & Games", "Android-native wheel-friendly tools inspired by the Rockbox plugin menu");
        final String[] names = {"Snake", "Stopwatch", "Calculator", "Dice roller", "Starfield visualizer", "System information"};
        ui.list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, Arrays.asList(names)));
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent;
                if (position == 0) intent = new Intent(PluginsActivity.this, SnakeActivity.class);
                else { intent = new Intent(PluginsActivity.this, UtilityPluginActivity.class); intent.putExtra("plugin", names[position]); }
                startActivity(intent);
            }
        });
    }
}
