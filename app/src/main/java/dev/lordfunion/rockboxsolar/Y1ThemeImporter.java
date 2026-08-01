package dev.lordfunion.rockboxsolar;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class Y1ThemeImporter {
    static final class Report {
        String themeName;
        File jsonFile;
        File background;
        final ArrayList<String> imported = new ArrayList<String>();
        final ArrayList<String> unsupported = new ArrayList<String>();
        String summary() { return "Created " + jsonFile.getName() + "\nImported: " + imported + "\nUnsupported: " + unsupported; }
    }

    Report importArchive(File source, File themeDir) throws Exception {
        if (!source.isFile()) throw new IllegalArgumentException("Theme archive not found");
        if (!themeDir.exists() && !themeDir.mkdirs()) throw new IllegalStateException("Cannot create themes directory");
        String base = strip(source.getName());
        File work = new File(themeDir, "imported-" + safe(base));
        delete(work); work.mkdirs();
        if (source.getName().toLowerCase(Locale.US).endsWith(".zip") || source.getName().toLowerCase(Locale.US).endsWith(".apk")) unzip(source, work);
        else copy(source, new File(work, source.getName()));
        return translate(base, work, themeDir);
    }

    private Report translate(String base, File work, File themeDir) throws Exception {
        Report report = new Report(); report.themeName = base;
        ArrayList<File> all = new ArrayList<File>(); collect(work, all);
        File preferredImage = null;
        int bg = Color.rgb(8,11,13), fg = Color.WHITE, accent = Color.rgb(159,232,112), selected = Color.rgb(36,58,44), muted = Color.GRAY;
        for (File file : all) {
            String lower = file.getName().toLowerCase(Locale.US);
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp")) {
                if (preferredImage == null || lower.contains("background") || lower.contains("wallpaper") || lower.contains("main")) preferredImage = file;
                report.imported.add("image:" + file.getName());
            } else if (lower.endsWith(".properties") || lower.endsWith(".ini") || lower.endsWith(".cfg")) {
                Properties props = new Properties(); FileInputStream in = new FileInputStream(file); props.load(in); in.close();
                bg = color(props, bg, "background", "bg", "background_color"); fg = color(props, fg, "foreground", "text", "text_color");
                accent = color(props, accent, "accent", "highlight", "selection_color"); report.imported.add("settings:" + file.getName());
            } else if (lower.endsWith(".xml")) {
                String text = read(file); bg = findHex(text, "background", bg); fg = findHex(text, "text", fg); accent = findHex(text, "accent", accent);
                report.imported.add("xml-colors:" + file.getName());
            } else if (lower.endsWith(".ttf") || lower.endsWith(".otf")) report.unsupported.add("font:" + file.getName());
            else if (lower.endsWith(".gif") || lower.endsWith(".webp")) report.unsupported.add("animated/unsupported image:" + file.getName());
        }
        if (preferredImage != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(preferredImage.getAbsolutePath());
            if (bitmap != null) {
                int average = average(bitmap); bitmap.recycle(); bg = darken(average, .35f);
                File copied = new File(themeDir, safe(base) + "-background" + extension(preferredImage.getName())); copy(preferredImage, copied); report.background = copied;
                accent = brighten(average, 1.6f); fg = contrast(bg); selected = blend(bg, accent, .28f); muted = blend(fg, bg, .55f);
            }
        }
        JSONObject json = new JSONObject(); json.put("name", "Y1: " + base); json.put("background", hex(bg)); json.put("foreground", hex(fg));
        json.put("accent", hex(accent)); json.put("selected", hex(selected)); json.put("muted", hex(muted)); json.put("fontScale", 1.0);
        if (report.background != null) json.put("backgroundImage", report.background.getAbsolutePath());
        report.jsonFile = new File(themeDir, "y1-" + safe(base) + ".json"); FileWriter writer = new FileWriter(report.jsonFile); writer.write(json.toString(2)); writer.close();
        if (report.unsupported.isEmpty()) report.unsupported.add("none detected");
        return report;
    }

    private static int color(Properties p, int fallback, String... keys) { for (String key:keys) { String v=p.getProperty(key); if(v!=null) try{return Color.parseColor(v.trim());}catch(Exception ignored){} } return fallback; }
    private static int findHex(String text,String key,int fallback){int i=text.toLowerCase(Locale.US).indexOf(key.toLowerCase(Locale.US));if(i<0)return fallback;int hash=text.indexOf('#',i);if(hash<0||hash+7>text.length())return fallback;try{return Color.parseColor(text.substring(hash,hash+7));}catch(Exception e){return fallback;}}
    private static String read(File f)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l).append('\n');r.close();return b.toString();}
    private static void unzip(File zip,File dir)throws Exception{ZipInputStream in=new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));ZipEntry e;byte[]buf=new byte[32768];String root=dir.getCanonicalPath()+File.separator;while((e=in.getNextEntry())!=null){File out=new File(dir,e.getName());if(!out.getCanonicalPath().startsWith(root))throw new SecurityException("Unsafe path in archive");if(e.isDirectory())out.mkdirs();else{File parent=out.getParentFile();if(parent!=null)parent.mkdirs();FileOutputStream s=new FileOutputStream(out);int n;while((n=in.read(buf))>=0)s.write(buf,0,n);s.close();}in.closeEntry();}in.close();}
    private static void collect(File f,ArrayList<File>out){if(f.isFile()){out.add(f);return;}File[]a=f.listFiles();if(a!=null)for(File x:a)collect(x,out);}
    private static void copy(File a,File b)throws Exception{FileInputStream in=new FileInputStream(a);FileOutputStream out=new FileOutputStream(b);byte[]buf=new byte[32768];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);out.close();in.close();}
    private static void delete(File f){if(f.isDirectory()){File[]a=f.listFiles();if(a!=null)for(File x:a)delete(x);}f.delete();}
    private static int average(Bitmap b){long r=0,g=0,bl=0,c=0;int sx=Math.max(1,b.getWidth()/40),sy=Math.max(1,b.getHeight()/30);for(int y=0;y<b.getHeight();y+=sy)for(int x=0;x<b.getWidth();x+=sx){int p=b.getPixel(x,y);r+=Color.red(p);g+=Color.green(p);bl+=Color.blue(p);c++;}return Color.rgb((int)(r/c),(int)(g/c),(int)(bl/c));}
    private static int darken(int c,float f){return Color.rgb((int)(Color.red(c)*f),(int)(Color.green(c)*f),(int)(Color.blue(c)*f));}
    private static int brighten(int c,float f){return Color.rgb(Math.min(255,(int)(Color.red(c)*f)),Math.min(255,(int)(Color.green(c)*f)),Math.min(255,(int)(Color.blue(c)*f)));}
    private static int contrast(int c){double lum=.299*Color.red(c)+.587*Color.green(c)+.114*Color.blue(c);return lum>140?Color.BLACK:Color.WHITE;}
    private static int blend(int a,int b,float t){return Color.rgb((int)(Color.red(a)*(1-t)+Color.red(b)*t),(int)(Color.green(a)*(1-t)+Color.green(b)*t),(int)(Color.blue(a)*(1-t)+Color.blue(b)*t));}
    private static String hex(int c){return String.format(Locale.US,"#%06X",0xFFFFFF&c);}
    private static String safe(String s){return s.replaceAll("[^A-Za-z0-9._-]","_");}
    private static String strip(String s){int d=s.lastIndexOf('.');return d>0?s.substring(0,d):s;}
    private static String extension(String s){int d=s.lastIndexOf('.');return d>=0?s.substring(d):".png";}
}
