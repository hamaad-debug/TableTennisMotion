package com.hmaad.tabletennis;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared role picker for the phone and Android TV. */
public class MainActivity extends Activity {
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(5, 13, 24));
        getWindow().setNavigationBarColor(Color.rgb(5, 13, 24));
        boolean television = isTelevision();

        FrameLayout frame = new FrameLayout(this);
        ImageView art = new ImageView(this);
        art.setImageResource(television ? R.drawable.arena_landscape : R.drawable.arena_portrait);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(art, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0xB907101C);
        frame.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = television ? 42 : 22;
        content.setPadding(dp(side), dp(24), dp(side), dp(28));
        scroll.addView(content);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        BrandMark mark = new BrandMark(this);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView brandText = text("RALLY", 20, 0xFFF4FBFF, true);
        brandText.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams brandTextLp = new LinearLayout.LayoutParams(-2, -2);
        brandTextLp.leftMargin = dp(12);
        brand.addView(brandText, brandTextLp);
        content.addView(brand);

        TextView eyebrow = text("LOCAL MULTIPLAYER  •  NO CLOUD REQUIRED", 11, 0xFF55DDF5, true);
        eyebrow.setLetterSpacing(0.09f);
        eyebrow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams eyebrowLp = new LinearLayout.LayoutParams(-1, -2);
        eyebrowLp.topMargin = dp(32);
        content.addView(eyebrow, eyebrowLp);

        TextView title = text("TABLE TENNIS\nMOTION", television ? 42 : 34, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(8);
        content.addView(title, titleLp);

        TextView subtitle = text("Your phone is the racket. Your TV is the court.", television ? 18 : 15,
                0xFFD0DFEA, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(9);
        content.addView(subtitle, subLp);

        TextView choose = text("CHOOSE HOW TO PLAY", 12, 0xFF8EA8BC, true);
        choose.setGravity(Gravity.CENTER);
        choose.setLetterSpacing(0.13f);
        LinearLayout.LayoutParams chooseLp = new LinearLayout.LayoutParams(-1, -2);
        chooseLp.topMargin = dp(35);
        chooseLp.bottomMargin = dp(14);
        content.addView(choose, chooseLp);

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(television ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        cards.setGravity(Gravity.CENTER);
        content.addView(cards, new LinearLayout.LayoutParams(-1, -2));

        View racket = roleCard("01", "I'M THE RACKET", "Use this phone to swing, aim, and return the ball.",
                "OPEN RACKET MODE", 0xFF27C6E8, new Runnable() {
                    @Override public void run() { open(RacketActivity.class); }
                });
        View court = roleCard("02", "I'M THE TV", "Show the court and country match. The TV remote can play as Player 2.",
                "OPEN TV COURT", 0xFF9B83FF, new Runnable() {
                    @Override public void run() { open(TvActivity.class); }
                });
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(television ? 0 : -1, -2,
                television ? 1f : 0f);
        if (television) cardLp.rightMargin = dp(12); else cardLp.bottomMargin = dp(14);
        cards.addView(racket, cardLp);
        LinearLayout.LayoutParams courtLp = new LinearLayout.LayoutParams(television ? 0 : -1, -2,
                television ? 1f : 0f);
        if (television) courtLp.leftMargin = dp(12);
        cards.addView(court, courtLp);

        LinearLayout tip = new LinearLayout(this);
        tip.setGravity(Gravity.CENTER_VERTICAL);
        tip.setBackground(panel(0xAA071421, 0x5539B8D5, 14));
        tip.setPadding(dp(15), dp(12), dp(15), dp(12));
        TextView dot = text("●", 16, 0xFF48E4A5, true);
        tip.addView(dot);
        TextView tipText = text("Both devices need to be on the same Wi-Fi network.", 12, 0xFFBDCDD8, false);
        LinearLayout.LayoutParams tipTextLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tipTextLp.leftMargin = dp(10);
        tip.addView(tipText, tipTextLp);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(-1, -2);
        tipLp.topMargin = dp(10);
        content.addView(tip, tipLp);

        setContentView(frame);
        if (television) racket.post(new Runnable() { @Override public void run() { court.requestFocus(); } });
    }

    private boolean isTelevision() {
        UiModeManager manager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return manager != null && manager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private void open(Class<?> target) {
        startActivity(new Intent(this, target));
        finish();
    }

    private View roleCard(String number, String heading, String description, String action, int accent, Runnable click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(panel(0xE60B1A2A, 0x554E7D92, 20));
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setContentDescription(heading + ". " + description);

        TextView top = text(number + "   /   PLAY MODE", 11, accent, true);
        top.setLetterSpacing(0.09f);
        card.addView(top);
        TextView name = text(heading, 20, Color.WHITE, true);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.topMargin = dp(12);
        card.addView(name, nameLp);
        TextView desc = text(description, 13, 0xFFC2D2DD, false);
        desc.setMinHeight(dp(44));
        desc.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(-1, -2);
        descLp.topMargin = dp(7);
        card.addView(desc, descLp);

        Button button = new Button(this);
        button.setText(action);
        button.setTextColor(0xFF06121D);
        button.setTextSize(13);
        button.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setBackground(panel(accent, accent, 14));
        button.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { click.run(); } });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, -2);
        buttonLp.topMargin = dp(16);
        card.addView(button, buttonLp);

        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { click.run(); } });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean focused) {
                card.setBackground(panel(focused ? 0xF211283B : 0xE60B1A2A,
                        focused ? accent : 0x554E7D92, 20));
                card.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(120).start();
            }
        });
        return card;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.create("sans-serif", bold ? 1 : 0));
        return t;
    }

    private GradientDrawable panel(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{fill, fill});
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private static final class BrandMark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        BrandMark(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            float x = getWidth()/2f, y = getHeight()/2f, r = s*.47f;
            p.setStyle(Paint.Style.FILL); p.setColor(0xFF0A2638); c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s*.045f); p.setColor(0xFF42DDF4); c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.FILL); p.setColor(0xFFEF5364);
            oval.set(x-r*.43f,y-r*.52f,x+r*.43f,y+r*.35f); c.drawOval(oval,p);
            p.setColor(0xFFDBEAF2); c.drawRoundRect(x-r*.07f,y+r*.24f,x+r*.07f,y+r*.72f,s*.06f,s*.06f,p);
            p.setColor(0xFF43DDF2); c.drawCircle(x-r*.53f,y+r*.18f,s*.07f,p);
        }
    }
}

