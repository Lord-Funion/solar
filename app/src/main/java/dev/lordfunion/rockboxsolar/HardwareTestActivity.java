package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public final class HardwareTestActivity extends Activity {
    private AppUi.Screen ui;private ArrayAdapter<String>adapter;private JSONObject report=new JSONObject();private JSONArray keys=new JSONArray();private String variant="Unverified";
    @Override protected void onCreate(Bundle state){super.onCreate(state);ui=AppUi.screen(this,"Y1 Hardware Validation","Run on a physical player; press every wheel/button key and export the report");adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>());ui.list.setAdapter(adapter);try{report.put("createdAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US).format(new Date()));report.put("manufacturer",Build.MANUFACTURER);report.put("model",Build.MODEL);report.put("device",Build.DEVICE);report.put("board",Build.BOARD);report.put("android",Build.VERSION.RELEASE);report.put("api",Build.VERSION.SDK_INT);report.put("abi",Build.CPU_ABI);report.put("keys",keys);}catch(Exception ignored){}refresh();ui.list.setOnItemClickListener(new AdapterView.OnItemClickListener(){public void onItemClick(AdapterView<?>p,View v,int pos,long id){if(pos==0)chooseVariant();else if(pos==1)testStorage();else if(pos==2)testAudio();else if(pos==3)testWifi();else if(pos==4)testBluetooth();else if(pos==5)testMicFm();else if(pos==6)testRoot();else if(pos==7)export();else if(pos==8)showReport();}});}
    private void refresh(){adapter.clear();adapter.add("Hardware type — "+variant);adapter.add("Test internal/removable storage write");adapter.add("Test speaker/headphone audio tone");adapter.add("Test Wi-Fi adapter and scan request");adapter.add("Test Bluetooth adapter");adapter.add("Probe microphone and FM capability");adapter.add("Test root/su availability");adapter.add("Export JSON validation report");adapter.add("View current report");adapter.notifyDataSetChanged();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){try{JSONObject k=new JSONObject();k.put("keyCode",e.getKeyCode());k.put("name",KeyEvent.keyCodeToString(e.getKeyCode()));k.put("scanCode",e.getScanCode());k.put("deviceId",e.getDeviceId());k.put("time",System.currentTimeMillis());keys.put(k);ui.subtitle.setText("Last key: "+KeyEvent.keyCodeToString(e.getKeyCode())+" scan="+e.getScanCode()+" • captured "+keys.length());}catch(Exception ignored){}if(e.getKeyCode()!=KeyEvent.KEYCODE_BACK)return true;}return super.dispatchKeyEvent(e);}
    private void chooseVariant(){new AlertDialog.Builder(this).setTitle("Verified hardware type").setItems(new String[]{"Type A","Type B","Unknown / not verified"},new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){variant=w==0?"A":w==1?"B":"Unverified";put("hardwareType",variant);refresh();}}).show();}
    private void testStorage(){JSONArray out=new JSONArray();for(File root:LibraryScanner.roots()){JSONObject item=new JSONObject();try{File test=new File(root,".rockbox-solar-write-test");FileOutputStream s=new FileOutputStream(test);s.write("ok".getBytes("UTF-8"));s.flush();s.getFD().sync();s.close();item.put("path",root.getAbsolutePath());item.put("writable",test.delete());item.put("freeBytes",root.getFreeSpace());}catch(Exception e){try{item.put("path",root.getAbsolutePath());item.put("writable",false);item.put("error",e.getMessage());}catch(Exception ignored){}}out.put(item);}put("storage",out);ui.subtitle.setText("Storage test complete");}
    private void testAudio(){try{ToneGenerator tone=new ToneGenerator(AudioManager.STREAM_MUSIC,80);tone.startTone(ToneGenerator.TONE_DTMF_5,1000);tone.release();put("audioToneRequested",true);ui.subtitle.setText("Played a 1-second tone — confirm whether you heard it");new AlertDialog.Builder(this).setTitle("Audio result").setMessage("Did you hear the test tone through the expected output?").setPositiveButton("Yes",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){put("audioToneHeard",true);}}).setNegativeButton("No",new android.content.DialogInterface.OnClickListener(){public void onClick(android.content.DialogInterface d,int w){put("audioToneHeard",false);}}).show();}catch(Exception e){put("audioError",e.getMessage());}}
    private void testWifi(){try{WifiManager w=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);JSONObject j=new JSONObject();j.put("present",w!=null);if(w!=null){j.put("enabled",w.isWifiEnabled());j.put("scanRequested",w.startScan());j.put("connection",String.valueOf(w.getConnectionInfo()));}put("wifi",j);ui.subtitle.setText("Wi-Fi test recorded");}catch(Exception e){put("wifiError",e.getMessage());}}
    private void testBluetooth(){try{BluetoothAdapter b=BluetoothAdapter.getDefaultAdapter();JSONObject j=new JSONObject();j.put("present",b!=null);if(b!=null){j.put("enabled",b.isEnabled());j.put("address",b.getAddress());j.put("name",b.getName());j.put("bondedCount",b.getBondedDevices().size());}put("bluetooth",j);ui.subtitle.setText("Bluetooth test recorded");}catch(Exception e){put("bluetoothError",e.getMessage());}}
    private void testMicFm(){PackageManager pm=getPackageManager();JSONObject j=new JSONObject();try{j.put("microphone",pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));j.put("fmFeature",pm.hasSystemFeature("android.hardware.fm.receiver"));JSONArray packages=new JSONArray();for(String p:new String[]{"com.mediatek.FMRadio","com.mediatek.fmradio","com.android.fmradio","com.caf.fmradio"})if(pm.getLaunchIntentForPackage(p)!=null)packages.put(p);j.put("fmPackages",packages);put("mediaHardware",j);ui.subtitle.setText("Microphone/FM probe complete");}catch(Exception e){put("mediaHardwareError",e.getMessage());}}
    private void testRoot(){try{Process p=Runtime.getRuntime().exec(new String[]{"su","-c","id"});BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line=r.readLine();int code=p.waitFor();JSONObject j=new JSONObject();j.put("exitCode",code);j.put("output",line);j.put("root",line!=null&&line.contains("uid=0"));put("root",j);ui.subtitle.setText("Root probe: "+line);}catch(Exception e){put("rootError",e.getMessage());ui.subtitle.setText("No usable su: "+e.getMessage());}}
    private void export(){try{File dir=new File(Environment.getExternalStorageDirectory(),"RockboxSolar/Diagnostics");dir.mkdirs();File file=new File(dir,"Y1-validation-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date())+".json");report.put("hardwareType",variant);report.put("keyCount",keys.length());report.put("heapMaxBytes",Runtime.getRuntime().maxMemory());report.put("heapFreeBytes",Runtime.getRuntime().freeMemory());FileWriter w=new FileWriter(file);w.write(report.toString(2));w.close();ui.subtitle.setText("Exported "+file.getAbsolutePath());Toast.makeText(this,"Validation report exported",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void showReport(){new AlertDialog.Builder(this).setTitle("Current validation report").setMessage(report.toString()).setPositiveButton("OK",null).show();}
    private void put(String key,Object value){try{report.put(key,value);}catch(Exception ignored){}}
}
