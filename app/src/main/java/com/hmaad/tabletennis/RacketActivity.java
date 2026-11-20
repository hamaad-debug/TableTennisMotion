package com.hmaad.tabletennis;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Motion-controller screen shown when this device is selected as the racket. */
public class RacketActivity extends Activity implements SensorEventListener {
    private SensorManager sm;
    private Sensor acc, gyro;
    private Vibrator vib;
    private final float[] grav = new float[3];
    private boolean gravInit = false;
    private volatile String tvIp = null;
    private DatagramSocket sendSocket, discSocket;
    private Thread discoveryThread;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private TextView status, live, sensLabel;
    private EditText ipBox;
    private MotionGauge gauge;
    private float threshold = 12f;
    private boolean flipAim = false;
    private int phase = 0;
    private long phaseStart = 0, cooldownUntil = 0, lastUi = 0;
    private float peak, peakX;
    private float spinSideRadians, spinTopRadians;
    private long gyroTimestamp;
    private WifiManager.MulticastLock mlock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pinger = new Runnable() {
        @Override public void run() { send("P"); handler.postDelayed(this, 1000); }
    };

    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return v;
    }

    private GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{fill, fill});
        d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }

    private LinearLayout section() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(18), dp(16), dp(18), dp(16)); v.setBackground(panel(0xE90A1B2B, 0x553F8298, 18));
        return v;
    }

    private void gap(LinearLayout parent, int height) {
        View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(5, 13, 24));
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 24));
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acc = sm != null ? sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
        gyro = sm != null ? sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        final SharedPreferences sp = getSharedPreferences("tt", MODE_PRIVATE);
        threshold = sp.getFloat("threshold", 12f);
        flipAim = sp.getBoolean("flipAim", false);

        FrameLayout frame = new FrameLayout(this);
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.arena_portrait); art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(art, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this); shade.setBackgroundColor(0xC107111E);
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(28));
        scroll.addView(root); frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        TextView brand = label("RALLY     /     RACKET MODE", 12, 0xFF54DDF4, true);
        brand.setLetterSpacing(0.12f); root.addView(brand);
        TextView title = label("Swing to play.", 30, Color.WHITE, true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2); titleLp.topMargin = dp(10);
        root.addView(title, titleLp);
        TextView intro = label("Your phone becomes a motion controller for the court on your TV.", 14, 0xFFB7C9D6, false);
        intro.setLineSpacing(dp(3), 1.05f);
        LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(-1, -2); introLp.topMargin = dp(5);
        root.addView(intro, introLp);

        LinearLayout connection = section();
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, -2); sectionLp.topMargin = dp(20);
        root.addView(connection, sectionLp);
        connection.addView(label("TV CONNECTION", 11, 0xFF83A4B8, true));
        status = label("Searching for your TV…", 16, 0xFFFFD36E, true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = dp(8);
        connection.addView(status, statusLp);
        TextView hint = label("Keep both devices on the same Wi-Fi. The TV will appear here automatically.", 12, 0xFFB5C8D4, false);
        hint.setLineSpacing(dp(2), 1.05f);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2); hintLp.topMargin = dp(5);
        connection.addView(hint, hintLp);
        ipBox = new EditText(this);
        ipBox.setHint("TV address  ·  e.g. 192.168.1.5"); ipBox.setTextColor(Color.WHITE);
        ipBox.setHintTextColor(0xFF718A9A); ipBox.setSingleLine(true);
        ipBox.setTextSize(14); ipBox.setPadding(dp(12), dp(7), dp(12), dp(7));
        ipBox.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        ipBox.setBackground(panel(0xFF0C1B2A, 0xFF29465B, 10));
        String saved = sp.getString("ip", ""); ipBox.setText(saved); if (!saved.isEmpty()) tvIp = saved;
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(-1, dp(50)); ipLp.topMargin = dp(13);
        connection.addView(ipBox, ipLp);
        Button connect = button("CONNECT TO TV", 0xFF273F53, Color.WHITE);
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(-1, dp(48)); connectLp.topMargin = dp(9);
        connection.addView(connect, connectLp);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String ip = ipBox.getText().toString().trim();
                if (validIpv4(ip)) {
                    tvIp = ip; sp.edit().putString("ip", ip).apply();
                    status.setText("Using TV at " + ip); status.setTextColor(0xFF54E4A5);
                } else {
                    status.setText("Enter a valid TV address"); status.setTextColor(0xFFFF927F);
                }
            }
        });

        LinearLayout motion = section();
        LinearLayout.LayoutParams motionLp = new LinearLayout.LayoutParams(-1, -2); motionLp.topMargin = dp(13);
        root.addView(motion, motionLp);
        LinearLayout motionTitleRow = new LinearLayout(this); motionTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        motion.addView(motionTitleRow);
        TextView motionTitle = label("LIVE SWING", 11, 0xFF83A4B8, true);
        motionTitle.setLetterSpacing(0.1f); motionTitleRow.addView(motionTitle);
        View flex = new View(this); motionTitleRow.addView(flex, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView liveValue = label("READY", 11, 0xFF54E4A5, true); liveValue.setGravity(Gravity.RIGHT);
        motionTitleRow.addView(liveValue);
        gauge = new MotionGauge(this);
        LinearLayout.LayoutParams gaugeLp = new LinearLayout.LayoutParams(-1, dp(158)); gaugeLp.topMargin = dp(7);
        motion.addView(gauge, gaugeLp);
        live = label("Motion  0.0 m/s²     ·     Swing above the threshold to hit", 12, 0xFFCAD8E1, false);
        live.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(-1, -2); liveLp.topMargin = dp(2);
        motion.addView(live, liveLp);

        LinearLayout settings = section();
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(-1, -2); settingsLp.topMargin = dp(13);
        root.addView(settings, settingsLp);
        sensLabel = label("SWING SENSITIVITY     ·     threshold " + (int) threshold + " m/s²", 11, 0xFFE4EDF3, true);
        settings.addView(sensLabel);
        SeekBar sens = new SeekBar(this); sens.setMax(14);
        sens.setProgress(Math.max(0, Math.min(14, 20 - Math.round(threshold))));
        sens.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF32C8E8));
        sens.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFEAFBFF));
        LinearLayout.LayoutParams sensLp = new LinearLayout.LayoutParams(-1, dp(40)); sensLp.topMargin = dp(5);
        settings.addView(sens, sensLp);
        sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean user) {
                threshold = 20 - progress;
                sensLabel.setText("SWING SENSITIVITY     ·     threshold " + (int) threshold + " m/s²");
                if (user) sp.edit().putFloat("threshold", threshold).apply();
                if (gauge != null) gauge.setThreshold(threshold);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        CheckBox flip = new CheckBox(this);
        flip.setText("Reverse left / right aiming"); flip.setTextColor(0xFFD6E2EA);
        flip.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFF42DDF4));
        flip.setChecked(flipAim);
        flip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                flipAim = checked; sp.edit().putBoolean("flipAim", checked).apply();
            }
        });
        settings.addView(flip);

        Button test = button("PRACTICE HIT", 0xFF35D0E9, 0xFF04131D);
        test.setTextSize(15); test.setMinHeight(dp(58));
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(-1, dp(58)); testLp.topMargin = dp(13);
        root.addView(test, testLp);
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sendHit(0.55f, 0f); } });
        TextView foot = label("Hold your phone upright. Tilt left or right to aim, swing for power, and twist your wrist through the shot to add spin.",
                12, 0xFFADC1CE, false);
        foot.setGravity(Gravity.CENTER); foot.setLineSpacing(dp(3), 1.05f);
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(-1, -2); footLp.topMargin = dp(12);
        root.addView(foot, footLp);

        if (acc == null) {
            status.setText("This phone has no accelerometer"); status.setTextColor(0xFFFF927F);
            test.setEnabled(false); test.setAlpha(0.45f);
        }
        setContentView(frame);
    }

    private Button button(String value, int fill, int foreground) {
        Button b = new Button(this); b.setText(value); b.setTextColor(foreground); b.setTextSize(13);
        b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setBackground(panel(fill, fill, 13)); b.setPadding(dp(12), 0, dp(12), 0); return b;
    }

    private boolean validIpv4(String s) {
        String[] parts = s.split("\\."); if (parts.length != 4) return false;
        try { for (String part : parts) { int n = Integer.parseInt(part); if (n < 0 || n > 255) return false; } }
        catch (NumberFormatException e) { return false; }
        return true;
    }

    @Override protected void onResume() {
        super.onResume();
        if (acc != null) sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME);
        if (gyro != null) sm.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME);
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) { mlock = wm.createMulticastLock("rally-discovery"); mlock.setReferenceCounted(false); mlock.acquire(); }
        } catch (Exception ignored) { }
        startDiscovery(); handler.post(pinger);
    }

    @Override protected void onPause() {
        super.onPause();
        if (sm != null) sm.unregisterListener(this);
        handler.removeCallbacks(pinger); stopDiscovery();
        try { if (mlock != null && mlock.isHeld()) mlock.release(); } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        exec.shutdown(); if (sendSocket != null) sendSocket.close(); super.onDestroy();
    }

    private void startDiscovery() {
        running = true;
        discoveryThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DatagramSocket s = new DatagramSocket(null); s.setReuseAddress(true); s.setBroadcast(true);
                    s.bind(new InetSocketAddress(Net.DISCOVERY_PORT)); discSocket = s;
                    byte[] buf = new byte[64];
                    while (running) {
                        DatagramPacket pk = new DatagramPacket(buf, buf.length); s.receive(pk);
                        String m = new String(pk.getData(), 0, pk.getLength(), StandardCharsets.UTF_8).trim();
                        if (m.equals("TTTV")) {
                            final String ip = pk.getAddress().getHostAddress(); tvIp = ip;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    status.setText("TV READY   ·   " + ip); status.setTextColor(0xFF54E4A5);
                                    ipBox.setText(ip); if (gauge != null) gauge.setConnected(true);
                                }
                            });
                        }
                    }
                } catch (Exception ignored) { }
            }
        }, "rally-tv-discovery");
        discoveryThread.start();
    }

    private void stopDiscovery() {
        running = false; DatagramSocket s = discSocket; if (s != null) s.close(); discSocket = null;
    }

    private void send(final String msg) {
        final String ip = tvIp; if (ip == null) return;
        try {
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        if (sendSocket == null) sendSocket = new DatagramSocket();
                        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                        sendSocket.send(new DatagramPacket(data, data.length, InetAddress.getByName(ip), Net.GAME_PORT));
                    } catch (Exception ignored) { }
                }
            });
        } catch (Exception ignored) { }
    }

    private void sendHit(float power, float aim) {
        sendHit(power, aim, 0f, 0f);
    }

    private void sendHit(float power, float aim, float sideSpin, float topSpin) {
        send(String.format(Locale.US, "H,%.2f,%.2f,%.2f,%.2f", power, aim, sideSpin, topSpin));
        if (gauge != null) gauge.swing(aim, power);
        try { if (vib != null) {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(45);
        } } catch (Exception ignored) { }
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            long now = SystemClock.uptimeMillis();
            if (phase != 0 && gyroTimestamp != 0) {
                float dt = Math.min(0.04f, Math.max(0f, (now - gyroTimestamp) / 1000f));
                spinSideRadians += e.values[2] * dt;
                spinTopRadians += e.values[1] * dt;
            }
            gyroTimestamp = now;
            return;
        }
        if (e.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x=e.values[0], y=e.values[1], z=e.values[2];
        if (!gravInit) { grav[0]=x; grav[1]=y; grav[2]=z; gravInit=true; return; }
        final float alpha=0.98f;
        grav[0]=alpha*grav[0]+(1-alpha)*x; grav[1]=alpha*grav[1]+(1-alpha)*y; grav[2]=alpha*grav[2]+(1-alpha)*z;
        float lx=x-grav[0], ly=y-grav[1], lz=z-grav[2];
        float mag=(float)Math.sqrt(lx*lx+ly*ly+lz*lz); long now=SystemClock.uptimeMillis();
        if (gauge != null) gauge.setMotion(mag, clamp(lx/20f,-1f,1f), phase != 0);
        if (now-lastUi>140) {
            lastUi=now; live.setText(String.format(Locale.US,"Motion  %.1f m/s²     ·     Swing above %.0f",mag,threshold));
        }
        if (phase==0) {
            if (mag>threshold && now>cooldownUntil) {
                phase=1; phaseStart=now; peak=mag; peakX=lx;
                spinSideRadians=0f; spinTopRadians=0f; gyroTimestamp=now;
            }
        } else {
            if (mag>peak) peak=mag; if (Math.abs(lx)>Math.abs(peakX)) peakX=lx;
            if (now-phaseStart>=120) {
                float power=clamp((peak-threshold)/18f,0f,1f);
                float tiltAim=clamp(-grav[0]/7f,-0.65f,0.65f);
                float swingAim=clamp(peakX/30f,-0.35f,0.35f);
                float aim=clamp(tiltAim+swingAim,-1f,1f);
                float sideSpin=clamp(spinSideRadians/1.4f,-1f,1f);
                float topSpin=clamp(spinTopRadians/1.4f,-1f,1f);
                if (flipAim) aim=-aim;
                sendHit(power,aim,sideSpin,topSpin); phase=0; cooldownUntil=now+450;
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }

    private static final class MotionGauge extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval=new RectF();
        private float motion=0, threshold=12, aim=0, power=0, swingT=0;
        private boolean swinging=false, connected=false;
        MotionGauge(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        void setThreshold(float t) { threshold=t; invalidate(); }
        void setConnected(boolean v) { connected=v; invalidate(); }
        void setMotion(float m,float a,boolean active) { motion=m; aim=a; swinging=active; invalidate(); }
        void swing(float a,float pw) { aim=a; power=pw; swingT=1f; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w=getWidth(),h=getHeight(), cx=w*.5f, cy=h*.52f;
            float radius=Math.min(w*.24f,h*.43f);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF0D2030); c.drawCircle(cx,cy,radius*1.18f,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(dpLocal(8));
            p.setColor(0xFF243A4B); oval.set(cx-radius,cy-radius,cx+radius,cy+radius); c.drawArc(oval,135,270,false,p);
            p.setColor(motion>=threshold?0xFF52E3B2:0xFF35CDEB);
            float ratio=Math.max(.03f,Math.min(1f,motion/(threshold*1.6f))); c.drawArc(oval,135,270*ratio,false,p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(dpLocal(21)); p.setColor(Color.WHITE); c.drawText(String.format(Locale.US,"%.1f",motion),cx,cy+dpLocal(5),p);
            p.setTextSize(dpLocal(9)); p.setColor(0xFF9EB5C5); c.drawText("M/S²",cx,cy+dpLocal(22),p);
            float paddleX=w*.81f, paddleY=h*.52f;
            c.save();
            float angle=(swingT>0?aim*65f:aim*14f)+(swinging?aim*20f:0f);
            c.rotate(angle,paddleX,paddleY);
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(dpLocal(8)); p.setColor(0xFFE9F4F7);
            c.drawLine(paddleX,paddleY+dpLocal(10),paddleX,paddleY+dpLocal(43),p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFFF5C70); oval.set(paddleX-dpLocal(19),paddleY-dpLocal(26),paddleX+dpLocal(19),paddleY+dpLocal(20)); c.drawOval(oval,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dpLocal(3)); p.setColor(0xFFFFA0A8); c.drawOval(oval,p);
            c.restore();
            p.setStyle(Paint.Style.FILL); p.setTextSize(dpLocal(10)); p.setTextAlign(Paint.Align.CENTER);
            p.setColor(connected?0xFF62EDB6:0xFF829AA9); c.drawText(connected?"TV LINKED":"SEARCHING",paddleX,h*.91f,p);
            if(swingT>0){ swingT=Math.max(0,swingT-.035f); postInvalidateOnAnimation(); }
        }
        private float dpLocal(float n){return n*getResources().getDisplayMetrics().density;}
    }
}

