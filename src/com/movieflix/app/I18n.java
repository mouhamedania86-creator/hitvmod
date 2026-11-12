package com.movieflix.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/** ترجمات: ar · en · hi · ur */
public class I18n {

    public static final String[] CODES = {"ar", "en", "hi", "ur"};
    public static final String[] LABELS = {"العربية", "English", "हिन्दी", "اردو"};

    private static final Map<String, String[]> T = new HashMap<String, String[]>();

    private static void p(String k, String ar, String en, String hi, String ur) {
        T.put(k, new String[]{ar, en, hi, ur});
    }

    static {
        p("app", "MovieFlix", "MovieFlix", "MovieFlix", "MovieFlix");
        p("tagline", "شاهد بلا حدود", "Watch unlimited", "असीमित देखें", "بلا حد دیکھیں");
        p("tab_home", "الرئيسية", "Home", "होम", "ہوم");
        p("tab_search", "البحث", "Search", "खोजें", "تلاش");
        p("tab_settings", "الإعدادات", "Settings", "सेटिंग्स", "ترتیبات");
        p("search_hint", "ابحث عن عمل…", "Search titles…", "शीर्षक खोजें…", "عنوان تلاش کریں…");
        p("featured", "رائج الآن", "Trending Now", "ट्रेंडिंग", "ٹرینڈنگ");
        p("play_info", "تشغيل", "Play", "चलाएँ", "چلائیں");
        p("download_info", "التنزيل", "Download", "डाउनलोड", "ڈاؤن لوڈ");
        p("details", "التفاصيل", "Details", "विवरण", "تفصیلات");
        p("discover", "اكتشف", "Discover", "खोजें", "دریافت کریں");
        p("refresh", "تحديث", "Refresh", "रिफ़्रेश", "ریفریش");
        p("loading", "جارٍ التحميل…", "Loading…", "लोड हो रहा है…", "لوڈ ہو رہا ہے…");
        p("error", "حدث خطأ", "Error", "त्रुटि", "خرابی");
        p("empty", "لا يوجد محتوى", "No content", "कोई सामग्री नहीं", "کوئی مواد نہیں");
        p("search_start", "ابدأ البحث", "Start searching", "खोजना शुरू करें", "تلاش شروع کریں");
        p("search_start_desc", "اكتب اسم عمل أو ممثل", "Type a title or name", "नाम लिखें", "نام لکھیں");
        p("no_results", "لا توجد نتائج لـ", "No results for", "कोई परिणाम नहीं:", "کوئی نتیجہ نہیں:");
        p("episodes", "الحلقات", "Episodes", "एपिसोड", "اقساط");
        p("episode", "حلقة", "Ep", "एपिसोड", "قسط");
        p("no_episodes", "لا توجد حلقات", "No episodes", "कोई एपिसोड नहीं", "کوئی قسط نہیں");
        p("links", "روابط المشاهدة", "Playback links", "प्लेबैक लिंक", "پلے بیک لنکس");
        p("dl_links", "روابط التنزيل", "Download links", "डाउनलोड लिंक", "ڈاؤن لوڈ لنکس");
        p("copy", "نسخ", "Copy", "कॉपी", "نقل");
        p("open", "فتح", "Open", "खोलें", "کھولیں");
        p("copied", "تم النسخ", "Copied", "कॉपी हुआ", "نقل ہو گیا");
        p("no_links", "لا توجد روابط", "No links", "कोई लिंक नहीं", "کوئی لنک نہیں");
        p("links_hint", "انسخ الرابط إلى مشغّلك المفضّل", "Copy the link into your player", "लिंक कॉपी करें", "لنک نقل کریں");
        p("settings", "الإعدادات", "Settings", "सेटिंग्स", "ترتیبات");
        p("connection", "الاتصال", "Connection", "कनेक्शन", "کنکشن");
        p("base_url", "عنوان الخدمة", "Base URL", "बेस URL", "بیس URL");
        p("device_id", "معرّف الجهاز", "Device ID", "डिवाइस आईडी", "ڈیوائس آئی ڈی");
        p("email", "البريد", "Email", "ईमेल", "ای میل");
        p("password", "كلمة المرور", "Password", "पासवर्ड", "پاس ورڈ");
        p("password_hint", "اتركها فارغة للإبقاء", "Blank keeps current", "खाली = मौजूदा", "خالی = موجودہ");
        p("language", "اللغة", "Language", "भाषा", "زبان");
        p("debug", "إظهار الرد الخام", "Show raw JSON", "कच्चा JSON", "خام JSON");
        p("save", "حفظ", "Save", "सहेजें", "محفوظ کریں");
        p("saved", "تم الحفظ", "Saved", "सहेजा गया", "محفوظ ہو گیا");
        p("relogin", "إعادة الدخول", "Sign in again", "फिर साइन इन", "دوبارہ سائن اِن");
        p("logout", "خروج", "Sign out", "साइन आउट", "سائن آؤٹ");
        p("logged_out", "تم مسح التوكن", "Token cleared", "टोकन हटा", "ٹوکن ہٹا");
        p("account", "الحساب", "Account", "खाता", "اکاؤنٹ");
        p("token", "التوكن", "Token", "टोकन", "ٹوکن");
        p("usertype", "نوع المستخدم", "User type", "उपयोगकर्ता", "صارف");
        p("version", "الإصدار", "Version", "संस्करण", "ورژن");
        p("vip", "العضوية", "Membership", "सदस्यता", "رکنیت");
        p("user_id", "رقم المستخدم", "User ID", "यूज़र आईडी", "صارف آئی ڈی");
        p("connected", "متصل", "Connected", "कनेक्टेड", "منسلک");
        p("connecting", "جارٍ الاتصال…", "Connecting…", "कनेक्ट…", "منسلک…");
        p("offline_mode", "وضع عدم الاتصال", "Offline mode", "ऑफ़लाइन मोड", "آف لائن موڈ");
        p("my_downloads", "تنزيلاتي", "My Downloads", "मेरे डाउनलोड", "میری ڈاؤن لوڈز");
        p("downloads_empty", "لا توجد تنزيلات بعد", "No downloads yet", "अभी कोई डाउनलोड नहीं", "ابھی کوئی ڈاؤن لوڈ نہیں");
        p("download_start", "بدء التنزيل", "Start download", "डाउनलोड शुरू", "ڈاؤن لوڈ شروع");
        p("download_done", "اكتمل التنزيل", "Download complete", "डाउनलोड पूरा", "ڈاؤن لوڈ مکمل");
        p("about_title", "عن التطبيق", "About", "के बारे में", "کے بارے میں");
        p("about_body", "MovieFlix — تطبيق مشاهدة سينمائي\n\nBy FaresCodeX\nالإصدار 1.1.0\n\n© 2026 جميع الحقوق محفوظة", "MovieFlix — cinematic streaming\n\nBy FaresCodeX\nVersion 1.1.0\n\n© 2026 All rights reserved", "MovieFlix\n\nBy FaresCodeX\nसंस्करण 1.0.0", "MovieFlix\n\nBy FaresCodeX\nورژن 1.0.0");

        p("my_list", "قائمتي", "My List", "मेरी सूची", "میری فہرست");
        p("my_list_empty", "القائمة فارغة", "Your list is empty", "सूची खाली है", "فہرست خالی ہے");
        p("continue_watching", "متابعة المشاهدة", "Continue Watching", "जारी रखें", "جاری رکھیں");
        p("your_stats", "إحصائياتك", "Your Stats", "आपके आँकड़े", "آپ کے اعداد");
        p("stat_minutes", "دقائق المشاهدة", "Minutes watched", "देखे मिनट", "دیکھے منٹ");
        p("stat_finished", "أعمال مكتملة", "Finished titles", "पूर्ण शीर्षक", "مکمل عنوان");
        p("stat_sessions", "جلسات", "Sessions", "सत्र", "سیشنز");
        p("stat_downloads", "تنزيلات", "Downloads", "डाउनलोड", "ڈاؤن لوڈز");
        p("audio", "الصوت", "Audio", "ऑडियो", "آڈیو");
        p("subtitles", "الترجمة", "Subtitles", "उपशीर्षक", "سب ٹائٹلز");
        p("off", "إيقاف", "Off", "बंद", "بند");
        p("tracks", "المسارات", "Tracks", "ट्रैक", "ٹریکس");
        p("trending", "الأكثر رواجاً", "Trending", "ट्रेंडिंग", "ٹرینڈنگ");
        p("offline", "غير متصل", "Offline", "ऑफ़लाइन", "آف لائن");
        p("signing_in", "جارٍ تسجيل الدخول…", "Signing in…", "साइन इन…", "سائن اِن…");
        p("signed_in", "تم الدخول", "Signed in", "साइन इन", "سائن اِن");
        p("login_failed", "تعذّر الدخول", "Sign-in failed", "साइन-इन विफल", "سائن اِن ناکام");
        p("raw", "الرد الخام", "Raw response", "कच्चा जवाब", "خام جواب");
        p("close", "إغلاق", "Close", "बंद", "بند");
        p("continue", "متابعة المشاهدة", "Continue watching", "देखना जारी रखें", "دیکھنا جاری رکھیں");
        p("trending", "الأكثر بحثًا", "Trending now", "ट्रेंडिंग", "ٹرینڈنگ");
        p("watch_now", "تشغيل الآن", "Play now", "अभी चलाएँ", "ابھی چلائیں");
        p("player_error", "تعذّر تشغيل الفيديو", "Playback failed", "वीडियो नहीं चला", "ویڈیو نہیں چلا");
    }

    public static String lang(Context c) {
        return c.getSharedPreferences("movieflix", Context.MODE_PRIVATE).getString("lang", "ar");
    }

    public static void setLang(Context c, String code) {
        c.getSharedPreferences("movieflix", Context.MODE_PRIVATE).edit().putString("lang", code).apply();
    }

    public static boolean rtl(Context c) {
        String l = lang(c);
        return "ar".equals(l) || "ur".equals(l);
    }

    public static int index(Context c) {
        String l = lang(c);
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(l)) return i;
        return 0;
    }

    public static String t(Context c, String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        return v[index(c)];
    }
}
