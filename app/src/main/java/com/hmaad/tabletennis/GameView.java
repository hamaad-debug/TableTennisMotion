package com.hmaad.tabletennis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.BitmapShader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.Choreographer;
import android.view.View;

/** The TV court: perspective table, moving players, ball, net, score and connection HUD. */
public class GameView extends View implements Choreographer.FrameCallback, GameLogic.Sound {
    public final GameLogic g = new GameLogic();
    public volatile long lastPhonePacket = 0;
    public volatile String tvIp = "finding...";
    public volatile String netError = "";

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Bitmap arena;
    private final Bitmap tableSurface;
    private final BitmapShader tableShader;
    private final Matrix textureMatrix = new Matrix();
    private final float[] trailX = new float[18], trailY = new float[18], trailZ = new float[18];
    private boolean running = false;
    private long lastNanos = 0;
    private ToneGenerator tone;
    private float W, H, cx, horizon, nearY, nearHalf, hs;
    private int frame = 0;
    private int trailWrite = 0;
    private int stadiumIndex = 0, matchupIndex = 0;
    private static final String[] STADIUMS = {"TOKYO  ·  SKYDOME", "PARIS  ·  GRAND ARENA", "RIO  ·  SAMBA HALL", "LONDON  ·  CHAMPIONS COURT"};
    private static final String[][] MATCHUPS = {
            {"INDIA", "JAPAN"}, {"BRAZIL", "FRANCE"}, {"CHINA", "USA"}, {"SOUTH KOREA", "GERMANY"},
            {"SWEDEN", "AUSTRALIA"}, {"EGYPT", "SPAIN"}, {"CANADA", "ITALY"}, {"MEXICO", "SINGAPORE"}
    };

    public GameView(Context c) {
        super(c);
        g.sound = this;
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.RGB_565;
        opt.inDither = true;
        arena = BitmapFactory.decodeResource(c.getResources(), R.drawable.arena_landscape, opt);
        BitmapFactory.Options surfaceOpt = new BitmapFactory.Options();
        surfaceOpt.inPreferredConfig = Bitmap.Config.RGB_565;
        surfaceOpt.inDither = true;
        tableSurface = BitmapFactory.decodeResource(c.getResources(), R.drawable.court_surface, surfaceOpt);
        tableShader = tableSurface == null ? null : new BitmapShader(tableSurface, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        setFocusable(true);
    }

    public void queueHit(float power, float aim) { g.queueHit(power, aim); }
    public void nextStadium() { stadiumIndex = (stadiumIndex + 1) % STADIUMS.length; invalidate(); }
    public void nextMatchup() {
        matchupIndex = (matchupIndex + 1) % MATCHUPS.length;
        g.resetMatch();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 65); } catch (Exception e) { tone = null; }
        running = true; lastNanos = 0; Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false; Choreographer.getInstance().removeFrameCallback(this);
        if (tone != null) { try { tone.release(); } catch (Exception ignored) { } tone = null; }
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long nanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 0.016f : (nanos-lastNanos)/1000000000f;
        lastNanos=nanos; if (dt>0.05f) dt=0.05f;
        g.update(dt); frame++;
        if (g.state == GameLogic.RALLY && frame % 2 == 0) {
            trailX[trailWrite] = g.bx; trailY[trailWrite] = g.by; trailZ[trailWrite] = g.bz;
            trailWrite = (trailWrite + 1) % trailX.length;
        }
        invalidate(); Choreographer.getInstance().postFrameCallback(this);
    }

    private void beep(int type, int duration) {
        if (tone != null) { try { tone.startTone(type,duration); } catch (Exception ignored) { } }
    }
    @Override public void hit() { beep(ToneGenerator.TONE_PROP_BEEP,55); }
    @Override public void win() { beep(ToneGenerator.TONE_PROP_ACK,190); }
    @Override public void lose() { beep(ToneGenerator.TONE_PROP_NACK,190); }

    private float sc(float z) { return 1f/(1f+1.2f*Math.max(z,-0.6f)); }
    private float sx(float x,float z) { return cx+x*nearHalf*sc(z); }
    private float sy(float z) { return horizon+(nearY-horizon)*sc(z); }
    private float dp(float n) { return n*getResources().getDisplayMetrics().density; }

    private void fill(Canvas c,int color) { p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color); }
    private void text(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold) {
        p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color); p.setTextAlign(align); p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL)); c.drawText(s,x,y,p);
    }

    private void drawBackground(Canvas c) {
        if (arena != null) {
            rect.set(0,0,W,H); p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(Color.WHITE);
            c.drawBitmap(arena,null,rect,p);
        } else c.drawColor(0xFF071522);
        int[] arenaWash = {0x122250FF, 0x1BDF365D, 0x1BCC8E3D, 0x1532B889};
        p.setColor(arenaWash[stadiumIndex]); p.setStyle(Paint.Style.FILL); p.setShader(null); c.drawRect(0,0,W,H,p);
        if (stadiumIndex != 0) {
            p.setShader(new LinearGradient(W*.18f,0,W*.48f,H*.72f,0x3345DDF5,0x0045DDF5,Shader.TileMode.CLAMP));
            path.reset(); path.moveTo(W*.12f,0); path.lineTo(W*.31f,0); path.lineTo(W*.55f,H*.76f); path.close(); c.drawPath(path,p);
            p.setShader(new LinearGradient(W*.82f,0,W*.58f,H*.72f,0x33FFCF7A,0x00FFCF7A,Shader.TileMode.CLAMP));
            path.reset(); path.moveTo(W*.76f,0); path.lineTo(W*.95f,0); path.lineTo(W*.62f,H*.76f); path.close(); c.drawPath(path,p);
        }
        p.setShader(new LinearGradient(0,0,0,H,0x70040C16,0x36051019,Shader.TileMode.CLAMP));
        c.drawRect(0,0,W,H,p); p.setShader(null);
    }

    private void drawTable(Canvas c) {
        path.reset(); path.moveTo(sx(-1.08f,0),sy(0)); path.lineTo(sx(1.08f,0),sy(0));
        path.lineTo(sx(1.0f,1),sy(1)); path.lineTo(sx(-1.0f,1),sy(1)); path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,sy(1),0,sy(0),0xFF1779A2,0xFF0C426C,Shader.TileMode.CLAMP));
        c.drawPath(path,p); p.setShader(null);
        if (tableShader != null && tableSurface != null) {
            textureMatrix.setScale(W/tableSurface.getWidth(), H/tableSurface.getHeight());
            tableShader.setLocalMatrix(textureMatrix);
            p.setShader(tableShader); p.setAlpha(45); c.drawPath(path,p); p.setAlpha(255); p.setShader(null);
        }
        // The apron and front edge give the court a solid, three-dimensional silhouette.
        path.reset(); path.moveTo(sx(-1.08f,0),sy(0)); path.lineTo(sx(1.08f,0),sy(0));
        path.lineTo(sx(1.08f,0),sy(0)+H*.045f); path.lineTo(sx(-1.08f,0),sy(0)+H*.045f); path.close();
        fill(c,0xFF082942); c.drawPath(path,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(dp(2),H*.004f)); p.setColor(0xFFE5F8FF); c.drawPath(path,p);
        path.reset(); path.moveTo(sx(-1.08f,0),sy(0)); path.lineTo(sx(1.08f,0),sy(0));
        path.lineTo(sx(1f,1),sy(1)); path.lineTo(sx(-1f,1),sy(1)); path.close();
        p.setStrokeWidth(Math.max(dp(2),H*.004f)); p.setColor(0xFFF0FBFF); c.drawPath(path,p);
        p.setStrokeWidth(Math.max(dp(1),H*.002f)); p.setColor(0xAAE2F5FF);
        c.drawLine(sx(0,0),sy(0),sx(0,1),sy(1),p);
        c.drawLine(sx(-1f,0.5f),sy(0.5f),sx(1f,0.5f),sy(0.5f),p);
        // Floor under the table catches a soft blue reflection.
        p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0,sy(0),0,H,0x3B45D7F2,0x00060E1A,Shader.TileMode.CLAMP));
        c.drawRect(0,sy(0),W,H,p); p.setShader(null);
    }

    private void drawNet(Canvas c) {
        float z=.5f,s=sc(z),l=sx(-1.03f,z),r=sx(1.03f,z),base=sy(z),top=base-GameLogic.NET_H*hs*s;
        p.setStyle(Paint.Style.FILL); p.setColor(0x8831D5E9); c.drawRect(l,top,r,base,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1,dp(1))); p.setColor(0xBBE3FAFF);
        c.drawLine(l,top,r,top,p); c.drawLine(l,top,l,base,p); c.drawLine(r,top,r,base,p);
        p.setColor(0x5279E0F2);
        for(int i=1;i<17;i++) { float x=l+(r-l)*i/17f; c.drawLine(x,top,x,base,p); }
        for(int i=1;i<3;i++) { float y=top+(base-top)*i/3f; c.drawLine(l,y,r,y,p); }
    }

    private void drawPlayer(Canvas c,float x,float z,boolean far,int racketAim,float anim) {
        float scale=sc(z), center=sx(x,z), base=sy(z)+H*.018f*scale;
        float u=Math.max(dp(3),H*.034f*scale);
        float lean=(float)Math.sin(anim*Math.PI)*.24f;
        int jersey=far?0xFFF04D59:0xFF20C6D8;
        int shorts=far?0xFF3A2234:0xFF123B57;
        // Player shadow.
        fill(c,0x77030B14); rect.set(center-u*1.9f,base-u*.10f,center+u*1.9f,base+u*.32f); c.drawOval(rect,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(u*.30f);
        p.setColor(0xFF0A1722);
        c.drawLine(center-u*.5f,base-u*1.35f,center-u*.72f-lean*u,base-u*.12f,p);
        c.drawLine(center+u*.5f,base-u*1.35f,center+u*.68f-lean*u,base-u*.12f,p);
        p.setColor(shorts); c.drawLine(center-u*.5f,base-u*1.65f,center-u*.72f-lean*u,base-u*.12f,p);
        c.drawLine(center+u*.5f,base-u*1.65f,center+u*.68f-lean*u,base-u*.12f,p);
        // Torso and collar.
        fill(c,jersey); rect.set(center-u*.73f+lean*u,base-u*3.48f,center+u*.73f+lean*u,base-u*1.32f);
        c.drawRoundRect(rect,u*.48f,u*.48f,p);
        fill(c,far?0xFFFFCBD0:0xFFB9FAFF);
        path.reset(); path.moveTo(center-u*.24f+lean*u,base-u*3.46f); path.lineTo(center+lean*u,base-u*3.14f);
        path.lineTo(center+u*.24f+lean*u,base-u*3.46f); path.close(); c.drawPath(path,p);
        // Head and hair band.
        fill(c,0xFFE8AD88); c.drawCircle(center+lean*u,base-u*4.07f,u*.54f,p);
        fill(c,far?0xFF402D3D:0xFF173A4A); rect.set(center-u*.56f+lean*u,base-u*4.45f,center+u*.56f+lean*u,base-u*4.12f);
        c.drawRoundRect(rect,u*.15f,u*.15f,p);
        // Left arm rests at the side; racket arm follows the incoming phone aim and hit timing.
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(u*.27f);
        p.setColor(0xFFE8AD88);
        c.drawLine(center-u*.61f+lean*u,base-u*3.20f,center-u*1.03f,base-u*2.12f,p);
        float direction=(racketAim>=0?1f:-1f), reach=anim>0?0.45f+0.9f*anim:0.28f;
        float wristX=center+u*(.65f+direction*reach), wristY=base-u*(2.35f+anim*.46f);
        c.drawLine(center+u*.60f+lean*u,base-u*3.20f,wristX,wristY,p);
        float angle=direction*(anim>0?52f+anim*44f:12f);
        c.save(); c.rotate(angle,wristX,wristY);
        p.setStrokeWidth(u*.16f); p.setColor(0xFFDFECF4); c.drawLine(wristX,wristY,wristX,wristY-u*1.18f,p);
        fill(c,far?0xFFF33E4E:0xFFFC5265); rect.set(wristX-u*.62f,wristY-u*2.05f,wristX+u*.62f,wristY-u*.78f); c.drawOval(rect,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(u*.11f); p.setColor(0xFFFFCFD5); c.drawOval(rect,p);
        c.restore();
        // Motion streaks make the relationship between the phone swing and TV racket obvious.
        if(anim>0.12f && !far) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(u*.10f); p.setColor(0x8866E9FA);
            c.drawLine(wristX-direction*u*1.3f,wristY-u*.8f,wristX-direction*u*2.8f,wristY-u*1.5f,p);
            c.drawLine(wristX-direction*u*1.1f,wristY-u*.35f,wristX-direction*u*2.4f,wristY-u*.72f,p);
        }
    }

    private void drawBallTrail(Canvas c) {
        if (g.state != GameLogic.RALLY || Math.abs(g.lastSideSpin) + Math.abs(g.lastTopSpin) < 0.12f) return;
        int tint = g.lastSideSpin > 0.12f ? 0xFF48E0F5 : (g.lastSideSpin < -0.12f ? 0xFFFF6480 : 0xFFFFC45B);
        float spin = Math.min(1f, Math.abs(g.lastSideSpin) + Math.abs(g.lastTopSpin));
        for (int i = 0; i < trailX.length; i++) {
            int index = (trailWrite + i) % trailX.length;
            float scale = sc(trailZ[index]);
            float x = sx(trailX[index], trailZ[index]);
            float y = sy(trailZ[index]) - Math.max(0f, trailY[index]) * hs * scale;
            float r = Math.max(dp(1.5f), H * .010f * scale) * (0.35f + 0.65f * i / trailX.length);
            p.setStyle(Paint.Style.FILL); p.setShader(null);
            int alpha = (int)(Math.min(125f, 18f + 100f * i / trailX.length) * spin);
            p.setColor((alpha << 24) | (tint & 0x00FFFFFF));
            c.drawCircle(x, y, r, p);
        }
    }

    private void drawBall(Canvas c,float x,float y,float radius) {
        fill(c,0x66000000); rect.set(x-radius*.8f,y+radius*.35f,x+radius*.8f,y+radius*.72f); c.drawOval(rect,p);
        fill(c,0xFFFFB52E); c.drawCircle(x,y,radius,p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1,dp(1.2f))); p.setColor(0xFFFFE8B4);
        c.drawArc(x-radius*.7f,y-radius*.7f,x+radius*.7f,y+radius*.7f,220,115,false,p);
        fill(c,0xCCFFFFFF); c.drawCircle(x-radius*.27f,y-radius*.3f,radius*.2f,p);
    }

    private void drawScoreboard(Canvas c) {
        float width=Math.min(W*.47f,dp(500)), height=Math.max(dp(68),H*.105f), top=H*.035f;
        rect.set(cx-width*.5f,top,cx+width*.5f,top+height);
        p.setStyle(Paint.Style.FILL); p.setShader(new LinearGradient(0,top,0,top+height,0xED071725,0xD20A2030,Shader.TileMode.CLAMP)); c.drawRoundRect(rect,height*.22f,height*.22f,p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(0x7775D4E6); c.drawRoundRect(rect,height*.22f,height*.22f,p);
        float scoreY=top+height*.69f;
        text(c,MATCHUPS[matchupIndex][0],cx-width*.29f,top+height*.32f,height*.145f,0xFF71E7F3,Paint.Align.CENTER,true);
        text(c,"WORLD TOUR",cx,top+height*.32f,height*.12f,0xFF98AEBE,Paint.Align.CENTER,true);
        text(c,MATCHUPS[matchupIndex][1],cx+width*.29f,top+height*.32f,height*.145f,0xFFFF8790,Paint.Align.CENTER,true);
        text(c,String.valueOf(g.playerScore),cx-width*.29f,scoreY,height*.42f,Color.WHITE,Paint.Align.CENTER,true);
        text(c,":",cx,scoreY-height*.01f,height*.28f,0xFFB5C8D5,Paint.Align.CENTER,false);
        text(c,String.valueOf(g.aiScore),cx+width*.29f,scoreY,height*.42f,Color.WHITE,Paint.Align.CENTER,true);
    }

    private void drawConnection(Canvas c,boolean connected) {
        float left=W*.035f, top=H*.043f;
        p.setStyle(Paint.Style.FILL); p.setColor(0xB8081825); rect.set(left-dp(10),top-dp(17),W*.30f,top+H*.066f); c.drawRoundRect(rect,dp(13),dp(13),p);
        fill(c,connected?0xFF4DE1A4:0xFFFFC15C); c.drawCircle(left,top,dp(4.5f),p);
        text(c,connected?"PHONE CONNECTED":"WAITING FOR PHONE",left+dp(12),top+dp(4),Math.max(dp(10),H*.014f),connected?0xFF8FF5C6:0xFFFFD685,Paint.Align.LEFT,true);
        text(c,"TV IP  " + tvIp,left,top+H*.036f,Math.max(dp(9),H*.012f),0xFFD6E3EB,Paint.Align.LEFT,false);
    }

    private void drawEventBadge(Canvas c) {
        text(c,STADIUMS[stadiumIndex],W-dp(26),H*.055f,Math.max(dp(11),H*.019f),0xFFE7F8FF,Paint.Align.RIGHT,true);
        text(c,"WORLD TOUR     UP: ARENA     DOWN: COUNTRY MATCH",W-dp(26),H*.082f,Math.max(dp(8),H*.012f),0xFFB7D4E2,Paint.Align.RIGHT,false);
    }

    @Override protected void onDraw(Canvas c) {
        W=getWidth(); H=getHeight(); cx=W*.5f; horizon=H*.12f; nearY=H*.88f; nearHalf=W*.36f; hs=H*.78f;
        drawBackground(c);
        boolean connected=System.currentTimeMillis()-lastPhonePacket<3000;
        // The far character is drawn behind the table; the near player is foregrounded below.
        drawPlayer(c,g.oppX,1.02f,true,1,g.lastHitter==GameLogic.AI?Math.max(0,g.swingT/.25f):0);
        drawTable(c);

        float ballScale=sc(g.bz), ballX=sx(g.bx,g.bz), shadowY=sy(g.bz);
        float ballRadius=Math.max(dp(4),H*.018f*ballScale);
        drawBallTrail(c);
        if(g.bz>.5f) drawBall(c,ballX,shadowY-Math.max(0,g.by)*hs*ballScale,ballRadius);
        drawNet(c);
        if(g.bz<=.5f) drawBall(c,ballX,shadowY-Math.max(0,g.by)*hs*ballScale,ballRadius);

        float animation=Math.max(0,Math.min(1,g.swingT/.25f));
        drawPlayer(c,g.playerX+g.swingDir*.20f*animation,-.10f,false,(int)g.swingDir,animation);
        drawScoreboard(c);
        drawConnection(c,connected);
        drawEventBadge(c);

        if(!g.message.isEmpty()) {
            float y=H*.255f, pad=dp(24);
            p.setStyle(Paint.Style.FILL); p.setColor(0xC8081724); rect.set(cx-W*.22f,y-H*.053f,cx+W*.22f,y+H*.026f); c.drawRoundRect(rect,dp(18),dp(18),p);
            text(c,g.message,cx,y+H*.003f,Math.max(dp(16),H*.029f),0xFFFFE390,Paint.Align.CENTER,true);
        }
        if(!netError.isEmpty()) text(c,netError,W*.5f,H*.955f,Math.max(dp(10),H*.016f),0xFFFF9B91,Paint.Align.CENTER,true);
        else text(c,"SWING YOUR PHONE  ·  FIRST TO 11 WINS BY 2",W*.5f,H*.975f,Math.max(dp(9),H*.013f),0xBFD8E7F0,Paint.Align.CENTER,true);
    }
}

