package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.conscrypt.Conscrypt;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.Security;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class UpdateActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private AppUi.Screen ui;private ArrayAdapter<String>adapter;private SharedPreferences prefs;private final OkHttpClient client=new OkHttpClient.Builder().build();private JSONObject manifest;
    @Override protected void onCreate(Bundle state){super.onCreate(state);try{Security.insertProviderAt(Conscrypt.newProvider(),1);}catch(Throwable ignored){}prefs=getSharedPreferences("rockbox_solar_updates",Context.MODE_PRIVATE);ui=AppUi.screen(this,"Updates & ROM Manager","Checksum-verified downloads with explicit Y1 Type A/Type B safeguards");adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());ui.list.setAdapter(adapter);refresh();ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?>p,View v,int pos,long id){if(pos==0)setManifest();else if(pos==1)check();else if(pos==2)downloadApk();else if(pos==3)downloadRom();else if(pos==4)showPreflight();else if(pos==5)openFolder();}});}
    private void refresh(){adapter.clear();adapter.add("Update manifest URL — "+prefs.getString("url","not configured"));adapter.add("Check update manifest");adapter.add("Download and verify APK update");adapter.add("Download and verify ROM package");adapter.add("Y1 ROM preflight and recovery checklist");adapter.add("Show update storage folder");adapter.notifyDataSetChanged();}
    private void setManifest(){AppUi.prompt(this,"Update manifest URL","https://example.com/rockbox-solar.json",prefs.getString("url",""),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI,new AppUi.TextCallback(){public void onText(String value){prefs.edit().putString("url",value).apply();refresh();}});}
    private void check(){final String url=prefs.getString("url","");if(url.length()==0){Toast.makeText(this,"Configure a manifest URL first",Toast.LENGTH_LONG).show();return;}ui.subtitle.setText("Checking manifest…");worker.execute(new Runnable(){public void run(){try{Request q=new Request.Builder().url(url).build();Response r=client.newCall(q).execute();try{if(!r.isSuccessful()||r.body()==null)throw new Exception("HTTP "+r.code());manifest=new JSONObject(r.body().string());}finally{r.close();}runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText("Available: "+manifest.optString("versionName","unknown"));new AlertDialog.Builder(UpdateActivity.this).setTitle("Update manifest").setMessage(manifest.toString()).setPositiveButton("OK",null).show();}});}catch(final Exception e){runOnUiThread(new Runnable(){public void run(){Toast.makeText(UpdateActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();ui.subtitle.setText("Check failed");}});}}});}
    private void downloadApk(){ensureManifest(new Runnable(){public void run(){String url=manifest.optString("apkUrl","");String sha=manifest.optString("apkSha256","");if(url.length()==0||sha.length()==0){toast("Manifest lacks apkUrl/apkSha256");return;}File dir=folder("APK");download(url,new File(dir,"RockboxSolar-"+manifest.optString("versionName","update")+".apk"),sha,true);}});}
    private void downloadRom(){ensureManifest(new Runnable(){public void run(){chooseVariant(new Variant(){public void selected(String variant){String key="rom"+variant+"Url",hash="rom"+variant+"Sha256";String url=manifest.optString(key,manifest.optString("romUrl","")),sha=manifest.optString(hash,manifest.optString("romSha256",""));if(url.length()==0||sha.length()==0){toast("Manifest lacks "+key+"/"+hash);return;}File dir=folder("ROM/Type-"+variant);download(url,new File(dir,"rom.zip"),sha,false);}});}});}
    private interface Variant{void selected(String v);}private void chooseVariant(final Variant c){new AlertDialog.Builder(this).setTitle("Select verified Y1 hardware type").setMessage("Do not guess. Type A and Type B boot-critical images are not interchangeable.").setItems(new String[]{"Type A","Type B","Cancel"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(w<2)c.selected(w==0?"A":"B");}}).show();}
    private void ensureManifest(final Runnable run){if(manifest!=null){run.run();return;}Toast.makeText(this,"Check the manifest first",Toast.LENGTH_LONG).show();}
    private void download(final String url,final File dest,final String expected,final boolean install){ui.subtitle.setText("Downloading "+dest.getName()+"…");worker.execute(new Runnable(){public void run(){File part=new File(dest.getAbsolutePath()+".part");try{Request q=new Request.Builder().url(url).build();Response r=client.newCall(q).execute();try{if(!r.isSuccessful()||r.body()==null)throw new Exception("HTTP "+r.code());InputStream in=r.body().byteStream();FileOutputStream out=new FileOutputStream(part);byte[]b=new byte[32768];int n;while((n=in.read(b))>=0)out.write(b,0,n);out.flush();out.getFD().sync();out.close();in.close();}finally{r.close();}String actual=sha256(part);if(!actual.equalsIgnoreCase(expected))throw new Exception("SHA-256 mismatch\nExpected "+expected+"\nActual "+actual);if(dest.exists())dest.delete();if(!part.renameTo(dest))throw new Exception("Final rename failed");runOnUiThread(new Runnable(){public void run(){ui.subtitle.setText("Verified: "+dest.getAbsolutePath());if(install)offerInstall(dest);else offerRom(dest);}});}catch(final Exception e){part.delete();runOnUiThread(new Runnable(){public void run(){toast(e.getMessage());ui.subtitle.setText("Download failed");}});}}});}
    private void offerInstall(final File apk){new AlertDialog.Builder(this).setTitle("APK verified").setMessage("Install this debug/update APK now? Android will show its package-installer confirmation.").setPositiveButton("Install",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(DownloadProvider.uri(apk),"application/vnd.android.package-archive");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("Installer unavailable: "+e.getMessage());}}}).setNegativeButton("Later",null).show();}
    private void offerRom(final File rom){new AlertDialog.Builder(this).setTitle("ROM verified and staged").setMessage("The ROM was not flashed. It is stored at:\n"+rom.getAbsolutePath()+"\n\nUse the stock Innioasis updater or audited flashing workflow only after a complete partition backup and correct Type A/Type B verification.").setPositiveButton("Try stock updater",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{Intent i=new Intent(Intent.ACTION_VIEW,DownloadProvider.uri(rom));i.setType("application/zip");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("No updater accepted the file");}}}).setNegativeButton("Close",null).show();}
    private void showPreflight(){String text="1. Record whether the device is verified Type A or Type B.\n2. Back up preloader, lk, boot, recovery, system and userdata before writes.\n3. Verify all package SHA-256 hashes.\n4. Keep SP Flash Tool/MTKClient and the matching stock image ready.\n5. Confirm ADB and hardware keys work before replacing HOME.\n6. Never try one hardware variant and then the other.\n7. This APK only stages packages; it never flashes boot-critical partitions automatically.";new AlertDialog.Builder(this).setTitle("ROM preflight").setMessage(text).setPositiveButton("I understand",null).show();}
    private void openFolder(){ui.subtitle.setText(folder("").getAbsolutePath());}
    private File folder(String child){File f=new File(new File(Environment.getExternalStorageDirectory(),"RockboxSolar/Updates"),child);f.mkdirs();return f;}
    private static String sha256(File file)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");InputStream in=new java.io.FileInputStream(file);byte[]b=new byte[32768];int n;while((n=in.read(b))>=0)md.update(b,0,n);in.close();StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x&255));return s.toString();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}@Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
