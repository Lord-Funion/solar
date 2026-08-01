package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.Arrays;

public final class LauncherActivity extends Activity {
    private static final String[] ITEMS = {
            "Rockbox Player", "Reach / Soulseek", "Deezer", "Stem Player", "YouTube Metadata",
            "Remote SSH + SCP", "Plugins & Games", "DSP / Recording / FM", "Y1 Theme Translator",
            "Wi-Fi Manager", "Bluetooth Manager", "Updates & ROM Manager", "Y1 Hardware Validation",
            "Open Original Solar", "Android Settings"
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AppUi.Screen ui = AppUi.screen(this, "Rockbox Solar 0.3", "Unified wheel-first media, network, remote-compute, and device-management shell");
        ui.list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_activated_1, Arrays.asList(ITEMS)));
        ui.list.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE); ui.list.setSelection(0);
        ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    Class<?> target = null;
                    if(position==0)target=MainActivity.class;else if(position==1)target=ReachActivity.class;else if(position==2)target=DeezerActivity.class;
                    else if(position==3)target=StemActivity.class;else if(position==4)target=YouTubeActivity.class;else if(position==5)target=SshActivity.class;
                    else if(position==6)target=PluginsActivity.class;else if(position==7)target=AudioToolsActivity.class;else if(position==8)target=ThemeImportActivity.class;
                    else if(position==9)target=WifiActivity.class;else if(position==10)target=BluetoothActivity.class;else if(position==11)target=UpdateActivity.class;
                    else if(position==12)target=HardwareTestActivity.class;
                    if(target!=null)startActivity(new Intent(LauncherActivity.this,target));
                    else if(position==13){if(!SolarBridge.open(LauncherActivity.this,"home"))Toast.makeText(LauncherActivity.this,"Original Solar is not installed",Toast.LENGTH_LONG).show();}
                    else startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) { Toast.makeText(LauncherActivity.this,"Feature unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
            }
        });
    }
}
