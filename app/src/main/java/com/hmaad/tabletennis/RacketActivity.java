package com.hmaad.tabletennis;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.text.method.DigitsKeyListener;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
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

public class RacketActivity extends Activity implements SensorEventListener {
    private SensorManager sm;
    private Sensor acc;
    private Vibrator vib;
    private final float[] grav = new float[3];
    private boolean gravInit = false;
    private volatile String tvIp = null;
    private DatagramSocket sendSocket, discSocket;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private TextView status, info, live, sensLabel;
    private EditText ipBox;
    private float threshold = 12f;
    private boolean flipAim = false;
    private int phase = 0;
    private long phaseStart = 0, cooldownUntil = 0, lastUi = 0;
    private float peak, peakX;
    private WifiManager.MulticastLock mlock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pinger = new Runnable() {
        @Override public void run() { send("P"); handler.postDelayed(this, 1000); }
    };

    private TextView label(String t, int size, int color) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(size); v.setTextColor(color);
        return v;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        final SharedPreferences sp = getSharedPreferences("tt", MODE_PRIVATE);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF101820);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        root.addView(label("RACKET MODE", 24, Color.WHITE));
        status = label("Searching for TV on Wi-Fi...", 16, 0xFFFFEB3B);
        root.addView(status);
        root.addView(label("\nIf it doesn't find the TV, type the TV IP shown on the TV screen:", 13, 0xFFB0BEC5));

        ipBox = new EditText(this);
        ipBox.setHint("e.g. 192.168.1.5");
        ipBox.setTextColor(Color.WHITE);
        ipBox.setHintTextColor(0xFF78909C);
        ipBox.setSingleLine(true);
        ipBox.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        String saved = sp.getString("ip", "");
        ipBox.setText(saved);
        if (saved.length() > 0) tvIp = saved;
        root.addView(ipBox);

        Button connect = new Button(this);
        connect.setText("Connect to this IP");
        connect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String ip = ipBox.getText().toString().trim();
                if (ip.length() > 0) {
                    tvIp = ip;
                    sp.edit().putString("ip", ip).apply();
                    status.setText("Using TV IP: " + ip);
                }
            }
        });
        root.addView(connect);

        sensLabel = label("\nSensitivity (swing threshold " + (int) threshold + ")", 14, Color.WHITE);
        root.addView(sensLabel);
        SeekBar sens = new SeekBar(this);
        sens.setMax(14);
        sens.setProgress(8);
        sens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                threshold = 20 - prog;
                sensLabel.setText("\nSensitivity (swing threshold " + (int) threshold + ")  - move right = easier");
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        root.addView(sens);

        CheckBox flip = new CheckBox(this);
        flip.setText("Flip left/right aim");
        flip.setTextColor(Color.WHITE);
        flip.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton cb, boolean on) { flipAim = on; }
        });
        root.addView(flip);

        Button test = new Button(this);
        test.setText("TEST HIT (no swing needed)");
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendHit(0.5f, 0f); }
        });
        root.addView(test);

        info = label("\nLast swing: -", 16, 0xFF66BB6A);
        root.addView(info);
        live = label("Motion: -", 13, 0xFFB0BEC5);
        root.addView(live);
        root.addView(label("\nHold the phone upright (portrait), screen facing you, and swing it like a table tennis racket. Swing sideways to aim left/right. Swing harder for a deeper shot.", 13, 0xFF90A4AE));

        if (acc == null) status.setText("This phone has no accelerometer!");
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        if (acc != null) sm.registerListener(this, acc, 10000);
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            mlock = wm.createMulticastLock("tt");
            mlock.setReferenceCounted(false);
            mlock.acquire();
        } catch (Exception ignored) { }
        startDiscovery();
        handler.post(pinger);
    }

    @Override protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
        handler.removeCallbacks(pinger);
        stopDiscovery();
        try { if (mlock != null && mlock.isHeld()) mlock.release(); } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        exec.shutdown();
        if (sendSocket != null) sendSocket.close();
        super.onDestroy();
    }

    private void startDiscovery() {
        running = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DatagramSocket s = new DatagramSocket(null);
                    s.setReuseAddress(true);
                    s.setBroadcast(true);
                    s.bind(new InetSocketAddress(Net.DISCOVERY_PORT));
                    discSocket = s;
                    byte[] buf = new byte[64];
                    while (running) {
                        DatagramPacket pk = new DatagramPacket(buf, buf.length);
                        s.receive(pk);
                        String m = new String(pk.getData(), 0, pk.getLength(), StandardCharsets.UTF_8).trim();
                        if (m.equals("TTTV")) {
                            final String ip = pk.getAddress().getHostAddress();
                            if (!ip.equals(tvIp)) {
                                tvIp = ip;
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        status.setText("TV found: " + ip + "  (ready!)");
                                        ipBox.setText(ip);
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
        }).start();
    }

    private void stopDiscovery() {
        running = false;
        DatagramSocket s = discSocket;
        if (s != null) s.close();
    }

    private void send(final String msg) {
        final String ip = tvIp;
        if (ip == null) return;
        try {
            exec.execute(new Runnable() {
                @Override public void run() {
                    try {
                        if (sendSocket == null) sendSocket = new DatagramSocket();
                        byte[] d = msg.getBytes(StandardCharsets.UTF_8);
                        sendSocket.send(new DatagramPacket(d, d.length, InetAddress.getByName(ip), Net.GAME_PORT));
                    } catch (Exception ignored) { }
                }
            });
        } catch (Exception ignored) { }
    }

    private void sendHit(float power, float aim) {
        send(String.format(Locale.US, "H,%.2f,%.2f", power, aim));
        info.setText(String.format(Locale.US, "\nLast swing: power %.2f  aim %.2f", power, aim));
        try { if (vib != null) vib.vibrate(40); } catch (Exception ignored) { }
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    @Override public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1], z = e.values[2];
        if (!gravInit) { grav[0] = x; grav[1] = y; grav[2] = z; gravInit = true; return; }
        final float a = 0.98f;
        grav[0] = a * grav[0] + (1 - a) * x;
        grav[1] = a * grav[1] + (1 - a) * y;
        grav[2] = a * grav[2] + (1 - a) * z;
        float lx = x - grav[0], ly = y - grav[1], lz = z - grav[2];
        float mag = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        long now = SystemClock.uptimeMillis();

        if (now - lastUi > 150) {
            lastUi = now;
            live.setText(String.format(Locale.US, "Motion: %.1f  (need > %d)", mag, (int) threshold));
        }

        if (phase == 0) {
            if (mag > threshold && now > cooldownUntil) {
                phase = 1; phaseStart = now; peak = mag; peakX = lx;
            }
        } else {
            if (mag > peak) peak = mag;
            if (Math.abs(lx) > Math.abs(peakX)) peakX = lx;
            if (now - phaseStart >= 60) {
                float power = clamp((peak - threshold) / 18f, 0f, 1f);
                float aim = clamp(peakX / 20f, -1f, 1f);
                if (flipAim) aim = -aim;
                sendHit(power, aim);
                phase = 0;
                cooldownUntil = now + 450;
            }
        }
    }

    @Override public void onAccuracyChanged(Sensor s, int accuracy) { }
}
