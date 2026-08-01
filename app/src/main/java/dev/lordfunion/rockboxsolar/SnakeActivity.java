package dev.lordfunion.rockboxsolar;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Random;

public final class SnakeActivity extends Activity {
    private SnakeView game;
    @Override protected void onCreate(Bundle state) { super.onCreate(state); requestWindowFeature(Window.FEATURE_NO_TITLE); getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); game = new SnakeView(); setContentView(game); }
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP) game.turn(0,-1);
            else if(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN)game.turn(0,1);
            else if(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT)game.turn(-1,0);
            else if(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)game.turn(1,0);
            else if(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)game.restart();
            else if(e.getKeyCode()==KeyEvent.KEYCODE_BACK)return super.dispatchKeyEvent(e);
            return true;
        } return super.dispatchKeyEvent(e);
    }
    private final class SnakeView extends View {
        final Paint paint=new Paint(); final ArrayList<int[]> snake=new ArrayList<int[]>(); final Random random=new Random(); int dx=1,dy=0,foodX=10,foodY=6,score=0; boolean dead;
        SnakeView(){super(SnakeActivity.this);setFocusable(true);restart();}
        void restart(){snake.clear();snake.add(new int[]{5,5});snake.add(new int[]{4,5});snake.add(new int[]{3,5});dx=1;dy=0;score=0;dead=false;placeFood();invalidate();}
        void turn(int x,int y){if(x!=-dx||y!=-dy){dx=x;dy=y;}}
        void placeFood(){foodX=random.nextInt(20);foodY=random.nextInt(12);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);int cell=Math.min(getWidth()/20,getHeight()/14);int ox=(getWidth()-cell*20)/2,oy=cell;
            paint.setTextSize(cell*.75f);paint.setColor(Color.WHITE);c.drawText("SNAKE  Score "+score+(dead?"  GAME OVER — press Select":""),8,cell*.8f,paint);
            paint.setColor(Color.rgb(159,232,112));for(int[]p:snake)c.drawRect(ox+p[0]*cell,oy+p[1]*cell,ox+(p[0]+1)*cell-1,oy+(p[1]+1)*cell-1,paint);
            paint.setColor(Color.YELLOW);c.drawCircle(ox+foodX*cell+cell/2f,oy+foodY*cell+cell/2f,cell*.38f,paint);
            if(!dead)postDelayed(new Runnable(){public void run(){step();}},140);
        }
        void step(){if(dead)return;int[]h=snake.get(0);int nx=h[0]+dx,ny=h[1]+dy;if(nx<0||nx>=20||ny<0||ny>=12){dead=true;invalidate();return;}for(int[]p:snake)if(p[0]==nx&&p[1]==ny){dead=true;invalidate();return;}snake.add(0,new int[]{nx,ny});if(nx==foodX&&ny==foodY){score++;placeFood();}else snake.remove(snake.size()-1);invalidate();}
    }
}
