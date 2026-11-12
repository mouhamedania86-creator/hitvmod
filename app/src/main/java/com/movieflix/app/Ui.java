package com.movieflix.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** التصميم + أدوات الواجهة + محمّل صور سريع (ذاكرة + قرص + 6 خيوط). */
public class Ui {

    /* Netflix-inspired palette: pure black + blood red */
    public static final int BG = 0xFF0B0B0B, BG_2 = 0xFF141414;
    public static final int PANEL = 0xFF1A1A1A, PANEL_2 = 0xFF221F1F, PANEL_3 = 0xFF2A2A2A;
    public static final int STROKE = 0x22FFFFFF, STROKE_2 = 0x40FFFFFF;
    public static final int TEXT = 0xFFFFFFFF, MUTED = 0xFFB3B3B3, MUTED_2 = 0xFF808080;
    public static final int BRAND = 0xFFE50914, BRAND2 = 0xFFB20710, PINK = 0xFFFF6B6B;
    public static final int GOLD_L = 0xFFFF8A8A;
    public static final int GOLD = 0xFFE50914, DANGER = 0xFFE50914, OK = 0xFF46D369;

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable roundStroke(int color, float radiusDp, int stroke, Context c) {
        GradientDrawable d = round(color, radiusDp, c);
        d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    public static GradientDrawable gradient(int from, int to, float radiusDp, boolean horizontal, Context c) {
        GradientDrawable d = new GradientDrawable(
                horizontal ? GradientDrawable.Orientation.LEFT_RIGHT : GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{from, to});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static GradientDrawable triGradient(int a, int b, int c3, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a, b, c3});
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s == null ? "" : s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView pill(Context c, String s, int bg, int fg) {
        TextView t = text(c, s, 10.5f, fg, true);
        t.setBackground(round(bg, 8, c));
        t.setPadding(dp(c, 7), dp(c, 3), dp(c, 7), dp(c, 3));
        return t;
    }

    public static TextView button(Context c, String label, boolean primary) {
        TextView b = text(c, label, 13.5f, primary ? Color.WHITE : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 16), dp(c, 11), dp(c, 16), dp(c, 11));
        b.setBackground(primary ? gradient(BRAND, BRAND2, 12, true, c) : roundStroke(PANEL_2, 12, STROKE_2, c));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static TextView small(Context c, String label, int bg, int fg) {
        TextView b = text(c, label, 11.5f, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 11), dp(c, 6), dp(c, 11), dp(c, 6));
        b.setBackground(round(bg, 9, c));
        b.setClickable(true);
        return b;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpm(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(lp(1, dp(c, h)));
        return v;
    }

    /* ══════════ محمّل الصور السريع ══════════ */

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(80);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int MAX_FILES = 500;

    private static String key(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    private static File cacheFile(Context c, String url) {
        File dir = new File(c.getCacheDir(), "img");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, key(url) + ".jpg");
    }

    public static void image(final ImageView view, final String url, final int px) {
        if (view == null || url == null || url.length() == 0) return;
        Bitmap mem = MEM.get(url);
        if (mem != null && !mem.isRecycled()) { view.setImageBitmap(mem); return; }
        view.setTag(url);
        POOL.execute(new Runnable() {
            @Override public void run() {
                Context c = view.getContext();
                Bitmap bmp = null;
                File f = cacheFile(c, url);
                if (f.exists()) {
                    bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
                }
                if (bmp == null) {
                    bmp = download(url, px);
                    if (bmp != null) save(f, bmp);
                }
                if (bmp == null) return;
                MEM.put(url, bmp);
                final Bitmap out = bmp;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        if (url.equals(view.getTag())) {
                            view.setAlpha(0f);
                            view.setImageBitmap(out);
                            view.animate().alpha(1f).setDuration(160).start();
                        }
                    }
                });
            }
        });
    }

    private static void save(File f, Bitmap bmp) {
        try {
            FileOutputStream fo = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, fo);
            fo.close();
            File dir = f.getParentFile();
            File[] files = dir.listFiles();
            if (files != null && files.length > MAX_FILES) {
                Arrays.sort(files, new Comparator<File>() {
                    @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
                });
                for (int i = 0; i < files.length / 2; i++) files[i].delete();
            }
        } catch (Throwable ignored) { }
    }

    /** طلب واحد فقط: نقرأ البايتات مرة واحدة ثم نفكّ الترميز مرتين من الذاكرة. */
    private static Bitmap download(String url, int targetPx) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(12000);
            con.setReadTimeout(18000);
            con.setRequestProperty("user-agent", "okhttp/4.12.0");
            con.setRequestProperty("accept", "image/*,*/*");
            con.setInstanceFollowRedirects(true);
            if (con.getResponseCode() < 200 || con.getResponseCode() >= 300) return null;

            InputStream is = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            try { is.close(); } catch (Exception ignored) { }
            byte[] data = bo.toByteArray();
            if (data.length == 0) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            int w = bounds.outWidth > 0 ? bounds.outWidth : targetPx;
            while (w / sample > Math.max(200, targetPx) * 2) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    /** تسخين مسبق للصور القادمة */
    public static void prefetch(final Context c, final String url, final int px) {
        if (url == null || url.length() == 0 || MEM.get(url) != null) return;
        POOL.execute(new Runnable() {
            @Override public void run() {
                File f = cacheFile(c, url);
                if (f.exists()) { MEM.put(url, BitmapFactory.decodeFile(f.getAbsolutePath())); return; }
                Bitmap bmp = download(url, px);
                if (bmp != null) { save(f, bmp); MEM.put(url, bmp); }
            }
        });
    }
}
