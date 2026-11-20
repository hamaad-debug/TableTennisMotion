package com.hmaad.tabletennis;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        UiModeManager um = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(new Intent(this, TvActivity.class));
            finish();
            return;
        }
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFF101820);

        TextView t = new TextView(this);
        t.setText("Table Tennis Motion");
        t.setTextSize(28);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER);
        root.addView(t);

        TextView s = new TextView(this);
        s.setText("Phone = racket, TV = game.\nBoth must be on the same Wi-Fi.\n");
        s.setTextSize(15);
        s.setTextColor(0xFFB0BEC5);
        s.setGravity(Gravity.CENTER);
        root.addView(s);

        Button racket = new Button(this);
        racket.setText("I'm the RACKET (phone)");
        racket.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, RacketActivity.class)); }
        });
        root.addView(racket);

        Button tv = new Button(this);
        tv.setText("I'm the TV (game screen)");
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, TvActivity.class)); }
        });
        root.addView(tv);

        setContentView(root);
    }
}
