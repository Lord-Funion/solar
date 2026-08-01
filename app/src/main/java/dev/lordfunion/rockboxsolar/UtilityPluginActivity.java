package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

public final class UtilityPluginActivity extends Activity {
    private long stopwatchStart;
    private TextView output;
    @Override protected void onCreate(Bundle state){super.onCreate(state);String plugin=getIntent().getStringExtra("plugin");if(plugin==null)plugin="System information";
        if("Starfield visualizer".equals(plugin)){setContentView(new Starfield(this));return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);int p=AppUi.dp(this,12);root.setPadding(p,p,p,p);root.addView(AppUi.text(this,plugin,24));output=AppUi.text(this,"",20);root.addView(output,new LinearLayout.LayoutParams(-1,0,1));Button action=new Button(this);root.addView(action);setContentView(root);
        if("Stopwatch".equals(plugin)){action.setText("Start / reset");action.setOnClickListener(new View.OnClickListener(){public void onClick(View v){stopwatchStart=SystemClock.elapsedRealtime();tick();}});output.setText("0.0 s");}
        else if("Calculator".equals(plugin)){action.setText("Enter expression");action.setOnClickListener(new View.OnClickListener(){public void onClick(View v){AppUi.prompt(UtilityPluginActivity.this,"Calculator","Example: (12+3)*4","",InputType.TYPE_CLASS_TEXT,new AppUi.TextCallback(){public void onText(String value){try{output.setText(value+" = "+Expression.eval(value));}catch(Exception e){output.setText("Error: "+e.getMessage());}}});}});}
        else if("Dice roller".equals(plugin)){action.setText("Roll dice");action.setOnClickListener(new View.OnClickListener(){public void onClick(View v){Random r=new Random();output.setText("d6: "+(r.nextInt(6)+1)+"\nd20: "+(r.nextInt(20)+1)+"\n2d6: "+(r.nextInt(6)+r.nextInt(6)+2));}});}
        else {action.setText("Refresh");action.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showInfo();}});showInfo();}
    }
    private void tick(){if(stopwatchStart==0)return;output.setText(String.format(Locale.US,"%.1f seconds",(SystemClock.elapsedRealtime()-stopwatchStart)/1000d));output.postDelayed(new Runnable(){public void run(){tick();}},100);}
    private void showInfo(){StatFs fs=new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());long block=fs.getBlockSize(),avail=fs.getAvailableBlocks();output.setText("Android: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nBoard: "+Build.BOARD+"\nABI: "+Build.CPU_ABI+"\nFree storage: "+(block*avail/1024/1024)+" MiB\nHeap max: "+(Runtime.getRuntime().maxMemory()/1024/1024)+" MiB");}
    private static final class Starfield extends View{final Paint p=new Paint();final Random r=new Random();final float[]x=new float[120],y=new float[120],z=new float[120];Starfield(Context c){super(c);for(int i=0;i<x.length;i++)reset(i);}
        void reset(int i){x[i]=(r.nextFloat()-.5f)*2;y[i]=(r.nextFloat()-.5f)*2;z[i]=r.nextFloat()+.05f;}
        protected void onDraw(Canvas c){c.drawColor(Color.BLACK);p.setColor(Color.WHITE);float cx=getWidth()/2f,cy=getHeight()/2f;for(int i=0;i<x.length;i++){z[i]-=.012f;if(z[i]<=.02f)reset(i);float sx=cx+x[i]/z[i]*cx,sy=cy+y[i]/z[i]*cy,floatSize=Math.max(1,4*(1-z[i]));c.drawCircle(sx,sy,floatSize,p);}postInvalidateDelayed(30);}}
    private static final class Expression{static double eval(final String source){return new Object(){int pos=-1,ch;void next(){ch=(++pos<source.length())?source.charAt(pos):-1;}boolean eat(int c){while(ch==' ')next();if(ch==c){next();return true;}return false;}double parse(){next();double x=expr();if(pos<source.length())throw new RuntimeException("Unexpected: "+(char)ch);return x;}double expr(){double x=term();for(;;){if(eat('+'))x+=term();else if(eat('-'))x-=term();else return x;}}double term(){double x=factor();for(;;){if(eat('*'))x*=factor();else if(eat('/'))x/=factor();else return x;}}double factor(){if(eat('+'))return factor();if(eat('-'))return-factor();double x;int start=pos;if(eat('(')){x=expr();eat(')');}else{while((ch>='0'&&ch<='9')||ch=='.')next();x=Double.parseDouble(source.substring(start,pos));}return x;}}.parse();}}
}
