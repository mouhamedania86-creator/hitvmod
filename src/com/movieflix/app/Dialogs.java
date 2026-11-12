package com.movieflix.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

/** نافذة التفاصيل + نافذة الجودات/الروابط مع تشغيل داخلي. */
public class Dialogs {

    /** آخر قائمة حلقات عُرضت — تُمرَّر للمشغّل للتنقل بين الحلقات. */
    public static java.util.List<Model.Episode> EPISODES = new java.util.ArrayList<Model.Episode>();

    private static int dp(Context c, float v) { return Ui.dp(c, v); }

    private static Dialog base(Activity a, View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            w.getDecorView().setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        }
        return d;
    }

    private static ScrollView scroller(Activity a) {
        int h = a.getResources().getDisplayMetrics().heightPixels;
        ScrollView sv = new ScrollView(a);
        sv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (h * 0.88f)));
        return sv;
    }

    private static LinearLayout panel(Activity a) {
        LinearLayout p = Ui.column(a);
        p.setBackground(Ui.roundStroke(0xFF141414, 20, Ui.STROKE_2, a));
        if (I18n.rtl(a)) p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return p;
    }

    /* ═══════ التفاصيل ═══════ */

    public static void showDetail(final Activity a, final HitvClient client, final Model.Item item) {
        final Dialog[] holder = new Dialog[1];
        ScrollView sv = scroller(a);
        final LinearLayout panel = panel(a);
        sv.addView(panel);
        final Dialog d = base(a, sv);
        holder[0] = d;

        // غلاف + تعتيم متدرّج
        FrameLayout cover = new FrameLayout(a);
        cover.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 190)));

        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        String coverUrl = item.backdrop != null && item.backdrop.length() > 0 ? item.backdrop : item.poster;
        if (coverUrl != null && coverUrl.length() > 0) Ui.image(img, coverUrl, dp(a, 240));
        else img.setBackground(Ui.gradient(0xFF2E2412, 0xFF141414, 0, false, a));

        View shade = new View(a);
        shade.setBackground(Ui.gradient(0x00000000, 0xFF141414, 0, false, a));
        cover.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = Ui.text(a, "✕", 14, Color.WHITE, true);
        close.setPadding(dp(a, 10), dp(a, 4), dp(a, 10), dp(a, 4));
        close.setBackground(Ui.round(0x66000000, 10, a));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(a, 12), dp(a, 12), 0);
        close.setLayoutParams(clp);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        cover.addView(close);

        LinearLayout titleBox = Ui.column(a);
        titleBox.setPadding(dp(a, 16), 0, dp(a, 16), dp(a, 10));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        titleBox.setLayoutParams(tlp);

        TextView title = Ui.text(a, item.title, 20, Color.WHITE, true);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(title);

        LinearLayout metaRow = Ui.row(a);
        metaRow.setPadding(0, dp(a, 6), 0, 0);
        if (item.score.length() > 0) metaRow.addView(Ui.pill(a, "★ " + item.score, Ui.GOLD, 0xFF1A1A1A));
        if (item.type.length() > 0) {
            TextView p = Ui.pill(a, item.type, 0x33E50914, 0xFFFFFFFF);
            LinearLayout.LayoutParams pp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.setMargins(dp(a, 6), 0, 0, 0);
            p.setLayoutParams(pp);
            metaRow.addView(p);
        }
        if (item.year.length() > 0) {
            TextView p = Ui.pill(a, item.year, 0x33FFFFFF, Ui.TEXT);
            LinearLayout.LayoutParams pp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.setMargins(dp(a, 6), 0, 0, 0);
            p.setLayoutParams(pp);
            metaRow.addView(p);
        }
        titleBox.addView(metaRow);
        cover.addView(titleBox);
        panel.addView(cover);

        LinearLayout body = Ui.column(a);
        body.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 18));

        final TextView comments = Ui.text(a, "", 10.5f, Ui.MUTED_2, false);
        comments.setPadding(0, 0, 0, dp(a, 6));
        body.addView(comments);

        if (item.desc != null && item.desc.length() > 0) {
            TextView desc = Ui.text(a, item.desc, 12, 0xFFB3B3B3, false);
            desc.setMaxLines(5);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            desc.setPadding(0, dp(a, 4), 0, 0);
            body.addView(desc);
        }

        LinearLayout actions = Ui.row(a);
        actions.setPadding(0, dp(a, 14), 0, 0);
        final TextView watch = Ui.button(a, "▶  " + I18n.t(a, "play_info"), true);
        watch.setTextSize(12.5f);
        watch.setPadding(dp(a, 16), dp(a, 9), dp(a, 16), dp(a, 9));
        LinearLayout.LayoutParams wp = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.setMargins(0, 0, dp(a, 8), 0);
        watch.setLayoutParams(wp);
        actions.addView(watch);

        TextView dl = Ui.button(a, "⤓", false);
        dl.setTextSize(13);
        dl.setPadding(dp(a, 16), dp(a, 9), dp(a, 16), dp(a, 9));
        actions.addView(dl);

        final TextView listBtn = Ui.button(a, MyList.contains(a, item.id) ? "✓ List" : "+ List", false);
        listBtn.setTextSize(12);
        listBtn.setPadding(dp(a, 12), dp(a, 9), dp(a, 12), dp(a, 9));
        LinearLayout.LayoutParams lpList = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpList.setMargins(dp(a, 8), 0, 0, 0);
        listBtn.setLayoutParams(lpList);
        listBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleMyList(a, item);
                listBtn.setText(MyList.contains(a, item.id) ? "✓ List" : "+ List");
            }
        });
        actions.addView(listBtn);
        body.addView(actions);

        body.addView(Ui.spacer(a, 16));
        body.addView(Ui.text(a, I18n.t(a, "episodes"), 12.5f, Ui.TEXT, true));

        final LinearLayout epsBox = Ui.column(a);
        epsBox.setPadding(0, dp(a, 8), 0, 0);
        epsBox.addView(Ui.text(a, I18n.t(a, "loading"), 11.5f, Ui.MUTED_2, false));
        body.addView(epsBox);
        panel.addView(body);
        d.show();

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = client.detail(item.id, item.category);
                    List<Model.Episode> eps = Model.episodes(payload);
                    if (eps.isEmpty()) {
                        try {
                            JSONObject alt = client.detail(item.id, item.category == 0 ? 1 : 0);
                            List<Model.Episode> eps2 = Model.episodes(alt);
                            if (!eps2.isEmpty()) { payload = alt; eps = eps2; }
                        } catch (Throwable ignored) { }
                    }
                    final List<Model.Episode> epsF = eps;
                    String c = "";
                    try { c = Model.parseCommentNum(client.commentNum(item.id)); } catch (Throwable ignored) { }
                    final String commentTxt = c.length() > 0 ? "💬 " + c : "";
                    final String raw = client.isDebug() ? payload.toString() : null;

                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            comments.setText(commentTxt);
                            if (epsF.isEmpty()) {
                                epsBox.removeAllViews();
                                epsBox.addView(Ui.text(a, I18n.t(a, "no_episodes"), 11, Ui.MUTED_2, false));
                                if (raw != null) epsBox.addView(rawBox(a, raw));
                            } else {
                                renderEpisodes(a, client, item, epsBox, epsF);
                            }
                            final Model.Episode first = epsF.isEmpty() ? null : epsF.get(0);
                            watch.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    showPlay(a, client, item, first);
                                }
                            });
                            dl.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    showLinks(a, client, item, first);
                                }
                            });
                        }
                    });
                } catch (final Throwable e) {
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            epsBox.removeAllViews();
                            epsBox.addView(Ui.text(a, I18n.t(a, "error") + ": " + msg(e), 11, Ui.DANGER, false));
                        }
                    });
                }
            }
        }).start();
    }

    private static TextView rawBox(Activity a, String raw) {
        TextView r = Ui.text(a, I18n.t(a, "raw") + "\n" + raw, 8.5f, 0xFF8FA3C0, false);
        r.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        r.setBackground(Ui.round(0xFF000000, 10, a));
        return r;
    }

    private static void renderEpisodes(final Activity a, final HitvClient client, final Model.Item item,
                                       LinearLayout box, final List<Model.Episode> eps) {
        EPISODES = eps;
        box.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < eps.size(); i++) {
            final Model.Episode ep = eps.get(i);
            if (i % 5 == 0) {
                row = Ui.row(a);
                row.setPadding(0, 0, 0, dp(a, 6));
                box.addView(row);
            }
            TextView b = Ui.text(a, ep.index, 12, Ui.TEXT, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, dp(a, 10), 0, dp(a, 10));
            b.setBackground(Ui.roundStroke(Ui.PANEL_2, 10, Ui.STROKE, a));
            LinearLayout.LayoutParams p = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(0, 0, dp(a, 6), 0);
            b.setLayoutParams(p);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showPlay(a, client, item, ep); }
            });
            if (row != null) row.addView(b);
        }
    }

    /* ═══════ الجودات + التشغيل ═══════ */

    public static void showPlay(final Activity a, final HitvClient client, final Model.Item item,
                                final Model.Episode ep) {
        final Dialog[] holder = new Dialog[1];
        ScrollView sv = scroller(a);
        LinearLayout panel = panel(a);
        sv.addView(panel);
        final Dialog d = base(a, sv);
        holder[0] = d;

        panel.addView(headRow(a, I18n.t(a, "links"), holder));

        LinearLayout body = Ui.column(a);
        body.setPadding(dp(a, 16), 0, dp(a, 16), dp(a, 18));

        String sub = item.title;
        if (ep != null && ep.index != null && ep.index.length() > 0)
            sub += "  ·  " + I18n.t(a, "episode") + " " + ep.index;
        body.addView(Ui.text(a, sub, 11.5f, Ui.MUTED, false));
        body.addView(Ui.spacer(a, 8));

        final LinearLayout list = Ui.column(a);
        list.addView(Ui.text(a, I18n.t(a, "loading"), 11.5f, Ui.MUTED_2, false));
        body.addView(list);
        panel.addView(body);
        d.show();

        final String episodeId = ep != null && ep.id != null && ep.id.length() > 0 ? ep.id : item.id;
        final int epNo = parseNum(ep == null ? "" : ep.index);

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = client.preview(item.id, episodeId, item.category, "GROOT_LD");
                    final List<Model.Link> links = Model.links(payload);
                    final String raw = client.isDebug() ? payload.toString() : null;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            renderPlay(a, client, item, list, links, raw, episodeId, epNo, d);
                        }
                    });
                } catch (final Throwable e) {
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            list.removeAllViews();
                            list.addView(Ui.text(a, I18n.t(a, "error") + ": " + msg(e), 11, Ui.DANGER, false));
                        }
                    });
                }
            }
        }).start();
    }

    private static void renderPlay(final Activity a, final HitvClient client, final Model.Item item,
                                   LinearLayout list, final List<Model.Link> links, String raw,
                                   final String episodeId, final int epNo, final Dialog d) {
        list.removeAllViews();
        if (links == null || links.isEmpty()) {
            list.addView(Ui.text(a, I18n.t(a, "no_links"), 11, Ui.MUTED_2, false));
            if (raw != null) list.addView(rawBox(a, raw));
            return;
        }
        final Model.Link best = pick(links);
        TextView big = Ui.button(a, "▶  " + I18n.t(a, "watch_now"), true);
        big.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        final int bestIdx = links.indexOf(best);
        big.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                play(a, item, links, Math.max(0, bestIdx), episodeId, epNo);
            }
        });
        list.addView(big);
        list.addView(Ui.spacer(a, 10));

        for (int li = 0; li < links.size(); li++) {
            final Model.Link l = links.get(li);
            final int linkIndex = li;
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.roundStroke(Ui.PANEL, 12, Ui.STROKE, a));
            row.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
            LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, 0, 0, dp(a, 8));
            row.setLayoutParams(rp);

            row.addView(Ui.pill(a, l.label, 0x33E50914, 0xFFFFFFFF));

            TextView url = Ui.text(a, l.url, 9, Ui.MUTED, false);
            url.setMaxLines(1);
            url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            LinearLayout.LayoutParams up = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            up.setMargins(dp(a, 8), 0, dp(a, 8), 0);
            url.setLayoutParams(up);
            row.addView(url);

            TextView playB = Ui.small(a, "▶", 0x33E50914, 0xFFFFFFFF);
            playB.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    d.dismiss();
                    play(a, item, links, linkIndex, episodeId, epNo);
                }
            });
            row.addView(playB);

            TextView copy = Ui.small(a, I18n.t(a, "copy"), 0x33FFFFFF, Ui.TEXT);
            LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(a, 6), 0, 0, 0);
            copy.setLayoutParams(cp);
            copy.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { copyUrl(a, l.url); }
            });
            row.addView(copy);
            list.addView(row);
        }
        if (raw != null) list.addView(rawBox(a, raw));
    }

    /* ═══════ التنزيل ═══════ */

    public static void showLinks(final Activity a, final HitvClient client, final Model.Item item,
                                 final Model.Episode ep) {
        final Dialog[] holder = new Dialog[1];
        ScrollView sv = scroller(a);
        LinearLayout panel = panel(a);
        sv.addView(panel);
        final Dialog d = base(a, sv);
        holder[0] = d;

        panel.addView(headRow(a, I18n.t(a, "dl_links"), holder));

        LinearLayout body = Ui.column(a);
        body.setPadding(dp(a, 16), 0, dp(a, 16), dp(a, 18));
        body.addView(Ui.text(a, item.title, 11.5f, Ui.MUTED, false));
        final LinearLayout list = Ui.column(a);
        list.setPadding(0, dp(a, 8), 0, 0);
        list.addView(Ui.text(a, I18n.t(a, "loading"), 11.5f, Ui.MUTED_2, false));
        body.addView(list);
        panel.addView(body);
        d.show();

        final String episodeId = ep != null && ep.id != null && ep.id.length() > 0 ? ep.id : item.id;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject payload = client.downloadInfo(item.id, episodeId, "");
                    final List<Model.Link> links = Model.links(payload);
                    final String raw = client.isDebug() ? payload.toString() : null;
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            list.removeAllViews();
                            if (links.isEmpty()) {
                                list.addView(Ui.text(a, I18n.t(a, "no_links"), 11, Ui.MUTED_2, false));
                                if (raw != null) list.addView(rawBox(a, raw));
                                return;
                            }
                            for (final Model.Link l : links) {
                                LinearLayout row = Ui.row(a);
                                row.setBackground(Ui.roundStroke(Ui.PANEL, 12, Ui.STROKE, a));
                                row.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
                                LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);
                                rp.setMargins(0, 0, 0, dp(a, 8));
                                row.setLayoutParams(rp);
                                row.addView(Ui.pill(a, l.label, 0x33E50914, 0xFFFFFFFF));
                                TextView url = Ui.text(a, l.url, 9, Ui.MUTED, false);
                                url.setMaxLines(1);
                                url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                                LinearLayout.LayoutParams up = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                                up.setMargins(dp(a, 8), 0, dp(a, 8), 0);
                                url.setLayoutParams(up);
                                row.addView(url);
                                TextView open = Ui.small(a, I18n.t(a, "open"), 0x33E50914, 0xFFFFFFFF);
                                open.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        try {
                                            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(l.url)));
                                        } catch (Throwable t) {
                                            Toast.makeText(a, "لا يوجد تطبيق", Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                });
                                row.addView(open);
                                TextView offlineBtn = Ui.small(a, "⬇", 0x33E50914, 0xFFFFFFFF);
                                offlineBtn.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) {
                                        saveOffline(a, item, ep, l);
                                    }
                                });
                                row.addView(offlineBtn);
                                TextView copy = Ui.small(a, I18n.t(a, "copy"), 0x33FFFFFF, Ui.TEXT);
                                LinearLayout.LayoutParams cp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT);
                                cp.setMargins(dp(a, 6), 0, 0, 0);
                                copy.setLayoutParams(cp);
                                copy.setOnClickListener(new View.OnClickListener() {
                                    @Override public void onClick(View v) { copyUrl(a, l.url); }
                                });
                                row.addView(copy);
                                list.addView(row);
                            }
                            if (raw != null) list.addView(rawBox(a, raw));
                        }
                    });
                } catch (final Throwable e) {
                    a.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            list.removeAllViews();
                            list.addView(Ui.text(a, I18n.t(a, "error") + ": " + msg(e), 11, Ui.DANGER, false));
                        }
                    });
                }
            }
        }).start();
    }

    /* ═══════ أدوات ═══════ */

    private static void play(Activity a, Model.Item item, List<Model.Link> links,
                             int index, String episodeId, int epNo) {
        try {
            String[] urls = new String[links.size()];
            String[] labels = new String[links.size()];
            for (int i = 0; i < links.size(); i++) {
                urls[i] = links.get(i).url;
                labels[i] = links.get(i).label;
            }
            Intent i = new Intent(a, PlayerActivity.class);
            i.putExtra(PlayerActivity.EXTRA_URLS, urls);
            i.putExtra(PlayerActivity.EXTRA_LABELS, labels);
            i.putExtra(PlayerActivity.EXTRA_INDEX, Math.max(0, index));
            i.putExtra(PlayerActivity.EXTRA_TITLE, item.title);
            i.putExtra(PlayerActivity.EXTRA_POSTER, item.poster);
            i.putExtra(PlayerActivity.EXTRA_CONTENT, item.id);
            i.putExtra(PlayerActivity.EXTRA_EPISODE, episodeId);
            i.putExtra(PlayerActivity.EXTRA_EPNO, epNo);
            i.putExtra(PlayerActivity.EXTRA_CATEGORY, item.category);
            if (EPISODES != null && !EPISODES.isEmpty()) {
                String[] ids = new String[EPISODES.size()];
                String[] idxs = new String[EPISODES.size()];
                for (int k = 0; k < EPISODES.size(); k++) {
                    ids[k] = EPISODES.get(k).id;
                    idxs[k] = EPISODES.get(k).index;
                }
                i.putExtra("epIds", ids);
                i.putExtra("epIndexes", idxs);
                int pos = 0;
                for (int k = 0; k < EPISODES.size(); k++) {
                    if (EPISODES.get(k).id != null && EPISODES.get(k).id.equals(episodeId)) { pos = k; break; }
                }
                i.putExtra("epPos", pos);
            }
            a.startActivity(i);
        } catch (Throwable t) {
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(links.get(index).url)));
            } catch (Throwable t2) {
                Toast.makeText(a, "لا يوجد مشغل", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static Model.Link pick(List<Model.Link> links) {
        Model.Link best = links.get(0);
        for (Model.Link l : links) {
            String u = l.url.toLowerCase();
            if (u.contains("m3u8")) return l;
        }
        return best;
    }

    private static void copyUrl(Activity a, String url) {
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", url));
            Toast.makeText(a, I18n.t(a, "copied"), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
    }

    private static int parseNum(String s) {
        try {
            String d = s.replaceAll("[^0-9]", "");
            return d.length() == 0 ? 1 : Integer.parseInt(d);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static LinearLayout headRow(Activity a, String title, final Dialog[] holder) {
        LinearLayout row = Ui.row(a);
        row.setPadding(dp(a, 16), dp(a, 14), dp(a, 12), dp(a, 8));
        TextView t = Ui.text(a, title, 14, Ui.TEXT, true);
        t.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t);
        TextView close = Ui.text(a, "✕", 14, Ui.MUTED, true);
        close.setPadding(dp(a, 10), dp(a, 4), dp(a, 10), dp(a, 4));
        close.setBackground(Ui.round(0x22FFFFFF, 10, a));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (holder != null && holder[0] != null) holder[0].dismiss();
            }
        });
        row.addView(close);
        return row;
    }

    private static String msg(Throwable t) {
        String m = t == null ? null : t.getMessage();
        if (m == null || m.trim().length() == 0) m = t == null ? "?" : t.getClass().getSimpleName();
        return m;
    }

    /* ── حفظ أوفلاين ── */
    private static void saveOffline(final Activity a, final Model.Item item, final Model.Episode ep, final Model.Link link) {
        final String epId = ep != null && ep.id != null ? ep.id : "";
        final String title = item.title + (ep != null && ep.name != null && ep.name.length() > 0 ? " · " + ep.name : "");
        Toast.makeText(a, I18n.t(a, "download_start"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                final String path = OfflineStore.downloadUrl(a, link.url, item.id, epId, title, item.poster);
                a.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (path != null) {
                            Toast.makeText(a, I18n.t(a, "download_done"), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(a, I18n.t(a, "error"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }


    public static void toggleMyList(Activity a, Model.Item item) {
        MyList.toggle(a, item);
        boolean on = MyList.contains(a, item.id);
        Toast.makeText(a, on ? "+ My List" : "− My List", Toast.LENGTH_SHORT).show();
    }

}
