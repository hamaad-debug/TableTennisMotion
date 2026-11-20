package com.hmaad.tabletennis;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class TvActivity extends Activity {
    private GameView game;
    private volatile boolean running = true;
    private volatile DatagramSocket rx;
    private Thread rxThread, bcThread;

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new GameView(this);
        game.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(game);
        startNetwork();
    }

    private void startNetwork() {
        rxThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DatagramSocket s = new DatagramSocket(null);
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress(Net.GAME_PORT));
                    rx = s;
                    byte[] buf = new byte[256];
                    while (running) {
                        DatagramPacket pk = new DatagramPacket(buf, buf.length);
                        s.receive(pk);
                        String m = new String(pk.getData(), 0, pk.getLength(), StandardCharsets.UTF_8).trim();
                        game.lastPhonePacket = System.currentTimeMillis();
                        if (m.startsWith("H,")) {
                            String[] a = m.split(",");
                            if (a.length >= 3) {
                                try {
                                    float side = a.length > 3 ? Float.parseFloat(a[3]) : 0f;
                                    float top = a.length > 4 ? Float.parseFloat(a[4]) : 0f;
                                    game.queueHit(Float.parseFloat(a[1]), Float.parseFloat(a[2]), side, top);
                                }
                                catch (NumberFormatException ignored) { }
                            }
                        }
                    }
                } catch (Exception e) {
                    if (running) game.netError = "Network error: " + e.getMessage();
                }
            }
        });
        rxThread.start();

        bcThread = new Thread(new Runnable() {
            @Override public void run() {
                DatagramSocket s = null;
                try {
                    s = new DatagramSocket();
                    s.setBroadcast(true);
                    byte[] msg = "TTTV".getBytes(StandardCharsets.UTF_8);
                    while (running) {
                        for (InetAddress a : Net.broadcastTargets()) {
                            try { s.send(new DatagramPacket(msg, msg.length, a, Net.DISCOVERY_PORT)); }
                            catch (Exception ignored) { }
                        }
                        String ip = Net.localIp(TvActivity.this);
                        game.tvIp = ip != null ? ip : "unknown (check Wi-Fi)";
                        Thread.sleep(1000);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (s != null) s.close();
                }
            }
        });
        bcThread.start();
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (e.getRepeatCount() == 0 && k == KeyEvent.KEYCODE_DPAD_UP) { game.nextStadium(); return true; }
        if (e.getRepeatCount() == 0 && k == KeyEvent.KEYCODE_DPAD_DOWN) { game.nextMatchup(); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_SPACE) {
            game.queueHit(0.5f, 0f); return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) { game.queueHit(0.5f, -0.7f); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { game.queueHit(0.5f, 0.7f); return true; }
        return super.onKeyDown(k, e);
    }

    @Override
    protected void onDestroy() {
        running = false;
        DatagramSocket s = rx;
        if (s != null) s.close();
        if (bcThread != null) bcThread.interrupt();
        super.onDestroy();
    }
}

