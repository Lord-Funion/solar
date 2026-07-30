package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.conscrypt.Conscrypt;

import java.io.File;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;

public final class ReachActivity extends Activity implements NativeSoulseekClient.Listener {
    private static final String PREFS="reach_native";private final ExecutorService worker=Executors.newSingleThreadExecutor();private AppUi.Screen ui;private ArrayAdapter<String>adapter;private NativeSoulseekClient nativeClient;private final ArrayList<NativeSoulseekClient.Result>nativeResults=new ArrayList<NativeSoulseekClient.Result>();private final ArrayList<SlskdClient.Result>slskdResults=new ArrayList<SlskdClient.Result>();private int mode=0;
    @Override protected void onCreate(Bundle state){super.onCreate(state);try{Security.insertProviderAt(Conscrypt.newProvider(),1);}catch(Throwable ignored){}ui=AppUi.screen(this,"Reach / Soulseek","Native experimental protocol mode and full slskd queued-download mode");adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());ui.list.setAdapter(adapter);nativeClient=new NativeSoulseekClient(this);showMenu();ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?>p,View v,int pos,long id){if(mode==0)menu(pos);else if(mode==1&&pos<nativeResults.size())confirmNative(nativeResults.get(pos));else if(mode==2&&pos<slskdResults.size())confirmSlskd(slskdResults.get(pos));}});}
    private SharedPreferences prefs(){return getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private void showMenu(){mode=0;adapter.clear();adapter.add("Native Soulseek: configure account");adapter.add("Native Soulseek: connect");adapter.add("Native Soulseek: search");adapter.add("Native Soulseek: disconnect");adapter.add("slskd: configure server");adapter.add("slskd: search and queue downloads");adapter.add("slskd: connection test");adapter.notifyDataSetChanged();}
    private void menu(int p){if(p==0)configureNative();else if(p==1)connectNative();else if(p==2)searchNative();else if(p==3){nativeClient.disconnect();ui.subtitle.setText("Disconnected");}else if(p==4)configureSlskd();else if(p==5)searchSlskd();else if(p==6)testSlskd();}
    private void configureNative(){AppUi.promptText(this,"Soulseek username","Existing or new username",prefs().getString("user",""),new AppUi.TextCallback(){public void onText(final String u){AppUi.promptPassword(ReachActivity.this,"Soulseek password",new AppUi.TextCallback(){public void onText(String pass){prefs().edit().putString("user",u).putString("pass",pass).apply();ui.subtitle.setText("Native account configured for "+u);}});}});}
    private void connectNative(){String u=prefs().getString("user",""),p=prefs().getString("pass","");if(u.length()==0||p.length()==0){Toast.makeText(this,"Configure the account first",Toast.LENGTH_LONG).show();return;}nativeClient.connect(prefs().getString("host","vps.slsknet.org"),prefs().getInt("port",2271),u,p);}
    private void searchNative(){AppUi.promptText(this,"Soulseek search","artist album track","",new AppUi.TextCallback(){public void onText(String q){mode=1;nativeResults.clear();adapter.clear();adapter.notifyDataSetChanged();nativeClient.search(q);}});}
    private void confirmNative(final NativeSoulseekClient.Result r){new AlertDialog.Builder(this).setTitle(new File(r.filename.replace('\\','/')).getName()).setMessage("User: "+r.username+"\nRemote path: "+r.filename+"\nSize: "+r.size+" bytes\nQueue: "+r.queueLength).setPositiveButton("Queue download",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){nativeClient.queue(r);}}).setNegativeButton("Cancel",null).show();}
    private void configureSlskd(){AppUi.promptText(this,"slskd base URL","http://computer:5030",prefs().getString("slskd_url","http://192.168.1.2:5030"),new AppUi.TextCallback(){public void onText(final String url){AppUi.prompt(ReachActivity.this,"slskd API key","X-API-Key",prefs().getString("slskd_key",""),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,new AppUi.TextCallback(){public void onText(String key){prefs().edit().putString("slskd_url",url).putString("slskd_key",key).apply();ui.subtitle.setText("slskd configured");}});}});}
    private SlskdClient slskd(){return new SlskdClient(new OkHttpClient.Builder().build(),prefs().getString("slskd_url","http://192.168.1.2:5030"),prefs().getString("slskd_key",""));}
    private void testSlskd(){ui.subtitle.setText("Testing slskd…");worker.execute(new Runnable(){public void run(){try{final String s=slskd().state();runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText("slskd connected");new AlertDialog.Builder(ReachActivity.this).setTitle("slskd state").setMessage(s).setPositiveButton("OK",null).show();}});}catch(final Exception e){runOnUiThread(new Runnable(){public void run(){Toast.makeText(ReachActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();ui.subtitle.setText("slskd failed");}});}}});}
    private void searchSlskd(){AppUi.promptText(this,"Soulseek search through slskd","artist album track","",new AppUi.TextCallback(){public void onText(final String q){ui.subtitle.setText("Searching through slskd for 15 seconds…");worker.execute(new Runnable(){public void run(){try{final List<SlskdClient.Result>found=slskd().search(q);runOnUiThread(new Runnable(){public void run(){mode=2;slskdResults.clear();slskdResults.addAll(found);adapter.clear();for(SlskdClient.Result r:found)adapter.add(r.label());adapter.notifyDataSetChanged();ui.subtitle.setText(found.size()+" files from slskd");}});}catch(final Exception e){runOnUiThread(new Runnable(){public void run(){Toast.makeText(ReachActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}});}}});}});}
    private void confirmSlskd(final SlskdClient.Result r){new AlertDialog.Builder(this).setTitle(new File(r.filename().replace('\\','/')).getName()).setMessage("User: "+r.username+"\nRemote path: "+r.filename()+"\nThe completed file is stored by your slskd server. Use SSH/SCP to retrieve it automatically if the server is on another computer.").setPositiveButton("Queue in slskd",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){worker.execute(new Runnable(){public void run(){try{slskd().enqueue(r);runOnUiThread(new Runnable(){public void run(){Toast.makeText(ReachActivity.this,"Queued in slskd",Toast.LENGTH_LONG).show();}});}catch(final Exception e){runOnUiThread(new Runnable(){public void run(){Toast.makeText(ReachActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}});}}});}}).setNegativeButton("Cancel",null).show();}
    @Override public void onState(final String state){runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText(state);}});}
    @Override public void onResultsChanged(){runOnUiThread(new Runnable(){public void run(){mode=1;nativeResults.clear();nativeResults.addAll(nativeClient.results());adapter.clear();for(NativeSoulseekClient.Result r:nativeResults)adapter.add(r.label());adapter.notifyDataSetChanged();ui.subtitle.setText(nativeResults.size()+" native results");}});}
    @Override public void onTransfer(final String state,final File file){runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText(state+(file==null?"":" • "+file.getAbsolutePath()));if(file!=null)sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,android.net.Uri.fromFile(file)));}});}
    @Override public void onBackPressed(){if(mode!=0){showMenu();return;}super.onBackPressed();}
    @Override protected void onDestroy(){nativeClient.shutdown();worker.shutdownNow();super.onDestroy();}
}
