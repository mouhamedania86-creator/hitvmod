package com.movieflix.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Netflix-style splash — black + red brand mark. */
public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0B0B);
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        center.setLayoutParams(cp);

        TextView mark = new TextView(this);
        mark.setText("M");
        mark.setTextColor(Color.WHITE);
        mark.setTextSize(42);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(Ui.round(Ui.BRAND, 14, this));
        int s = Ui.dp(this, 88);
        mark.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        center.addView(mark);

        TextView name = new TextView(this);
        name.setText("MovieFlix");
        name.setTextColor(Ui.BRAND);
        name.setTextSize(28);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = Ui.dp(this, 18);
        name.setLayoutParams(np);
        center.addView(name);

        TextView by = new TextView(this);
        by.setText("By FaresCodeX");
        by.setTextColor(0xFF808080);
        by.setTextSize(12);
        by.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(this, 8);
        by.setLayoutParams(bp);
        center.addView(by);

        root.addView(center);
        setContentView(root);

        ScaleAnimation scale = new ScaleAnimation(0.7f, 1f, 0.7f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(650);
        mark.startAnimation(scale);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(700);
        name.startAnimation(fade);

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        }, 1600);
    }
}
