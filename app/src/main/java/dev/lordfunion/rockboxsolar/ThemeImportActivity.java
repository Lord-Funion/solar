package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ThemeImportActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor(); private AppUi.Screen ui;
    @Override protected void onCreate(Bundle state){super.onCreate(state);ui=AppUi.screen(this,"Y1 Theme Translator","Imports Y1 ZIP/APK/theme assets into Rockbox Solar JSON themes");ui.list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1, Arrays.asList("Import a theme archive","Open generated theme folder","Translation limitations")));ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?>p,View v,int pos,long id){if(pos==0)prompt();else if(pos==1)ui.subtitle.setText(new ThemeManager(ThemeImportActivity.this).themeDirectory().getAbsolutePath());else new android.app.AlertDialog.Builder(ThemeImportActivity.this).setTitle("Translation limitations").setMessage("Colors, static backgrounds, JSON/XML/properties palettes, and layout-scale hints are translated. Vendor executables, scripts, animated assets, proprietary fonts, and device-specific widgets are listed in the import report but cannot be executed inside this APK.").setPositiveButton("OK",null).show();}});}
    private void prompt(){File d=new File(Environment.getExternalStorageDirectory(),"Download");AppUi.promptText(this,"Y1 theme archive path","/sdcard/Download/theme.zip",d.getAbsolutePath(),new AppUi.TextCallback(){public void onText(final String value){ui.subtitle.setText("Importing…");worker.execute(new Runnable(){public void run(){try{final Y1ThemeImporter.Report r=new Y1ThemeImporter().importArchive(new File(value),new ThemeManager(ThemeImportActivity.this).themeDirectory());runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText("Imported "+r.themeName);new android.app.AlertDialog.Builder(ThemeImportActivity.this).setTitle("Theme imported").setMessage(r.summary()).setPositiveButton("OK",null).show();}});}catch(final Exception e){runOnUiThread(new Runnable(){public void run(){Toast.makeText(ThemeImportActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();ui.subtitle.setText("Import failed");}});}}});}});}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
