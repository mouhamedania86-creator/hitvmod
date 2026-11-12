package com.movieflix.app;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.view.SurfaceView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * مشغّل الفيديو — ExoPlayer (Media3) مع تحسينات للشبكات الضعيفة،
 * ونسخة احتياطية تلقائية إلى MediaPlayer لو تعذّر تشغيل ExoPlayer.
 */
public class PlayerActivity extends Activity {

    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_LABELS = "labels";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_POSTER = "poster";
    public static final String EXTRA_CONTENT = "contentId";
    public static final String EXTRA_EPISODE = "episodeId";
    public static final String EXTRA_EPNO = "episodeNo";
    public static final String EXTRA_CATEGORY = "category";
    public static final String EXTRA_START = "start";
    public static final String EXTRA_INDEX = "index";

    private static final int GOLD = 0xFFE50914; // Netflix red
    private static final int GOLD_D = 0xFFB20710;

    private String[] urls = new String[0];
    private String[] labels = new String[0];
    private String title = "", poster = "", contentId = "", episodeId = "";
    private int epNo = 1, category = 1, qualityIdx = 0, errors = 0;
    private long startPos = 0;

    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private SurfaceView surface;
    private VideoView fallback;
    private android.media.MediaPlayer fbPlayer;
    private boolean usingExo = true;

    private FrameLayout root, videoBox;
    private View controls;
    private LinearLayout bottomBar, topBar, centerBar, extraBar;
    private TextView titleView, timeNow, timeAll, btnPlay, qualityChip, speedChip, bufferHint, statChip;
    private SeekBar seek;
    private ProgressBar spinner;
    private LinearLayout errorPanel;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean dragging = false, locked = false, dataSaver = false, pip = false;
    private int aspect = 0;   // 0 fit · 1 fill · 2 zoom
    private final float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};
    private int rotMode = 0;                 // 0 تلقائي · 1 أفقي · 2 رأسي
    private AudioManager audio;
    private TextView hud;
    private long sleepAt = 0;
    private String[] epIds = new String[0];
    private String[] epIndexes = new String[0];
    private int epPos = 0;
    private TextView btnPrev, btnNext;
    // إيماءات
    private float downX, downY;
    private long downTime;
    private int gesture = 0;                 // 0 لا شيء · 1 سطوع · 2 صوت · 3 تقديم
    private long lastTap = 0;
    private int speedIdx = 2;

    /* ═══════════ إنشاء ═══════════ */

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        Intent it = getIntent();
        urls = it.getStringArrayExtra(EXTRA_URLS);
        labels = it.getStringArrayExtra(EXTRA_LABELS);
        if (urls == null || urls.length == 0) {
            String single = it.getStringExtra("url");
            if (single != null) urls = new String[]{single};
            else urls = new String[0];
        }
        if (labels == null || labels.length != urls.length) {
            labels = new String[urls.length];
            for (int i = 0; i < urls.length; i++) labels[i] = "جودة " + (i + 1);
        }
        title = it.getStringExtra(EXTRA_TITLE);
        poster = it.getStringExtra(EXTRA_POSTER);
        contentId = it.getStringExtra(EXTRA_CONTENT);
        episodeId = it.getStringExtra(EXTRA_EPISODE);
        epNo = it.getIntExtra(EXTRA_EPNO, 1);
        category = it.getIntExtra(EXTRA_CATEGORY, 1);
        qualityIdx = Math.max(0, it.getIntExtra(EXTRA_INDEX, 0));
        if (qualityIdx >= urls.length) qualityIdx = 0;
        epIds = it.getStringArrayExtra("epIds");
        epIndexes = it.getStringArrayExtra("epIndexes");
        epPos = it.getIntExtra("epPos", 0);
        if (epIds == null) epIds = new String[0];
        if (epIndexes == null) epIndexes = new String[0];
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        buildUi();
        startPos = loadPosition();
        WatchStats.bumpSession(this);

        if (urls.length == 0) {
            showError("لا يوجد رابط للتشغيل");
            return;
        }
        startExo();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        videoBox = new FrameLayout(this);
        videoBox.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(videoBox);

        surface = new SurfaceView(this);
        surface.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        videoBox.addView(surface);

        // شريط تحميل
        spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                Ui.dp(this, 54), Ui.dp(this, 54), Gravity.CENTER);
        spinner.setLayoutParams(sp);
        root.addView(spinner);

        bufferHint = Ui.text(this, "الشبكة ضعيفة… جاري التحميل", 12, GOLD, false);
        bufferHint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams bh = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        bh.topMargin = Ui.dp(this, 90);
        bufferHint.setLayoutParams(bh);
        root.addView(bufferHint);

        controls = buildControls();
        root.addView(controls);

        errorPanel = buildErrorPanel();
        root.addView(errorPanel);

        // مؤشر الإيماءات
        hud = Ui.text(this, "", 13, 0xFFFFFFFF, true);
        hud.setGravity(Gravity.CENTER);
        hud.setBackground(Ui.round(0xCC000000, 12, this));
        hud.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        hud.setLayoutParams(hp);
        hud.setVisibility(View.GONE);
        root.addView(hud);

        setContentView(root);
        applyImmersive();

    }

    private View buildControls() {
        FrameLayout layer = new FrameLayout(this);
        layer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.setBackgroundColor(0x66000000);

        // ── الأعلى ──
        topBar = Ui.row(this);
        topBar.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        topBar.setBackground(Ui.gradient(0xCC000000, 0x00000000, 0, false, this));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        topBar.setLayoutParams(tp);

        TextView close = Ui.text(this, "✕", 16, GOLD, true);
        close.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        close.setBackground(Ui.roundStroke(0x55000000, 10, 0x66E50914, this));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        topBar.addView(close);

        titleView = Ui.text(this, title == null ? "" : title, 12.5f, Color.WHITE, true);
        titleView.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleView.setPadding(Ui.dp(this, 10), 0, 0, 0);
        titleView.setSingleLine(true);
        topBar.addView(titleView);

        qualityChip = Ui.text(this, labels[Math.min(qualityIdx, labels.length - 1)], 10.5f, 0xFFFFFFFF, true);
        qualityChip.setBackground(Ui.gradient(GOLD, GOLD_D, 8, true, this));
        qualityChip.setPadding(Ui.dp(this, 8), Ui.dp(this, 3), Ui.dp(this, 8), Ui.dp(this, 3));
        qualityChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showQualities(); }
        });
        topBar.addView(qualityChip);

        TextView ext = Ui.text(this, "⤴", 15, GOLD, true);
        ext.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 2), 0);
        ext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(); }
        });
        topBar.addView(ext);
        layer.addView(topBar);

        // ── الوسط ──
        centerBar = Ui.row(this);
        centerBar.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        centerBar.setLayoutParams(cp);

        TextView back10 = roundBtn("⏪", 18);
        back10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(-10000); }
        });
        centerBar.addView(back10);

        btnPlay = roundBtn("▶", 26);
        LinearLayout.LayoutParams pp = Ui.lp(Ui.dp(this, 64), Ui.dp(this, 64));
        pp.setMargins(Ui.dp(this, 26), 0, Ui.dp(this, 26), 0);
        btnPlay.setLayoutParams(pp);
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlay(); }
        });
        centerBar.addView(btnPlay);

        TextView fwd10 = roundBtn("⏩", 18);
        fwd10.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { seekBy(10000); }
        });
        centerBar.addView(fwd10);

        btnPrev = roundBtn("⏮", 15);
        LinearLayout.LayoutParams pv = Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44));
        pv.setMargins(Ui.dp(this, 18), 0, 0, 0);
        btnPrev.setLayoutParams(pv);
        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playEpisode(-1); }
        });
        centerBar.addView(btnPrev);

        btnNext = roundBtn("⏭", 15);
        LinearLayout.LayoutParams nx = Ui.lp(Ui.dp(this, 44), Ui.dp(this, 44));
        nx.setMargins(Ui.dp(this, 8), 0, 0, 0);
        btnNext.setLayoutParams(nx);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { playEpisode(1); }
        });
        centerBar.addView(btnNext);
        layer.addView(centerBar);

        // ── الأسفل ──
        bottomBar = Ui.column(this);
        bottomBar.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 10));
        bottomBar.setBackground(Ui.gradient(0x00000000, 0xE6000000, 0, false, this));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        bottomBar.setLayoutParams(bp);

        LinearLayout timeRow = Ui.row(this);
        timeNow = Ui.text(this, "00:00", 10.5f, 0xFFEDE3C8, true);
        timeAll = Ui.text(this, "00:00", 10.5f, 0xFF9A9382, false);
        seek = new SeekBar(this);
        seek.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        seek.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) timeNow.setText(fmt(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                dragging = false;
                if (usingExo && player != null) player.seekTo(sb.getProgress());
                else if (fallback != null) fallback.seekTo(sb.getProgress());
            }
        });
        timeRow.addView(timeNow);
        timeRow.addView(seek);
        timeRow.addView(timeAll);
        bottomBar.addView(timeRow);

        extraBar = Ui.row(this);
        extraBar.setPadding(0, Ui.dp(this, 6), 0, 0);

        speedChip = chip("1×");
        speedChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleSpeed(); }
        });
        extraBar.addView(speedChip);

        TextView audioChip = chip(I18n.t(this, "audio"));
        audioChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTrackPicker(C.TRACK_TYPE_AUDIO); }
        });
        extraBar.addView(audioChip);

        TextView subChip = chip(I18n.t(this, "subtitles"));
        subChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showTrackPicker(C.TRACK_TYPE_TEXT); }
        });
        extraBar.addView(subChip);

        TextView aspectChip = chip("ملء");
        aspectChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                aspect = (aspect + 1) % 3;
                ((TextView) v).setText(aspect == 0 ? "ملء" : aspect == 1 ? "تغطية" : "تكبير");
                applyAspect();
            }
        });
        extraBar.addView(aspectChip);

        TextView saver = chip("توفير");
        saver.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dataSaver = !dataSaver;
                ((TextView) v).setText(dataSaver ? "توفير ✓" : "توفير");
                applyDataSaver();
            }
        });
        extraBar.addView(saver);

        TextView lock = chip("قفل");
        lock.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setLocked(!locked); }
        });
        extraBar.addView(lock);

        if (Build.VERSION.SDK_INT >= 26) {
            TextView pipBtn = chip("PiP");
            pipBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { enterPip(); }
            });
            extraBar.addView(pipBtn);
        }

        TextView rotBtn = chip("⤾ تدوير");
        rotBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleRotation(); }
        });
        extraBar.addView(rotBtn);

        TextView sleepBtn = chip("⏱ نوم");
        sleepBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSleepDialog(); }
        });
        extraBar.addView(sleepBtn);

        statChip = chip("—");
        statChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showStats(); }
        });
        extraBar.addView(statChip);

        bottomBar.addView(extraBar);
        layer.addView(bottomBar);

        // إخفاء تلقائي
        final Runnable hide = new Runnable() {
            @Override public void run() { if (!locked) showControls(false); }
        };
        layer.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) { return onGesture(e); }
        });
        ui.postDelayed(hide, 3500);
        return layer;
    }

    private TextView roundBtn(String txt, float size) {
        TextView t = Ui.text(this, txt, size, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setLayoutParams(Ui.lp(Ui.dp(this, 52), Ui.dp(this, 52)));
        t.setBackground(Ui.roundStroke(0x55000000, 28, 0x66E50914, this));
        return t;
    }

    private TextView chip(String txt) {
        TextView t = Ui.text(this, txt, 10.5f, 0xFFEDE3C8, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        t.setBackground(Ui.roundStroke(0x33FFFFFF, 9, 0x55E50914, this));
        LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, Ui.dp(this, 6), 0);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout buildErrorPanel() {
        LinearLayout p = Ui.column(this);
        p.setGravity(Gravity.CENTER);
        p.setBackground(Ui.roundStroke(0xEE0B0B0B, 18, 0x55E50914, this));
        p.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 290),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        p.setLayoutParams(lp);
        p.setVisibility(View.GONE);
        return p;
    }


    /* ═══════════ التدوير والإيماءات ═══════════ */

    private void applyImmersive() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } catch (Throwable ignored) { }
    }

    private void cycleRotation() {
        rotMode = (rotMode + 1) % 3;
        try {
            if (rotMode == 0) setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR);
            else if (rotMode == 1) setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            else setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } catch (Throwable ignored) { }
        String txt = rotMode == 0 ? "تدوير تلقائي" : rotMode == 1 ? "أفقي" : "رأسي";
        Toast.makeText(this, txt, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        applyImmersive();
        ui.postDelayed(new Runnable() {
            @Override public void run() { applyAspect(); }
        }, 300);
    }

    private void showHud(String txt) {
        if (hud == null) return;
        hud.setText(txt);
        hud.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hudHide);
        ui.postDelayed(hudHide, 900);
    }

    private final Runnable hudHide = new Runnable() {
        @Override public void run() { if (hud != null) hud.setVisibility(View.GONE); }
    };

    private boolean onGesture(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY(); downTime = System.currentTimeMillis();
                gesture = 0;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (gesture == 0 && Math.abs(dy) > Ui.dp(this, 18) && Math.abs(dy) > Math.abs(dx)) {
                    gesture = e.getX() > videoBox.getWidth() / 2f ? 2 : 1;   // صوت / سطوع
                }
                if (gesture == 1) changeBrightness(-dy / Math.max(1, videoBox.getHeight()) * 1.6f);
                else if (gesture == 2) changeVolume(-dy / Math.max(1, videoBox.getHeight()) * 1.6f);
                return true;
            }
            case MotionEvent.ACTION_UP: {
                long dt = System.currentTimeMillis() - downTime;
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (gesture == 0 && Math.abs(dx) < Ui.dp(this, 12) && Math.abs(dy) < Ui.dp(this, 12) && dt < 350) {
                    long now = System.currentTimeMillis();
                    if (now - lastTap < 320) {
                        // نقر مزدوج: تقديم/تأخير حسب الجهة
                        if (e.getX() > videoBox.getWidth() / 2f) { seekBy(10000); showHud("⏩ 10 ثوانٍ"); }
                        else { seekBy(-10000); showHud("⏪ 10 ثوانٍ"); }
                        lastTap = 0;
                    } else {
                        lastTap = now;
                        ui.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (System.currentTimeMillis() - lastTap >= 300) toggleControls();
                            }
                        }, 300);
                    }
                }
                gesture = 0;
                return true;
            }
        }
        return true;
    }

    private void changeBrightness(float delta) {
        try {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            if (lp.screenBrightness < 0) lp.screenBrightness = 0.5f;
            lp.screenBrightness = Math.max(0.05f, Math.min(1f, lp.screenBrightness + delta * 0.12f));
            getWindow().setAttributes(lp);
            showHud("☀ " + Math.round(lp.screenBrightness * 100) + "%");
        } catch (Throwable ignored) { }
    }

    private void changeVolume(float delta) {
        try {
            if (audio == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int next = Math.max(0, Math.min(max, cur + Math.round(delta * max * 0.3f)));
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
            showHud("🔊 " + Math.round(next * 100f / Math.max(1, max)) + "%");
        } catch (Throwable ignored) { }
    }

    private void showSleepDialog() {
        final String[] opts = {"إلغاء المؤقت", "١٥ دقيقة", "٣٠ دقيقة", "٦٠ دقيقة", "٩٠ دقيقة"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("مؤقت النوم")
                .setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        if (which == 0) { sleepAt = 0; Toast.makeText(PlayerActivity.this, "أُلغي المؤقت", Toast.LENGTH_SHORT).show(); return; }
                        int mins = which == 1 ? 15 : which == 2 ? 30 : which == 3 ? 60 : 90;
                        sleepAt = System.currentTimeMillis() + mins * 60000L;
                        Toast.makeText(PlayerActivity.this, "سيتوقف بعد " + mins + " دقيقة", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    /* ═══════════ الحلقة التالية/السابقة ═══════════ */

    private void playEpisode(int delta) {
        int target = epPos + delta;
        if (epIds.length == 0 || target < 0 || target >= epIds.length) {
            Toast.makeText(this, delta > 0 ? "لا توجد حلقة تالية" : "لا توجد حلقة سابقة", Toast.LENGTH_SHORT).show();
            return;
        }
        final int idx = target;
        Toast.makeText(this, "جاري تحميل الحلقة…", Toast.LENGTH_SHORT).show();
        final String epId = epIds[idx];
        new Thread(new Runnable() {
            @Override public void run() {
                String found = null;
                try {
                    HitvClient c = new HitvClient(PlayerActivity.this);
                    for (String def : new String[]{"GROOT_HD", "GROOT_SD", "GROOT_LD"}) {
                        try {
                            org.json.JSONObject payload = c.preview(contentId, epId, category, def);
                            org.json.JSONArray arr = Norm.streams(payload);
                            for (int i = 0; i < arr.length(); i++) {
                                String u = arr.optJSONObject(i).optString("url", "");
                                if (u.contains(".m3u8")) { found = u; break; }
                                if (found == null && u.length() > 0) found = u;
                            }
                        } catch (Throwable ignored) { }
                        if (found != null) break;
                    }
                } catch (Throwable ignored) { }
                final String url = found;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (url == null) {
                            Toast.makeText(PlayerActivity.this, "تعذّر تحميل الحلقة", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        epPos = idx;
                        episodeId = epId;
                        startPos = 0;
                        urls = new String[]{url};
                        labels = new String[]{"تلقائي"};
                        qualityIdx = 0;
                        errors = 0;
                        if (usingExo && player != null) {
                            player.stop();
                            loadSource();
                        } else {
                            if (fallback != null) { videoBox.removeView(fallback); fallback = null; }
                            startFallback("حلقة جديدة");
                        }
                        if (titleView != null) titleView.setText(title + " · #" + (epIndexes.length > idx ? epIndexes[idx] : String.valueOf(idx + 1)));
                    }
                });
            }
        }).start();
    }

    /* ═══════════ ExoPlayer ═══════════ */

    private static final String[] EXO_REQUIRED = {
            "androidx.media3.exoplayer.ExoPlayer",
            "androidx.media3.exoplayer.hls.HlsMediaSource",
            "androidx.media3.datasource.DefaultHttpDataSource",
            "androidx.media3.common.Player",
            "androidx.collection.CircularIntArray",
            "kotlin.collections.ArraysKt",
            "com.google.common.util.concurrent.ListenableFuture",
            "com.google.common.util.concurrent.internal.InternalFutures",
    };

    /** يتأكد أن كل حزم المشغّل موجودة فعلاً — يمنع كراش NoClassDefFoundError. */
    private String missingExoClass() {
        for (String c : EXO_REQUIRED) {
            try {
                Class.forName(c);
            } catch (Throwable t) {
                return c;
            }
        }
        return null;
    }

    private void startExo() {
        String missing = missingExoClass();
        if (missing != null) {
            startFallback("حزمة ناقصة: " + missing);
            return;
        }
        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setUserAgent("okhttp/4.12.0")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(25000);

            trackSelector = new DefaultTrackSelector(this);
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredTextLanguage("ar")
                    .setPreferredAudioLanguage("ar")
                    .setMaxVideoBitrate(Integer.MAX_VALUE)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true));

            DefaultLoadControl load = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20000, 70000, 3000, 6000)   // صبر أطول للشبكات الضعيفة
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build();

            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(load)
                    .setMediaSourceFactory(new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(http))
                    .build();

            player.setVideoSurfaceView(surface);
            player.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    boolean buffering = state == Player.STATE_BUFFERING;
                    spinner.setVisibility(buffering && player.getPlayWhenReady() ? View.VISIBLE : View.GONE);
                    bufferHint.setVisibility(buffering && player.getPlayWhenReady() ? View.VISIBLE : View.GONE);
                    btnPlay.setText(player.isPlaying() ? "❚❚" : "▶");
                    if (state == Player.STATE_ENDED) onEnded();
                }
                @Override public void onIsPlayingChanged(boolean playing) {
                    btnPlay.setText(playing ? "❚❚" : "▶");
                    if (playing) {
                        spinner.setVisibility(View.GONE);
                        bufferHint.setVisibility(View.GONE);
                    }
                }
                @Override public void onVideoSizeChanged(VideoSize size) { applyAspect(); }
                @Override public void onPlayerError(PlaybackException error) { onError(error); }
            });

            loadSource();
            ui.post(ticker);
        } catch (Throwable t) {
            startFallback("تعذّر تشغيل المشغّل المتقدّم: " + t.getClass().getSimpleName());
        }
    }

    private void loadSource() {
        String url = urls[qualityIdx];
        MediaSource src;
        if (url.toLowerCase().contains(".m3u8")) {
            src = new HlsMediaSource.Factory(new DefaultHttpDataSource.Factory()
                    .setUserAgent("okhttp/4.12.0")
                    .setAllowCrossProtocolRedirects(true))
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(url));
        } else {
            src = new androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                    new DefaultHttpDataSource.Factory().setUserAgent("okhttp/4.12.0")
                            .setAllowCrossProtocolRedirects(true))
                    .createMediaSource(MediaItem.fromUri(url));
        }
        player.setMediaSource(src);
        player.prepare();
        if (startPos > 3000) player.seekTo(startPos);
        player.setPlayWhenReady(true);
        qualityChip.setText(labels[qualityIdx]);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try {
                if (usingExo && player != null) {
                    long pos = player.getCurrentPosition();
                    long dur = Math.max(1, player.getDuration());
                    if (!dragging) {
                        seek.setMax((int) dur);
                        seek.setProgress((int) pos);
                        timeNow.setText(fmt(pos));
                    }
                    timeAll.setText(fmt(dur));
                    seek.setSecondaryProgress((int) (dur * player.getBufferedPercentage() / 100));
                    long bitrate = 0;
                    Format f = currentFormat();
                    if (f != null && f.bitrate > 0) bitrate = f.bitrate;
                    statChip.setText(fmt(pos) + " · " + player.getBufferedPercentage() + "%"
                            + (bitrate > 0 ? " · " + (bitrate / 1000) + "k" : ""));
                }
                if (sleepAt > 0 && System.currentTimeMillis() > sleepAt) {
                    sleepAt = 0;
                    try { if (usingExo && player != null) player.pause(); } catch (Throwable ignored) { }
                    savePosition(currentPos());
                    Toast.makeText(PlayerActivity.this, "انتهى مؤقت النوم", Toast.LENGTH_LONG).show();
                    finish();
                }
            } catch (Throwable ignored) { }
            ui.postDelayed(this, 500);
        }
    };

    private Format currentFormat() {
        try {
            for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
                if (g.getType() == C.TRACK_TYPE_VIDEO && g.isSelected()) {
                    Format f = g.getTrackFormat(0);
                    return f;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    /* ═══════════ التحكم ═══════════ */

    private void togglePlay() {
        if (usingExo && player != null) {
            if (player.isPlaying()) player.pause(); else player.play();
        } else if (fallback != null) {
            if (fallback.isPlaying()) fallback.pause(); else fallback.start();
        }
    }

    private void seekBy(long ms) {
        if (usingExo && player != null) {
            long p = Math.max(0, Math.min(player.getDuration(), player.getCurrentPosition() + ms));
            player.seekTo(p);
        } else if (fallback != null) {
            fallback.seekTo(Math.max(0, fallback.getCurrentPosition() + (int) ms));
        }
    }

    private void cycleSpeed() {
        speedIdx = (speedIdx + 1) % speeds.length;
        float sp = speeds[speedIdx];
        speedChip.setText((sp == Math.round(sp) ? String.valueOf((int) sp) : String.valueOf(sp)) + "×");
        try {
            if (usingExo && player != null) player.setPlaybackSpeed(sp);
            else if (fbPlayer != null && Build.VERSION.SDK_INT >= 23)
                fbPlayer.setPlaybackParams(fbPlayer.getPlaybackParams().setSpeed(sp));
        } catch (Throwable ignored) { }
    }

    private void applyDataSaver() {
        if (!usingExo || player == null) return;
        try {
            TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
            if (dataSaver) b.setMaxVideoSizeSd();
            else b.setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
            player.setTrackSelectionParameters(b.build());
        } catch (Throwable ignored) { }
    }

    private void applyAspect() {
        if (usingExo && player != null) {
            VideoSize v = player.getVideoSize();
            if (v.width > 0 && v.height > 0) layoutSurface(v.width, v.height);
        }
    }

    private void layoutSurface(int vw, int vh) {
        int w = videoBox.getWidth(), h = videoBox.getHeight();
        if (w == 0 || h == 0) return;
        float videoRatio = (float) vw / vh;
        float boxRatio = (float) w / h;
        int nw = w, nh = h;
        if (aspect == 0) {           // fit (كامل الصورة)
            if (videoRatio > boxRatio) nh = (int) (w / videoRatio);
            else nw = (int) (h * videoRatio);
        } else if (aspect == 1) {    // fill (تغطية)
            if (videoRatio > boxRatio) nw = (int) (h * videoRatio);
            else nh = (int) (w / videoRatio);
        } else {                     // zoom
            nw = (int) (w * 1.25f);
            nh = (int) (h * 1.25f);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(nw, nh, Gravity.CENTER);
        surface.setLayoutParams(lp);
        if (fallback != null) fallback.setLayoutParams(new FrameLayout.LayoutParams(nw, nh, Gravity.CENTER));
    }

    private void setLocked(boolean on) {
        locked = on;
        topBar.setVisibility(on ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        centerBar.setVisibility(on ? View.GONE : View.VISIBLE);
        Toast.makeText(this, on ? "تم قفل الشاشة — اضغط مطوّلًا للفتح" : "تم فتح الشاشة",
                Toast.LENGTH_SHORT).show();
        if (on) ui.postDelayed(new Runnable() {
            @Override public void run() { if (locked) setLocked(false); }
        }, 4000);
    }

    private void showControls(boolean visible) {
        int v = visible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(v);
        bottomBar.setVisibility(v);
        centerBar.setVisibility(v);
        if (visible) ui.postDelayed(new Runnable() {
            @Override public void run() { if (!locked) showControls(false); }
        }, 3500);
    }

    private void toggleControls() {
        if (locked) { setLocked(false); return; }
        showControls(controls.getVisibility() != View.VISIBLE || topBar.getVisibility() != View.VISIBLE);
    }

    /* ═══════════ الجودات ═══════════ */

    private void showQualities() {
        final List<String> names = new ArrayList<String>();
        final List<Integer> idxs = new ArrayList<Integer>();
        for (int i = 0; i < urls.length; i++) {
            names.add(labels[i] + (i == qualityIdx ? "  ✓" : ""));
            idxs.add(i);
        }
        // جودات HLS الداخلية (إن وُجدت)
        try {
            if (usingExo && player != null) {
                Tracks t = player.getCurrentTracks();
                for (Tracks.Group g : t.getGroups()) {
                    if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                    for (int i = 0; i < g.length; i++) {
                        Format f = g.getTrackFormat(i);
                        if (f.height > 0) names.add("داخلية " + f.height + "p");
                    }
                }
            }
        } catch (Throwable ignored) { }

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this);
        b.setTitle("اختر الجودة");
        b.setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                if (which < idxs.size()) {
                    qualityIdx = idxs.get(which);
                    if (usingExo && player != null) {
                        long pos = player.getCurrentPosition();
                        boolean play = player.isPlaying();
                        loadSource();
                        player.seekTo(pos);
                        player.setPlayWhenReady(play);
                    } else {
                        startFallback("تبديل الجودة");
                    }
                } else {
                    // جودة داخلية من قائمة HLS
                    applyInternalQuality(which - idxs.size());
                }
            }
        });
        b.show();
    }

    private void applyInternalQuality(int videoGroupIndex) {
        try {
            int seen = -1;
            for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
                if (g.getType() != C.TRACK_TYPE_VIDEO) continue;
                seen++;
                if (seen != videoGroupIndex) continue;
                TrackGroup tg = g.getMediaTrackGroup();
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setOverrideForType(new TrackSelectionOverride(tg, 0)).build());
                Toast.makeText(this, "تم تثبيت الجودة", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Throwable t) {
            Toast.makeText(this, "تعذّر تغيير الجودة", Toast.LENGTH_SHORT).show();
        }
    }

    
    private void showTrackPicker(int trackType) {
        if (player == null || !usingExo) {
            Toast.makeText(this, I18n.t(this, "empty"), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Tracks tracks = player.getCurrentTracks();
            final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            final java.util.ArrayList<TrackSelectionOverride> overrides = new java.util.ArrayList<TrackSelectionOverride>();
            names.add(I18n.t(this, "off"));
            overrides.add(null);
            for (Tracks.Group g : tracks.getGroups()) {
                if (g.getType() != trackType) continue;
                TrackGroup tg = g.getMediaTrackGroup();
                for (int i = 0; i < tg.length; i++) {
                    Format f = tg.getFormat(i);
                    String lang = f.language == null ? "" : f.language;
                    String label = f.label == null ? "" : f.label;
                    String name = label.length() > 0 ? label : (lang.length() > 0 ? lang : ("#" + (i + 1)));
                    // prefer friendly names for ar/en/fr
                    if ("ar".equalsIgnoreCase(lang) || "ara".equalsIgnoreCase(lang)) name = "العربية";
                    else if ("en".equalsIgnoreCase(lang) || "eng".equalsIgnoreCase(lang)) name = "English";
                    else if ("fr".equalsIgnoreCase(lang) || "fra".equalsIgnoreCase(lang) || "fre".equalsIgnoreCase(lang)) name = "Français";
                    if (g.isTrackSelected(i)) name = "✓ " + name;
                    names.add(name);
                    overrides.add(new TrackSelectionOverride(tg, i));
                }
            }
            if (names.size() <= 1) {
                Toast.makeText(this, I18n.t(this, "empty"), Toast.LENGTH_SHORT).show();
                return;
            }
            final int type = trackType;
            new android.app.AlertDialog.Builder(this)
                    .setTitle(trackType == C.TRACK_TYPE_TEXT ? I18n.t(this, "subtitles") : I18n.t(this, "audio"))
                    .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int which) {
                            try {
                                TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
                                if (which == 0) {
                                    // off / default
                                    b.clearOverridesOfType(type);
                                    if (type == C.TRACK_TYPE_TEXT) {
                                        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
                                    }
                                } else {
                                    b.setTrackTypeDisabled(type, false);
                                    TrackSelectionOverride ovr = overrides.get(which);
                                    if (ovr != null) b.setOverrideForType(ovr);
                                }
                                player.setTrackSelectionParameters(b.build());
                            } catch (Throwable t) {
                                Toast.makeText(PlayerActivity.this, I18n.t(PlayerActivity.this, "error"), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).show();
        } catch (Throwable t) {
            Toast.makeText(this, I18n.t(this, "error"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showStats() {
        String txt;
        if (usingExo && player != null) {
            Format f = currentFormat();
            txt = "المخزّن: " + player.getBufferedPercentage() + "%\n"
                    + "الجودة: " + (f == null ? "—" : (f.width + "×" + f.height))
                    + (f != null && f.bitrate > 0 ? " · " + (f.bitrate / 1000) + " kbps" : "")
                    + "\nالأخطاء: " + errors;
        } else {
            txt = "وضع احتياطي (MediaPlayer)";
        }
        Toast.makeText(this, txt, Toast.LENGTH_LONG).show();
    }

    /* ═══════════ أخطاء/احتياطي ═══════════ */

    private void onError(PlaybackException e) {
        errors++;
        if (errors == 1 && qualityIdx + 1 < urls.length) {
            qualityIdx++;
            Toast.makeText(this, "تعثّر التشغيل — التحويل لجودة أقل", Toast.LENGTH_SHORT).show();
            loadSource();
            return;
        }
        if (errors == 2) {
            Toast.makeText(this, "تعثّر مجددًا — تجربة المشغّل الاحتياطي", Toast.LENGTH_SHORT).show();
            startFallback("فشل ExoPlayer");
            return;
        }
        showError("تعذّر التشغيل: " + e.getErrorCodeName());
    }

    private void startFallback(String why) {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            usingExo = false;
            if (surface != null) surface.setVisibility(View.GONE);

            fallback = new VideoView(this);
            fallback.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            MediaController mc = new MediaController(this);
            mc.setAnchorView(fallback);
            fallback.setMediaController(mc);
            videoBox.addView(fallback);

            fallback.setVideoURI(Uri.parse(urls[qualityIdx]));
            fallback.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(android.media.MediaPlayer mp) {
                    fbPlayer = mp;
                    spinner.setVisibility(View.GONE);
                    bufferHint.setVisibility(View.GONE);
                    if (startPos > 3000) fallback.seekTo((int) startPos);
                    fallback.start();
                }
            });
            fallback.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                @Override public boolean onError(android.media.MediaPlayer mp, int what, int extra) {
                    if (qualityIdx + 1 < urls.length) {
                        qualityIdx++;
                        startFallback("خطأ — جودة أقل");
                    } else {
                        showError("تعذّر تشغيل الفيديو");
                    }
                    return true;
                }
            });
            ui.post(ticker);
        } catch (Throwable t) {
            showError("تعذّر التشغيل: " + t.getClass().getSimpleName());
        }
    }

    private void showError(String msg) {
        spinner.setVisibility(View.GONE);
        bufferHint.setVisibility(View.GONE);
        errorPanel.removeAllViews();
        errorPanel.setVisibility(View.VISIBLE);

        TextView t = Ui.text(this, "⚠️ " + msg, 13, 0xFFEDE3C8, true);
        t.setGravity(Gravity.CENTER);
        errorPanel.addView(t);

        TextView hint = Ui.text(this, "جرّب جودة أقل أو افتح الرابط في مشغّل خارجي",
                11, 0xFF9A9382, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 12));
        errorPanel.addView(hint);

        TextView retry = Ui.button(this, "إعادة المحاولة", true);
        retry.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                errorPanel.setVisibility(View.GONE);
                errors = 0;
                startExo();
            }
        });
        errorPanel.addView(retry);

        TextView ext = Ui.small(this, "فتح في مشغّل خارجي", 0x33FFFFFF, Color.WHITE);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, 0);
        ext.setLayoutParams(lp);
        ext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openExternal(); }
        });
        errorPanel.addView(ext);

        TextView copy = Ui.small(this, "نسخ الرابط", 0x33FFFFFF, Color.WHITE);
        LinearLayout.LayoutParams lp2 = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.setMargins(0, Ui.dp(this, 8), 0, 0);
        copy.setLayoutParams(lp2);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", urls[qualityIdx]));
                    Toast.makeText(PlayerActivity.this, "تم النسخ", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) { }
            }
        });
        errorPanel.addView(copy);
    }

    private void onEnded() {
        WatchStats.markFinished(this);
        savePosition(0);
        finish();
    }

    private void openExternal() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls[qualityIdx])));
        } catch (Throwable t) {
            Toast.makeText(this, "لا يوجد مشغّل خارجي", Toast.LENGTH_SHORT).show();
        }
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
            b.setAspectRatio(new Rational(16, 9));
            enterPictureInPictureMode(b.build());
            pip = true;
        } catch (Throwable ignored) { }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(inPip, cfg);
        pip = inPip;
        controls.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }

    /* ═══════════ الاستكمال والحفظ ═══════════ */

    private SharedPreferences prefs() {
        return getSharedPreferences("movieflix_play", Context.MODE_PRIVATE);
    }

    private String posKey() {
        return "pos_" + contentId + "_" + episodeId;
    }

    private long loadPosition() {
        try {
            return prefs().getLong(posKey(), 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private void savePosition(long pos) {
        try {
            if (pos <= 5000) prefs().edit().remove(posKey()).apply();
            else prefs().edit().putLong(posKey(), pos).apply();
            long dur = 0;
            try {
                if (usingExo && player != null) dur = player.getDuration();
                else if (fallback != null) dur = fallback.getDuration();
            } catch (Throwable ignored) {}
            WatchStats.touch(this, contentId, episodeId, titleView != null ? titleView.getText().toString() : "", "", pos, dur);
            WatchStats.addWatchTime(this, 5000);
        } catch (Throwable ignored) { }
    }

    private long currentPos() {
        try {
            if (usingExo && player != null) return player.getCurrentPosition();
            if (fallback != null) return fallback.getCurrentPosition();
        } catch (Throwable ignored) { }
        return 0;
    }

    /* ═══════════ دورة الحياة ═══════════ */

    @Override
    protected void onPause() {
        super.onPause();
        savePosition(currentPos());
        try {
            if (usingExo && player != null) player.pause();
            else if (fallback != null) fallback.pause();
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (usingExo && player != null) player.play();
            else if (fallback != null) fallback.start();
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onStop() {
        super.onStop();
        savePosition(currentPos());
        // حفظ في سجل المشاهدة على الخادم
        try {
            final long pos = currentPos() / 1000;
            if (pos > 5 && contentId != null && episodeId != null) {
                final HitvClient c = new HitvClient(this);
                new Thread(new Runnable() {
                    @Override public void run() {
                        try { c.addHistory(contentId, episodeId, epNo, (int) pos, 0, category); }
                        catch (Throwable ignored) { }
                    }
                }).start();
            }
        } catch (Throwable ignored) { }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            if (fallback != null) fallback.stopPlayback();
        } catch (Throwable ignored) { }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (pip) {
            super.onBackPressed();
            return;
        }
        savePosition(currentPos());
        finish();
    }

    private static String fmt(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }
}
