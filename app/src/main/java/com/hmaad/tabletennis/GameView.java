package com.hmaad.tabletennis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.Choreographer;
import android.view.View;

public class GameView extends View implements Choreographer.FrameCallback, GameLogic.Sound {
    public final GameLogic g = new GameLogic();
    public volatile long lastPhonePacket = 0;
    public volatile String tvIp = "finding...";
    public volatile String netError = "";

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private boolean running = false;
    private long lastNanos = 0;
    private ToneGenerator tone;
    private float W, H, cx, horizon, nearY, nearHalf, hs;

    public GameView(Context c) {
        super(c);
        g.sound = this;
    }

    public void queueHit(float power, float aim) { g.queueHit(power, aim); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        try { tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70); } catch (Exception e) { tone = null; }
        running = true; lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        if (tone != null) { try { tone.release(); } catch (Exception ignored) { } tone = null; }
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long nanos) {
        if (!running) return;
        float dt = lastNanos == 0 ? 0.016f : (nanos - lastNanos) / 1000000000f;
        lastNanos = nanos;
        if (dt > 0.05f) dt = 0.05f;
        g.update(dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void beep(int t, int ms) {
        if (tone != null) { try { tone.startTone(t, ms); } catch (Exception ignored) { } }
    }
    @Override public void hit() { beep(ToneGenerator.TONE_PROP_BEEP, 60); }
    @Override public void win() { beep(ToneGenerator.TONE_PROP_ACK, 200); }
    @Override public void lose() { beep(ToneGenerator.TONE_PROP_NACK, 200); }

    private float sc(float z) { return 1f / (1f + 1.2f * Math.max(z, -0.6f)); }
    private float sx(float x, float z) { return cx + x * nearHalf * sc(z); }
    private float sy(float z) { return horizon + (nearY - horizon) * sc(z); }

    private void drawBall(Canvas c, float x, float y, float r) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF9800);
        c.drawCircle(x, y, r, p);
        p.setColor(0x66FFFFFF);
        c.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.35f, p);
    }

    private void drawPaddle(Canvas c, float x, float z, float h, int color, float angle) {
        float s = sc(z);
        float px = sx(x, z), py = sy(z) - h * hs * s;
        float r = H * 0.05f * s;
        c.save();
        c.rotate(angle, px, py);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF5D4037);
        c.drawRect(px - r * 0.14f, py, px + r * 0.14f, py + r * 1.6f, p);
        p.setColor(color);
        rect.set(px - r, py - r * 1.1f, px + r, py + r * 1.1f);
        c.drawOval(rect, p);
        c.restore();
    }

    private void drawNet(Canvas c) {
        float z = 0.5f, s = sc(z);
        float l = sx(-1.06f, z), r = sx(1.06f, z), base = sy(z), top = base - GameLogic.NET_H * hs * s;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66FFFFFF);
        c.drawRect(l, top, r, base, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(Math.max(2f, H * 0.006f));
        c.drawLine(l, top, r, top, p);
        c.drawLine(l, top, l, base, p);
        c.drawLine(r, top, r, base, p);
    }

    @Override protected void onDraw(Canvas c) {
        W = getWidth(); H = getHeight();
        cx = W / 2f; horizon = H * 0.10f; nearY = H * 0.88f; nearHalf = W * 0.38f; hs = H * 0.8f;
        c.drawColor(0xFF0E1621);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF152233);
        c.drawRect(0, horizon, W, H, p);

        path.reset();
        path.moveTo(sx(-1, 0), sy(0)); path.lineTo(sx(1, 0), sy(0));
        path.lineTo(sx(1, 1), sy(1)); path.lineTo(sx(-1, 1), sy(1)); path.close();
        p.setColor(0xFF1565C0);
        c.drawPath(path, p);
        p.setColor(0xFF0D3B73);
        c.drawRect(sx(-1, 0), sy(0), sx(1, 0), sy(0) + H * 0.03f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.WHITE);
        p.setStrokeWidth(Math.max(2f, H * 0.005f));
        c.drawPath(path, p);
        p.setStrokeWidth(Math.max(1f, H * 0.0025f));
        p.setColor(0x88FFFFFF);
        c.drawLine(sx(0, 0), sy(0), sx(0, 1), sy(1), p);

        drawPaddle(c, g.oppX, 1.02f, 0.14f, 0xFFE53935, 0f);

        float s = sc(g.bz);
        float bsx = sx(g.bx, g.bz), shy = sy(g.bz);
        float r = Math.max(3f, H * 0.022f * s);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        rect.set(bsx - r, shy - r * 0.35f, bsx + r, shy + r * 0.35f);
        c.drawOval(rect, p);
        float bsy = shy - Math.max(0f, g.by) * hs * s;
        if (g.bz > 0.5f) drawBall(c, bsx, bsy, r);
        drawNet(c);
        if (g.bz <= 0.5f) drawBall(c, bsx, bsy, r);

        float st = Math.max(0f, g.swingT) / 0.25f;
        drawPaddle(c, g.playerX + g.swingDir * 0.25f * st, -0.1f, 0.12f, 0xFF43A047, g.swingDir * 55f * st);

        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        p.setTextSize(H * 0.07f);
        c.drawText("YOU " + g.playerScore + "  :  " + g.aiScore + " AI", cx, H * 0.085f, p);
        p.setColor(0xFFFFEB3B);
        p.setTextSize(H * 0.055f);
        c.drawText(g.message, cx, H * 0.2f, p);

        boolean connected = System.currentTimeMillis() - lastPhonePacket < 3000;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(H * 0.03f);
        p.setColor(0xFFB0BEC5);
        c.drawText("TV IP: " + tvIp, W * 0.02f, H * 0.04f, p);
        p.setColor(connected ? 0xFF66BB6A : 0xFFEF5350);
        c.drawText(connected ? "Phone: connected" : "Phone: waiting (open Racket mode on phone)", W * 0.02f, H * 0.075f, p);
        if (netError.length() > 0) {
            p.setColor(0xFFEF5350);
            c.drawText(netError, W * 0.02f, H * 0.11f, p);
        }
    }
}
