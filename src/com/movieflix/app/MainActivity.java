package com.movieflix.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** الواجهة الرئيسية — تصميم داكن احترافي، Java أصلية. */
public class MainActivity extends Activity {

    private HitvClient client;
    private Context ctx;

    private LinearLayout root, tabBar, homeBody, sectionBox, historyBox;
    private TextView[] tabs = new TextView[3];
    private FrameLayout content;

    private ScrollView homeScroll;
    private LinearLayout searchView, settingsView;
    private GridView searchGrid;
    private ItemAdapter searchAdapter;
    private TextView stateHome, stateSearch, statusDot, statusTxt, searchTitle;
    private TextView heroTitle, heroMeta, heroPlay, heroInfo;
    private ImageView heroImg;
    private EditText searchField, fBase, fDevice, fEmail, fPass;
    private CheckBox debugBox;
    private TextView accToken, accType, accVersion, accUser, accVip;
    private LinearLayout statBox;

    private FrameLayout shell;
    private LinearLayout drawer;
    private View scrim;
    private boolean drawerOpen = false;
    private Model.Item heroItem;
    private int tab = 0;
    private boolean searchLoaded = false;


    @Override
    protected void onResume() {
        super.onResume();
        if (historyBox != null) renderContinueWatching();
        if (statBox != null) refreshStatsUi(statBox);
    }

    /* ═══════════ إنشاء ═══════════ */

    @Override
    protected void onCreate(android.os.Bundle b) {
        super.onCreate(b);
        ctx = this;
        client = new HitvClient(this);

        root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        if (I18n.rtl(this)) root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildHeader());
        root.addView(content);
        root.addView(buildTabs());

        content.addView(buildHome());
        content.addView(buildSearch());
        content.addView(buildSettings());

        shell = new FrameLayout(this);
        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrim = new View(this);
        scrim.setBackgroundColor(0xCC000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(false); }
        });
        shell.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawer = buildDrawer();
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
                Math.min(Ui.dp(this, 300), (int) (getResources().getDisplayMetrics().widthPixels * 0.82f)),
                ViewGroup.LayoutParams.MATCH_PARENT,
                I18n.rtl(this) ? Gravity.END : Gravity.START);
        drawer.setLayoutParams(dp);
        drawer.setVisibility(View.GONE);
        shell.addView(drawer);

        setContentView(shell);
        showTab(0);
        autoLogin();
    }

    private LinearLayout buildHeader() {
        LinearLayout h = Ui.column(this);
        h.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        h.setBackgroundColor(0xFF0B0B0B);

        LinearLayout row = Ui.row(this);
        TextView menuBtn = Ui.text(this, "\u2630", 19, Ui.BRAND, true);
        menuBtn.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 10), 0);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(!drawerOpen); }
        });
        row.addView(menuBtn);

        TextView logo = Ui.text(this, "M", 18, 0xFFFFFFFF, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.round(Ui.BRAND, 6, this));
        logo.setLayoutParams(Ui.lp(Ui.dp(this, 38), Ui.dp(this, 38)));
        row.addView(logo);

        LinearLayout titles = Ui.column(this);
        LinearLayout.LayoutParams tp = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 6), 0);
        titles.setLayoutParams(tp);
        titles.addView(Ui.text(this, I18n.t(this, "app"), 15, Ui.TEXT, true));
        titles.addView(Ui.text(this, I18n.t(this, "tagline"), 9.5f, Ui.MUTED_2, false));
        row.addView(titles);

        statusDot = Ui.text(this, "●", 12, Ui.MUTED_2, true);
        row.addView(statusDot);
        statusTxt = Ui.text(this, "", 10, Ui.MUTED, false);
        LinearLayout.LayoutParams stp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 8), 0);
        statusTxt.setLayoutParams(stp);
        row.addView(statusTxt);

        TextView langBtn = Ui.small(this, I18n.LABELS[I18n.index(this)] + " ▾", Ui.PANEL_2, Ui.TEXT);
        langBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { languageDialog(); }
        });
        row.addView(langBtn);
        h.addView(row);

        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.roundStroke(Ui.PANEL, 24, Ui.STROKE, this));
        box.setPadding(Ui.dp(this, 15), 0, Ui.dp(this, 8), 0);
        LinearLayout.LayoutParams bp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        bp.setMargins(0, Ui.dp(this, 10), 0, 0);
        box.setLayoutParams(bp);

        searchField = new EditText(this);
        searchField.setHint(I18n.t(this, "search_hint"));
        searchField.setHintTextColor(Ui.MUTED_2);
        searchField.setTextColor(Ui.TEXT);
        searchField.setTextSize(13);
        searchField.setSingleLine(true);
        searchField.setBackgroundColor(Color.TRANSPARENT);
        searchField.setInputType(InputType.TYPE_CLASS_TEXT);
        searchField.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        box.addView(searchField);

        TextView go = Ui.text(this, "⌕", 20, Ui.BRAND, true);
        go.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String q = searchField.getText().toString().trim();
                if (q.length() > 0) doSearch(q);
            }
        });
        box.addView(go);
        searchField.addTextChangedListener(new TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable pending;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (pending != null) h.removeCallbacks(pending);
                final String q = s == null ? "" : s.toString().trim();
                pending = new Runnable() {
                    @Override public void run() {
                        if (q.length() == 0) {
                            loadDiscovery();
                        } else if (q.length() >= 1) {
                            showTab(1);
                            doSearch(q);
                        }
                    }
                };
                h.postDelayed(pending, 380);
            }
        });
        h.addView(box);
        return h;
    }

    private void toggleDrawer(boolean open) {
        drawerOpen = open;
        drawer.setVisibility(open ? View.VISIBLE : View.GONE);
        scrim.setVisibility(open ? View.VISIBLE : View.GONE);
        float from = I18n.rtl(this) ? 1f : -1f;
        drawer.setTranslationX(open ? 0 : from * Ui.dp(this, 320));
        if (open) drawer.animate().translationX(0).setDuration(220).start();
    }

    private LinearLayout buildDrawer() {
        LinearLayout d = Ui.column(this);
        d.setBackground(Ui.round(0xFF0B0B0B, 0, this));
        if (I18n.rtl(this)) d.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        d.setPadding(Ui.dp(this, 16), Ui.dp(this, 22), Ui.dp(this, 16), Ui.dp(this, 18));

        LinearLayout head = Ui.row(this);
        TextView logo = Ui.text(this, "M", 20, 0xFFFFFFFF, true);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.round(Ui.BRAND, 6, this));
        logo.setLayoutParams(Ui.lp(Ui.dp(this, 46), Ui.dp(this, 46)));
        head.addView(logo);

        LinearLayout names = Ui.column(this);
        LinearLayout.LayoutParams np = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        np.setMargins(Ui.dp(this, 10), 0, 0, 0);
        names.setLayoutParams(np);
        names.addView(Ui.text(this, "MovieFlix", 16, Ui.BRAND, true));
        names.addView(Ui.text(this, "نسخة احترافية · v4.0", 10, Ui.MUTED_2, false));
        head.addView(names);

        TextView close = Ui.text(this, "✕", 14, Ui.MUTED, true);
        close.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        close.setBackground(Ui.round(0x22FFFFFF, 9, this));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleDrawer(false); }
        });
        head.addView(close);
        d.addView(head);

        d.addView(Ui.spacer(this, 16));

        LinearLayout dev = Ui.column(this);
        dev.setBackground(Ui.roundStroke(0x1FE50914, 16, 0x55E50914, this));
        dev.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        dev.addView(Ui.text(this, "حقوق المطوّر", 11, Ui.MUTED, true));
        TextView devName = Ui.text(this, "FaresCodeX", 19, Ui.BRAND, true);
        devName.setPadding(0, Ui.dp(this, 4), 0, 0);
        dev.addView(devName);
        TextView copy = Ui.text(this, "© 2026 MovieFlix — جميع الحقوق محفوظة", 9.5f, Ui.MUTED_2, false);
        copy.setPadding(0, Ui.dp(this, 4), 0, 0);
        dev.addView(copy);
        d.addView(dev);

        d.addView(Ui.spacer(this, 18));
        d.addView(Ui.text(this, "قنوات المطوّر", 11, Ui.MUTED, true));
        d.addView(Ui.spacer(this, 8));
        d.addView(drawerLink("⌘   GitHub · FaresCodeX", "https://github.com/FaresCodeX", 0x33E50914));
        d.addView(Ui.spacer(this, 8));
        d.addView(drawerLink("◆   FaresCodeX", "https://github.com/FaresCodeX", 0x33E50914));

        d.addView(Ui.spacer(this, 18));
        View divider = new View(this);
        divider.setBackgroundColor(0x33E50914);
        divider.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1))));
        d.addView(divider);
        d.addView(Ui.spacer(this, 14));

        TextView about = drawerLink("ℹ   عن التطبيق", "", 0x14FFFFFF);
        about.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("MovieFlix")
                        .setMessage("MovieFlix — تطبيق مشاهدة سينمائي بتجربة نتفليكس\n\nBy FaresCodeX\nالإصدار: 1.1.0\n© 2026 جميع الحقوق محفوظة\n\ngithub.com/FaresCodeX")
                        .setPositiveButton("تم", null).show();
            }
        });
        d.addView(about);

        TextView downloads = drawerLink("⬇   تنزيلاتي", "", 0x14FFFFFF);
        downloads.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleDrawer(false);
                showDownloads();
            }
        });
        d.addView(downloads);

        TextView mylist = drawerLink("+   قائمتي", "", 0x14FFFFFF);
        mylist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleDrawer(false);
                showMyList();
            }
        });
        d.addView(mylist);

        TextView settings = drawerLink("⚙   الإعدادات", "", 0x14FFFFFF);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                toggleDrawer(false);
                showTab(2);
            }
        });
        d.addView(settings);

        d.addView(Ui.spacer(this, 12));
        TextView foot = Ui.text(this, "By FaresCodeX", 9, Ui.MUTED_2, false);
        foot.setGravity(Gravity.CENTER);
        d.addView(foot);
        return d;
    }

    private TextView drawerLink(String label, final String url, int bg) {
        TextView t = Ui.text(this, label, 13, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        t.setBackground(Ui.roundStroke(bg, 12, 0x33E50914, this));
        t.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (url == null || url.length() == 0) return;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                } catch (Throwable e) {
                    Toast.makeText(MainActivity.this, url, Toast.LENGTH_LONG).show();
                }
            }
        });
        return t;
    }

    private LinearLayout buildTabs() {
        tabBar = Ui.row(this);
        tabBar.setBackgroundColor(0xFF0B0B0B);
        tabBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        String[] keys = {"tab_home", "tab_search", "tab_settings"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.text(this, I18n.t(this, keys[i]), 12, Ui.MUTED, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));
            LinearLayout.LayoutParams p = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            t.setLayoutParams(p);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showTab(idx); }
            });
            tabs[i] = t;
            tabBar.addView(t);
        }
        return tabBar;
    }

    private void showTab(int i) {
        tab = i;
        homeScroll.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        searchView.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(i == 2 ? View.VISIBLE : View.GONE);
        for (int k = 0; k < 3; k++) {
            boolean on = k == i;
            tabs[k].setTextColor(on ? Color.WHITE : Ui.MUTED);
            tabs[k].setBackground(on ? Ui.triGradient(0x77E50914, 0x55B20710, 0x33E50914, 12, this)
                    : Ui.round(0x00000000, 12, this));
        }
        if (i == 1 && !searchLoaded) { searchLoaded = true; loadDiscovery(); }
        if (i == 2) refreshAccount();
    }

    /* ═══════════ الرئيسية ═══════════ */

    private ScrollView buildHome() {
        homeScroll = new ScrollView(this);
        homeScroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        homeScroll.setFillViewport(true);

        homeBody = Ui.column(this);
        homeBody.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 24));

        // ═══ الغلاف البطل ═══
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.round(0xFF141414, 12, this));
        hero.setClipToOutline(true);
        hero.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 280)));

        heroImg = new ImageView(this);
        heroImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroImg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View shade = new View(this);
        shade.setBackground(Ui.gradient(0x00000000, 0xEE000000, 0, false, this));
        hero.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout heroBody = Ui.column(this);
        heroBody.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 14));
        FrameLayout.LayoutParams hb = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        heroBody.setLayoutParams(hb);

        TextView kicker = Ui.text(this, "—  " + I18n.t(this, "featured"), 9.5f, Ui.BRAND2, true);
        heroBody.addView(kicker);

        heroTitle = Ui.text(this, "…", 20, Color.WHITE, true);
        heroTitle.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 2));
        heroTitle.setMaxLines(1);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroBody.addView(heroTitle);

        heroMeta = Ui.text(this, "", 10.5f, 0xFFB3B3B3, false);
        heroMeta.setMaxLines(1);
        heroMeta.setEllipsize(TextUtils.TruncateAt.END);
        heroBody.addView(heroMeta);

        LinearLayout btns = Ui.row(this);
        btns.setPadding(0, Ui.dp(this, 10), 0, 0);
        heroPlay = Ui.button(this, "▶  " + I18n.t(this, "play_info"), true);
        heroPlay.setTextSize(12);
        heroPlay.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        heroPlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (heroItem != null) Dialogs.showDetail(MainActivity.this, client, heroItem);
            }
        });
        heroInfo = Ui.button(this, I18n.t(this, "details"), false);
        heroInfo.setTextSize(12);
        heroInfo.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        heroInfo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (heroItem != null) Dialogs.showDetail(MainActivity.this, client, heroItem);
            }
        });
        LinearLayout.LayoutParams hp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.setMargins(0, 0, Ui.dp(this, 8), 0);
        heroPlay.setLayoutParams(hp);
        btns.addView(heroPlay);
        btns.addView(heroInfo);
        heroBody.addView(btns);

        hero.addView(heroBody);
        homeBody.addView(hero);

        // ═══ متابعة المشاهدة ═══
        historyBox = Ui.column(this);
        homeBody.addView(historyBox);

        // ═══ عنوان + تحديث ═══
        LinearLayout secHead = Ui.row(this);
        secHead.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 2));
        View bar = new View(this);
        bar.setBackground(Ui.gradient(Ui.BRAND, Ui.BRAND2, 2, false, this));
        bar.setLayoutParams(Ui.lp(Ui.dp(this, 4), Ui.dp(this, 16)));
        secHead.addView(bar);
        TextView ttl = Ui.text(this, I18n.t(this, "discover"), 17f, Ui.TEXT, true);
        LinearLayout.LayoutParams tlp = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        ttl.setLayoutParams(tlp);
        secHead.addView(ttl);
        TextView refresh = Ui.small(this, "⟳  " + I18n.t(this, "refresh"), 0x33E50914, 0xFFFFFFFF);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { loadHome(); }
        });
        secHead.addView(refresh);
        homeBody.addView(secHead);

        stateHome = Ui.text(this, I18n.t(this, "loading"), 12.5f, Ui.MUTED, false);
        stateHome.setGravity(Gravity.CENTER);
        stateHome.setPadding(0, Ui.dp(this, 30), 0, Ui.dp(this, 20));
        homeBody.addView(stateHome);

        sectionBox = Ui.column(this);
        homeBody.addView(sectionBox);

        homeScroll.addView(homeBody);
        return homeScroll;
    }

    private void renderHero(Model.Item it) {
        heroItem = it;
        heroTitle.setText(it.title);
        StringBuilder m = new StringBuilder();
        if (it.score.length() > 0) m.append("★ ").append(it.score).append("   ");
        if (it.type.length() > 0) m.append(it.type).append("   ");
        if (it.year.length() > 0) m.append(it.year);
        if (it.episodes.length() > 0 && !"0".equals(it.episodes))
            m.append("   ").append(it.episodes).append(" ").append(I18n.t(this, "episode"));
        heroMeta.setText(m.toString().trim());
        String bg = it.backdrop != null && it.backdrop.length() > 0 ? it.backdrop : it.poster;
        if (bg != null && bg.length() > 0) {
            heroImg.setImageDrawable(null);
            Ui.image(heroImg, bg, Ui.dp(this, 260));
        }
    }

    private void renderSections(List<Model.Section> list) {
        sectionBox.removeAllViews();
        for (Model.Section s : list) {
            if (s.items.isEmpty()) continue;
            if (s.title != null && s.title.length() > 0) {
                LinearLayout sh = Ui.row(this);
                sh.setPadding(Ui.dp(this, 2), Ui.dp(this, 14), 0, Ui.dp(this, 6));
                View bar = new View(this);
                bar.setBackground(Ui.round(Ui.BRAND, 2, this));
                bar.setLayoutParams(Ui.lp(Ui.dp(this, 3), Ui.dp(this, 12)));
                sh.addView(bar);
                TextView st = Ui.text(this, s.title, 15f, Ui.TEXT, true);
                LinearLayout.LayoutParams sp = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                sp.setMargins(Ui.dp(this, 7), 0, 0, 0);
                st.setLayoutParams(sp);
                sh.addView(st);
                sectionBox.addView(sh);
            }
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout rowl = Ui.row(this);
            rowl.setPadding(0, 0, 0, Ui.dp(this, 2));
            for (Model.Item it : s.items) rowl.addView(card(it, Ui.dp(this, 124)));
            hs.addView(rowl);
            sectionBox.addView(hs);
            // تسخين مسبق لصور القسم التالي
            for (int i = 0; i < Math.min(4, s.items.size()); i++) {
                Model.Item it = s.items.get(i);
                String u = it.poster != null && it.poster.length() > 0 ? it.poster : it.backdrop;
                if (u != null) Ui.prefetch(this, u, Ui.dp(this, 150));
            }
        }
    }

    private void renderHistory(List<Model.Item> list) {
        historyBox.removeAllViews();
        if (list == null || list.isEmpty()) return;
        LinearLayout sh = Ui.row(this);
        sh.setPadding(Ui.dp(this, 2), Ui.dp(this, 16), 0, Ui.dp(this, 6));
        View bar = new View(this);
        bar.setBackground(Ui.gradient(Ui.PINK, 0x00F472B6, 2, false, this));
        bar.setLayoutParams(Ui.lp(Ui.dp(this, 3), Ui.dp(this, 12)));
        sh.addView(bar);
        TextView st = Ui.text(this, I18n.t(this, "continue"), 12.5f, Ui.TEXT, true);
        LinearLayout.LayoutParams sp = Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.setMargins(Ui.dp(this, 7), 0, 0, 0);
        st.setLayoutParams(sp);
        sh.addView(st);
        historyBox.addView(sh);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout rowl = Ui.row(this);
        for (Model.Item it : list) rowl.addView(card(it, Ui.dp(this, 108)));
        hs.addView(rowl);
        historyBox.addView(hs);
    }

    /* ═══════════ البحث ═══════════ */

    private LinearLayout buildSearch() {
        searchView = Ui.column(this);
        searchView.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), 0);

        searchTitle = Ui.text(this, I18n.t(this, "discover"), 13, Ui.TEXT, true);
        searchTitle.setPadding(Ui.dp(this, 2), 0, 0, Ui.dp(this, 6));
        searchView.addView(searchTitle);

        FrameLayout box = new FrameLayout(this);
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        searchGrid = new GridView(this);
        searchGrid.setNumColumns(3);
        searchGrid.setHorizontalSpacing(Ui.dp(this, 8));
        searchGrid.setVerticalSpacing(Ui.dp(this, 12));
        searchGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        searchGrid.setClipToPadding(false);
        searchGrid.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        searchGrid.setSelector(new ColorDrawable(0x00000000));
        searchAdapter = new ItemAdapter();
        searchGrid.setAdapter(searchAdapter);
        searchGrid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                List<Model.Item> items = searchAdapter.getItems();
                if (pos >= 0 && pos < items.size())
                    Dialogs.showDetail(MainActivity.this, client, items.get(pos));
            }
        });
        box.addView(searchGrid);

        stateSearch = Ui.text(this, I18n.t(this, "search_start") + "\n\n" + I18n.t(this, "search_start_desc"),
                12.5f, Ui.MUTED, false);
        stateSearch.setGravity(Gravity.CENTER);
        box.addView(stateSearch);
        searchView.addView(box);
        return searchView;
    }

    private void loadDiscovery() {
        stateSearch.setText(I18n.t(this, "loading"));
        stateSearch.setVisibility(View.VISIBLE);
        bg(new Runnable() {
            @Override public void run() {
                try {
                    final List<Model.Item> list = Model.parseDefaultPage(client.defaultPage(30));
                    ui(new Runnable() {
                        @Override public void run() {
                            searchAdapter.setItems(list);
                            searchAdapter.notifyDataSetChanged();
                            searchTitle.setText(I18n.t(MainActivity.this, "trending"));
                            stateSearch.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                            if (list.isEmpty()) stateSearch.setText(I18n.t(MainActivity.this, "empty"));
                        }
                    });
                } catch (final Throwable e) {
                    ui(new Runnable() {
                        @Override public void run() {
                            stateSearch.setText(I18n.t(MainActivity.this, "search_start") + "\n\n"
                                    + I18n.t(MainActivity.this, "search_start_desc"));
                            stateSearch.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    /* ═══════════ الإعدادات ═══════════ */

    private LinearLayout buildSettings() {
        settingsView = Ui.column(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout v = Ui.column(this);
        v.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 24));

        LinearLayout conn = panel(I18n.t(this, "connection"));
        fBase = field(conn, I18n.t(this, "base_url"), client.status().optString("base_url", ""), false);
        fDevice = field(conn, I18n.t(this, "device_id"), client.status().optString("device_id", ""), false);
        fEmail = field(conn, I18n.t(this, "email"), client.status().optString("email", ""), false);
        fPass = field(conn, I18n.t(this, "password"), "", true);
        fPass.setHint(I18n.t(this, "password_hint"));

        TextView langLbl = Ui.text(this, I18n.t(this, "language"), 10.5f, Ui.MUTED, true);
        langLbl.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 6));
        conn.addView(langLbl);

        LinearLayout langs = Ui.row(this);
        for (int i = 0; i < I18n.CODES.length; i++) {
            final String code = I18n.CODES[i];
            boolean on = I18n.lang(this).equals(code);
            TextView b = Ui.small(this, I18n.LABELS[i], on ? 0x66E50914 : Ui.PANEL_2, on ? Color.WHITE : Ui.MUTED);
            LinearLayout.LayoutParams p = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, Ui.dp(this, 6), 0);
            b.setLayoutParams(p);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    I18n.setLang(MainActivity.this, code);
                    recreate();
                }
            });
            langs.addView(b);
        }
        conn.addView(langs);

        debugBox = new CheckBox(this);
        debugBox.setText(I18n.t(this, "debug"));
        debugBox.setTextColor(Ui.MUTED);
        debugBox.setTextSize(11.5f);
        debugBox.setChecked(client.isDebug());
        debugBox.setPadding(0, Ui.dp(this, 10), 0, 0);
        conn.addView(debugBox);

        TextView save = Ui.button(this, I18n.t(this, "save"), true);
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, Ui.dp(this, 12), 0, 0);
        save.setLayoutParams(sp);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { saveSettings(); }
        });
        conn.addView(save);
        v.addView(conn);

        LinearLayout acc = panel(I18n.t(this, "account"));
        accUser = kv(acc, I18n.t(this, "user_id"), "—");
        accVip = kv(acc, I18n.t(this, "vip"), "—");
        accToken = kv(acc, I18n.t(this, "token"), "—");
        accType = kv(acc, I18n.t(this, "usertype"), "—");
        accVersion = kv(acc, I18n.t(this, "version"), "—");

        LinearLayout btns = Ui.row(this);
        btns.setPadding(0, Ui.dp(this, 12), 0, 0);
        TextView re = Ui.small(this, I18n.t(this, "relogin"), 0x33E50914, 0xFFFFFFFF);
        re.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { relogin(); }
        });
        TextView out = Ui.small(this, I18n.t(this, "logout"), 0x33FF5C72, 0xFFFF97A5);
        out.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                client.logout();
                toast(I18n.t(MainActivity.this, "logged_out"));
                refreshAccount();
                setStatus(I18n.t(MainActivity.this, "offline"), Ui.DANGER);
            }
        });
        LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, Ui.dp(this, 8), 0);
        re.setLayoutParams(rp);
        btns.addView(re);
        btns.addView(out);
        acc.addView(btns);

        v.addView(acc);
        sv.addView(v);
        settingsView.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        // ── إحصائيات المشاهدة ──
        LinearLayout statsHead = Ui.row(this);
        statsHead.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        statsHead.addView(Ui.text(this, I18n.t(this, "your_stats"), 14f, Ui.TEXT, true));
        settingsView.addView(statsHead);
        statBox = Ui.column(this);
        statBox.setBackground(Ui.roundStroke(Ui.PANEL, 12, Ui.STROKE, this));
        statBox.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        settingsView.addView(statBox);
        refreshStatsUi(statBox);

return settingsView;
    }

    private LinearLayout panel(String title) {
        LinearLayout p = Ui.column(this);
        p.setBackground(Ui.roundStroke(Ui.PANEL, 16, Ui.STROKE, this));
        p.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 14));
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 14));
        p.setLayoutParams(lp);
        TextView t = Ui.text(this, title, 13, Ui.TEXT, true);
        t.setPadding(0, 0, 0, Ui.dp(this, 8));
        p.addView(t);
        return p;
    }

    private EditText field(LinearLayout parent, String label, String value, boolean password) {
        TextView l = Ui.text(this, label, 10.5f, Ui.MUTED, true);
        l.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        parent.addView(l);
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(Ui.TEXT);
        e.setTextSize(12.5f);
        e.setSingleLine(true);
        e.setHintTextColor(Ui.MUTED_2);
        e.setBackground(Ui.roundStroke(0xFF1A1A1A, 10, Ui.STROKE, this));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        e.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        parent.addView(e);
        return e;
    }

    private TextView kv(LinearLayout parent, String key, String value) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView k = Ui.text(this, key, 11.5f, Ui.MUTED, false);
        k.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = Ui.text(this, value, 11.5f, Ui.TEXT, true);
        val.setMaxLines(1);
        val.setEllipsize(TextUtils.TruncateAt.END);
        val.setGravity(Gravity.END);
        r.addView(k);
        r.addView(val);
        parent.addView(r);
        return val;
    }

    /* ═══════════ البطاقة ═══════════ */

    private View card(final Model.Item it, int widthPx) {
        LinearLayout cell = Ui.column(this);
        cell.setPadding(0, 0, Ui.dp(this, 8), 0);

        FrameLayout fr = new FrameLayout(this);
        fr.setBackground(Ui.roundStroke(0xFF1A1A1A, 8, 0x22FFFFFF, this));
        fr.setClipToOutline(true);
        int posterH = widthPx <= 0 ? Ui.dp(this, 180) : (int) (widthPx * 1.48f);
        fr.setLayoutParams(new LinearLayout.LayoutParams(widthPx, posterH));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fr.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView letter = Ui.text(this, it.title.length() > 0 ? it.title.substring(0, 1) : "?",
                22, 0x44FFFFFF, true);
        letter.setGravity(Gravity.CENTER);
        fr.addView(letter, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (it.score != null && it.score.length() > 0 && !"0".equals(it.score) && !"0.0".equals(it.score)) {
            TextView badge = Ui.text(this, "★ " + it.score, 9.5f, 0xFFFFFFFF, true);
            badge.setBackground(Ui.round(Ui.BRAND, 6, this));
            badge.setPadding(Ui.dp(this, 5), Ui.dp(this, 2), Ui.dp(this, 5), Ui.dp(this, 2));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.gravity = Gravity.TOP | Gravity.START;
            bp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), 0, 0);
            badge.setLayoutParams(bp);
            fr.addView(badge);
        }

        if (it.progress > 0 && it.duration > 0) {
            View prog = new View(this);
            prog.setBackground(Ui.round(Ui.BRAND2, 2, this));
            int w = (int) (widthPx * Math.min(1f, (float) it.progress / Math.max(1, it.duration)));
            FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Math.max(6, w), Ui.dp(this, 3));
            pp.gravity = Gravity.BOTTOM | Gravity.START;
            pp.setMargins(Ui.dp(this, 6), 0, 0, Ui.dp(this, 6));
            prog.setLayoutParams(pp);
            fr.addView(prog);
        }

        String url = it.poster != null && it.poster.length() > 0 ? it.poster : it.backdrop;
        if (url != null && url.length() > 0) Ui.image(img, url, Ui.dp(this, 150));

        TextView title = Ui.text(this, it.title, 10.5f, Ui.TEXT, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(this, 5), 0, 0);
        title.setMinHeight(Ui.dp(this, 28));

        cell.addView(fr);
        cell.addView(title);
        cell.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Dialogs.showDetail(MainActivity.this, client, it);
            }
        });
        return cell;
    }

    private class ItemAdapter extends android.widget.BaseAdapter {
        private List<Model.Item> data = new ArrayList<Model.Item>();
        void setItems(List<Model.Item> items) { data = items == null ? new ArrayList<Model.Item>() : items; }
        List<Model.Item> getItems() { return data; }
        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int position) { return data.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            return card(data.get(position), ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /* ═══════════ المنطق ═══════════ */

    private void bg(final Runnable r) {
        new Thread(new Runnable() {
            @Override public void run() {
                try { r.run(); } catch (Throwable ignored) { }
            }
        }).start();
    }

    private void ui(final Runnable r) { runOnUiThread(r); }

    private void toast(String s) { Toast.makeText(this, s == null ? "" : s, Toast.LENGTH_LONG).show(); }

    private void setStatus(String text, int color) {
        if (statusDot != null) statusDot.setTextColor(color);
        if (statusTxt != null) statusTxt.setText(text);
    }

    private void autoLogin() {
        setStatus(I18n.t(this, "connecting"), Ui.GOLD);
        bg(new Runnable() {
            @Override public void run() {
                try {
                    client.ensureLogin();
                    ui(new Runnable() {
                        @Override public void run() {
                            setStatus(I18n.t(MainActivity.this, "connected"), Ui.OK);
                            refreshAccount();
                            loadHome();
                        }
                    });
                } catch (final Throwable e) {
                    ui(new Runnable() {
                        @Override public void run() {
                            setStatus(I18n.t(MainActivity.this, "offline"), Ui.DANGER);
                            stateHome.setText(I18n.t(MainActivity.this, "login_failed") + ": " + msg(e));
                            stateHome.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    private void relogin() {
        setStatus(I18n.t(this, "connecting"), Ui.GOLD);
        bg(new Runnable() {
            @Override public void run() {
                try {
                    client.logout();
                    client.doLogin();
                    ui(new Runnable() {
                        @Override public void run() {
                            setStatus(I18n.t(MainActivity.this, "connected"), Ui.OK);
                            toast(I18n.t(MainActivity.this, "signed_in"));
                            refreshAccount();
                            loadHome();
                        }
                    });
                } catch (final Throwable e) {
                    ui(new Runnable() {
                        @Override public void run() {
                            setStatus(I18n.t(MainActivity.this, "offline"), Ui.DANGER);
                            toast(I18n.t(MainActivity.this, "login_failed") + ": " + msg(e));
                        }
                    });
                }
            }
        });
    }

    private void loadHome() {
        stateHome.setText(I18n.t(this, "loading"));
        stateHome.setVisibility(View.VISIBLE);
        sectionBox.removeAllViews();
        historyBox.removeAllViews();
        bg(new Runnable() {
            @Override public void run() {
                final List<Model.Section> merged = new ArrayList<Model.Section>();
                List<Model.Item> hist = new ArrayList<Model.Item>();
                String err = null;
                for (int page = 1; page <= 2; page++) {
                    try {
                        for (Model.Section s : Model.parseHome(client.home(page, 10))) {
                            boolean dup = false;
                            for (Model.Section x : merged) if (x.title.equals(s.title)) { dup = true; break; }
                            if (!dup) merged.add(s);
                        }
                    } catch (Throwable t) {
                        if (err == null) err = msg(t);
                    }
                }
                try { hist = Model.parseHistory(client.history()); } catch (Throwable ignored) { }
                final String error = err;
                final List<Model.Item> history = hist;
                ui(new Runnable() {
                    @Override public void run() {
                        if (merged.isEmpty()) {
                            stateHome.setText(error != null
                                    ? I18n.t(MainActivity.this, "error") + ": " + error
                                    : I18n.t(MainActivity.this, "empty"));
                            stateHome.setVisibility(View.VISIBLE);
                        } else {
                            stateHome.setVisibility(View.GONE);
                            Model.Item first = merged.get(0).items.get(0);
                            renderHero(first);
                            renderHistory(history);
                            renderSections(merged);
                            renderContinueWatching();
                        }
                    }
                });
            }
        });
    }

    private void doSearch(final String q) {
        showTab(1);
        stateSearch.setText(I18n.t(this, "loading"));
        stateSearch.setVisibility(View.VISIBLE);
        bg(new Runnable() {
            @Override public void run() {
                try {
                    final List<Model.Item> list = Model.parseSearch(client.searchContent(q, 30));
                    ui(new Runnable() {
                        @Override public void run() {
                            searchAdapter.setItems(list);
                            searchAdapter.notifyDataSetChanged();
                            searchTitle.setText("« " + q + " »");
                            if (list.isEmpty()) {
                                stateSearch.setText(I18n.t(MainActivity.this, "no_results") + " «" + q + "»");
                                stateSearch.setVisibility(View.VISIBLE);
                            } else {
                                stateSearch.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (final Throwable e) {
                    ui(new Runnable() {
                        @Override public void run() {
                            stateSearch.setText(I18n.t(MainActivity.this, "error") + ": " + msg(e));
                            stateSearch.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }
        });
    }

    private void saveSettings() {
        String base = fBase.getText().toString().trim();
        String dev = fDevice.getText().toString().trim();
        String mail = fEmail.getText().toString().trim();
        String pass = fPass.getText().toString();
        client.setCredentials(mail, pass, base, dev);
        client.setDebug(debugBox.isChecked());
        fPass.setText("");
        toast(I18n.t(this, "saved"));
        refreshAccount();
        relogin();
    }

    private void refreshAccount() {
        if (accToken == null) return;
        try {
            JSONObject s = client.status();
            accToken.setText(s.optString("token_preview", "—"));
            accType.setText(s.optString("usertype", "—"));
            accVersion.setText("v" + s.optString("app_version", ""));
            if (s.optBoolean("logged_in")) setStatus(I18n.t(this, "connected"), Ui.OK);
        } catch (Throwable ignored) { }
        bg(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject vip = client.vipInfo();
                    JSONObject d = vip.optJSONObject("data");
                    final String uid = d == null ? "—" : d.optString("userId", "—");
                    String sub = d == null ? "" : d.optString("subscript", "");
                    final String vipTxt = sub.length() > 0 ? sub : "—";
                    ui(new Runnable() {
                        @Override public void run() {
                            if (accUser != null) accUser.setText(uid);
                            if (accVip != null) accVip.setText(vipTxt);
                        }
                    });
                } catch (Throwable ignored) { }
            }
        });
    }

    private void languageDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.roundStroke(Ui.PANEL, 16, Ui.STROKE_2, this));
        box.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        for (int i = 0; i < I18n.CODES.length; i++) {
            final String code = I18n.CODES[i];
            boolean on = I18n.lang(this).equals(code);
            TextView b = Ui.text(this, I18n.LABELS[i], 14, on ? Color.WHITE : Ui.MUTED, true);
            b.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
            b.setBackground(Ui.round(on ? 0x55E50914 : 0x00000000, 10, this));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    I18n.setLang(MainActivity.this, code);
                    d.dismiss();
                    recreate();
                }
            });
            box.addView(b);
        }
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(Ui.dp(this, 230), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
        d.show();
    }

    private static String msg(Throwable t) {
        String m = t == null ? null : t.getMessage();
        if (m == null || m.trim().length() == 0) m = t == null ? "?" : t.getClass().getSimpleName();
        return m;
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) { toggleDrawer(false); return; }
        if (tab != 0) { showTab(0); return; }
        super.onBackPressed();
    }

    private void showDownloads() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(this);
        LinearLayout panel = Ui.column(this);
        panel.setBackgroundColor(Ui.BG);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        TextView title = Ui.text(this, I18n.t(this, "my_downloads"), 18, Ui.TEXT, true);
        panel.addView(title);
        panel.addView(Ui.spacer(this, 12));
        java.util.List<org.json.JSONObject> items = OfflineStore.list(this);
        if (items.isEmpty()) {
            panel.addView(Ui.text(this, I18n.t(this, "downloads_empty"), 13, Ui.MUTED, false));
        } else {
            for (final org.json.JSONObject o : items) {
                LinearLayout row = Ui.row(this);
                row.setBackground(Ui.roundStroke(Ui.PANEL, 12, Ui.STROKE, this));
                row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
                LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.setMargins(0, 0, 0, Ui.dp(this, 8));
                row.setLayoutParams(rp);
                TextView name = Ui.text(this, o.optString("title", "—"), 13, Ui.TEXT, true);
                name.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(name);
                TextView play = Ui.small(this, "▶", 0x33E50914, 0xFFFFFFFF);
                play.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            Intent i = new Intent(MainActivity.this, PlayerActivity.class);
                            i.putExtra("url", "file://" + o.optString("path"));
                            i.putExtra("title", o.optString("title", ""));
                            i.putExtra("local", true);
                            startActivity(i);
                            d.dismiss();
                        } catch (Throwable t) {
                            Toast.makeText(MainActivity.this, I18n.t(MainActivity.this, "error"), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                row.addView(play);
                TextView del = Ui.small(this, "✕", 0x33FFFFFF, Ui.TEXT);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        OfflineStore.remove(MainActivity.this, o.optString("contentId"), o.optString("episodeId"));
                        d.dismiss();
                        showDownloads();
                    }
                });
                row.addView(del);
                panel.addView(row);
            }
        }
        TextView close = Ui.button(this, "تم", true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        panel.addView(Ui.spacer(this, 12));
        panel.addView(close);
        sv.addView(panel);
        d.setContentView(sv);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }


    private void showMyList() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(this);
        LinearLayout panel = Ui.column(this);
        panel.setBackgroundColor(Ui.BG);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        panel.addView(Ui.text(this, I18n.t(this, "my_list"), 18, Ui.TEXT, true));
        panel.addView(Ui.spacer(this, 12));
        java.util.List<org.json.JSONObject> items = MyList.list(this);
        if (items.isEmpty()) {
            panel.addView(Ui.text(this, I18n.t(this, "my_list_empty"), 13, Ui.MUTED, false));
        } else {
            for (final org.json.JSONObject o : items) {
                LinearLayout row = Ui.row(this);
                row.setBackground(Ui.roundStroke(Ui.PANEL, 12, Ui.STROKE, this));
                row.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
                LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.setMargins(0, 0, 0, Ui.dp(this, 8));
                row.setLayoutParams(rp);
                TextView name = Ui.text(this, o.optString("title", "—"), 13, Ui.TEXT, true);
                name.setLayoutParams(Ui.lpm(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(name);
                TextView open = Ui.small(this, "▶", 0x33E50914, 0xFFFFFFFF);
                open.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Model.Item it = new Model.Item();
                        it.id = o.optString("id");
                        it.title = o.optString("title");
                        it.poster = o.optString("poster");
                        Dialogs.showDetail(MainActivity.this, client, it);
                        d.dismiss();
                    }
                });
                row.addView(open);
                TextView del = Ui.small(this, "✕", 0x33FFFFFF, Ui.TEXT);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Model.Item it = new Model.Item();
                        it.id = o.optString("id");
                        it.title = o.optString("title");
                        MyList.toggle(MainActivity.this, it);
                        d.dismiss();
                        showMyList();
                    }
                });
                row.addView(del);
                panel.addView(row);
            }
        }
        TextView close = Ui.button(this, "تم", true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        panel.addView(Ui.spacer(this, 12));
        panel.addView(close);
        sv.addView(panel);
        d.setContentView(sv);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

}
