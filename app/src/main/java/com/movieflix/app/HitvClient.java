package com.movieflix.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * عميل MovieFlix — نسخة Java — بروتوكول الخدمة (مستخرجة من ملف الاتصالات):
 *  • POST /user/app/email/login  → token (JWT)
 *  • GET  /savita/homePage/getHome?page&navigationId=1000000&size
 *  • POST /aggregation/search/v2/searchContent
 *  • GET  /savita/movieDrama/get?id&category&origin=5
 *  • GET  /savita/media/previewInfo?category&contentId&episodeId&definition&projection&tryCode
 *  • GET  /user/vip/myVipInfo
 * التوقيع: AES-ECB + RSA(PKCS#1 v1.5) + MD5 — مع فكّ gzip وإعادة الدخول عند 401/403.
 */
public class HitvClient {

    static final String DEFAULT_BASE = "https://mobile-api.hitv.ltd";
    static final String DEFAULT_PUB_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCYo79ZFZHFCyboUBinm/I1OR7Obq2Do+4sMufINvD2Qw5fYvZeSYR24/lxRMsZnlbcP0a3/lG9dyX2G0ljvQwBSdCW9jTBBaOJiSW1M2DEFCP5/6ObJ7WCwDKxVVxebhzlzhGjjuPB86vsVVwYXTFtTZKfuTk3SQnpta/TOFE6cwIDAQAB";

    static final String DEFAULT_EMAIL = "modeadelvip@gmail.com";
    static final String DEFAULT_PASSWORD = "owDJ5S8-8Nz1a$v";
    static final String DEFAULT_DEVICE = "378f55c6f5bdfd8d";
    static final String PHONE_MODEL = "RMX3834 RMX3834";
    static final String PHONE_SYSTEM = "android_15_realme_AP3A.240905.015.A2";

    static final String CLIENT_TYPE = "android_movieflix";
    static final String VERSION_CODE = "133";
    static final String TIMEZONE = "GMT+02:00";
    static final String MCC = "602";
    static final String UA = "okhttp/4.12.0";
    static final String APP_VERSION = "3.2.0";

    private final SharedPreferences prefs;
    private final PublicKey pubKey;

    private String baseUrl, email, password, deviceId;
    private String token = "", usertype = "";
    private boolean debug = false;
    private volatile boolean loggingIn = false;
    private String debugLogin = "";

    public HitvClient(Context ctx) {
        prefs = ctx.getSharedPreferences("movieflix", Context.MODE_PRIVATE);
        baseUrl = prefs.getString("base_url", DEFAULT_BASE);
        email = prefs.getString("email", DEFAULT_EMAIL);
        password = prefs.getString("password", DEFAULT_PASSWORD);
        deviceId = prefs.getString("device_id", DEFAULT_DEVICE);
        token = prefs.getString("token", "");
        usertype = prefs.getString("usertype", "");
        debug = prefs.getBoolean("debug", false);
        PublicKey k = null;
        try {
            byte[] der = Base64.decode(DEFAULT_PUB_KEY, Base64.DEFAULT);
            k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ignored) { }
        pubKey = k;
    }

    /* ───────── التوقيع ───────── */

    private static String randAes() {
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String pyQuote(String s) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes;
        try { bytes = s.getBytes("UTF-8"); } catch (Exception e) { bytes = s.getBytes(); }
        for (byte b : bytes) {
            int c = b & 0xFF;
            boolean safe = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~' || c == '/';
            if (safe) sb.append((char) c);
            else sb.append('%').append(String.format(Locale.US, "%02X", c));
        }
        return sb.toString();
    }

    private String rsaEncrypt(String raw) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, pubKey);
        return Base64.encodeToString(c.doFinal(raw.getBytes("UTF-8")), Base64.NO_WRAP);
    }

    private static byte[] pkcs7Pad(byte[] raw) {
        int n = 16 - (raw.length % 16);
        byte[] out = new byte[raw.length + n];
        System.arraycopy(raw, 0, out, 0, raw.length);
        for (int i = raw.length; i < out.length; i++) out[i] = (byte) n;
        return out;
    }

    private static String signData(JSONObject params, String ts) {
        List<String> keys = new ArrayList<String>();
        for (Iterator<String> it = params.keys(); it.hasNext(); ) keys.add(it.next());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            String v = String.valueOf(params.opt(k));
            if (v == null || v.length() == 0 || "null".equals(v)) continue;
            sb.append(v);
        }
        sb.append(ts);
        return pyQuote(sb.toString());
    }

    private static String makeSign(String aes, String sd) throws Exception {
        Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aes.getBytes("UTF-8"), "AES"));
        byte[] enc = c.doFinal(pkcs7Pad(sd.getBytes("UTF-8")));
        String b64 = Base64.encodeToString(enc, Base64.NO_WRAP);
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(b64.getBytes("UTF-8"));
        StringBuilder hex = new StringBuilder();
        for (byte b : d) hex.append(String.format(Locale.US, "%02x", b & 0xFF));
        return hex.toString();
    }

    private String decryptData(String data, String aes) {
        if (data == null || data.length() == 0) return null;
        try {
            byte[] raw = Base64.decode(data, Base64.DEFAULT);
            Cipher c = Cipher.getInstance("AES/ECB/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aes.getBytes("UTF-8"), "AES"));
            byte[] pt = c.doFinal(raw);
            int p = pt.length > 0 ? (pt[pt.length - 1] & 0xFF) : 0;
            if (p < 1 || p > 16) p = 0;
            String s = new String(pt, 0, pt.length - p, "UTF-8");
            new JSONObject(s);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /* ───────── HTTP ───────── */

    static class Resp {
        int code;
        String text;
        Map<String, String> headers;
    }

    private JSONObject headers(String ts, String ek, String sign) {
        JSONObject h = new JSONObject();
        try {
            h.put("clienttype", CLIENT_TYPE);
            h.put("versioncode", VERSION_CODE);
            h.put("deviceid", deviceId);
            h.put("lang", "ar");
            h.put("timezone", TIMEZONE);
            h.put("mcc", MCC);
            h.put("keke", "false");
            h.put("geolatitude", "");
            h.put("geolongitude", "");
            h.put("geoisocode", "");
            h.put("geoisoname", "");
            h.put("georeliable", "");
            h.put("sign", sign);
            h.put("aeskey", ek);
            h.put("currenttime", ts);
            h.put("user-agent", UA);
            if (token != null && token.length() > 0) h.put("token", token);
            if (usertype != null && usertype.length() > 0) h.put("usertype", usertype);
        } catch (Exception ignored) { }
        return h;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private Resp http(String method, String urlStr, JSONObject jsonBody, JSONObject hdrs) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(20000);
        con.setReadTimeout(35000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("accept-encoding", "gzip, deflate");
        con.setRequestProperty("accept", "*/*");
        if (hdrs != null) {
            for (Iterator<String> it = hdrs.keys(); it.hasNext(); ) {
                String k = it.next();
                con.setRequestProperty(k, String.valueOf(hdrs.opt(k)));
            }
        }
        if (jsonBody != null) {
            con.setDoOutput(true);
            con.setRequestProperty("content-type", "application/json; charset=UTF-8");
            byte[] body = jsonBody.toString().getBytes("UTF-8");
            OutputStream os = con.getOutputStream();
            os.write(body);
            os.flush();
            os.close();
        }

        Resp r = new Resp();
        r.code = con.getResponseCode();
        r.headers = new HashMap<String, String>();
        for (Map.Entry<String, List<String>> e : con.getHeaderFields().entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
            r.headers.put(e.getKey().toLowerCase(Locale.US), e.getValue().get(0));
        }

        InputStream is = (r.code >= 200 && r.code < 300) ? con.getInputStream() : con.getErrorStream();
        byte[] bytes = new byte[0];
        if (is != null) {
            String enc = r.headers.get("content-encoding");
            try {
                if (enc != null && enc.toLowerCase(Locale.US).contains("gzip")) is = new GZIPInputStream(is);
                else if (enc != null && enc.toLowerCase(Locale.US).contains("deflate")) is = new InflaterInputStream(is);
            } catch (Exception ignored) { }
            try { bytes = readAll(is); } catch (Exception ignored) { }
            try { is.close(); } catch (Exception ignored) { }
        }
        con.disconnect();
        try { r.text = new String(bytes, "UTF-8"); } catch (Exception e) { r.text = new String(bytes); }
        return r;
    }

    /* ───────── usertype / أخطاء ───────── */

    private void updateUsertype(Resp r, JSONObject payload) {
        if (r != null && r.headers != null) {
            String ut = r.headers.get("usertype");
            if (ut == null) ut = r.headers.get("user-type");
            if (ut != null && ut.trim().length() > 0) { usertype = ut.trim(); return; }
        }
        if (payload != null) {
            JSONObject data = payload.optJSONObject("data");
            if (data != null) {
                String ut = data.optString("usertype", "");
                if (ut.length() == 0) ut = data.optString("userType", "");
                if (ut.length() > 0) usertype = ut;
            }
        }
    }

    private static boolean expired(int code, JSONObject payload) {
        if (code == 401 || code == 403) return true;
        if (payload == null) return false;
        String c = payload.optString("code", "");
        if ("00001".equals(c) || "00002".equals(c) || "401".equals(c) || "10001".equals(c)
                || "A0001".equals(c) || "A0002".equals(c)) return true;
        String msg = payload.optString("msg", "").toLowerCase(Locale.US);
        return msg.contains("token") || msg.contains("expire") || msg.contains("not login")
                || msg.contains("unauthorized") || msg.contains("انتهت");
    }

    private static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 160; i++) {
            char c = s.charAt(i);
            if (c >= 32 && c != 127) sb.append(c);
        }
        return sb.toString().trim();
    }

    private static String describe(Resp r) {
        String msg = "";
        try {
            JSONObject o = new JSONObject(r.text);
            msg = o.optString("msg", "");
        } catch (Exception ignored) { }
        if (msg.length() == 0) msg = clean(r.text);
        if (r.code == 403) return "رفضت الخدمة الطلب (403)" + (msg.length() > 0 ? " · " + msg : "");
        if (r.code == 401) return "الجلسة غير صالحة (401)" + (msg.length() > 0 ? " · " + msg : "");
        return "HTTP " + r.code + (msg.length() > 0 ? " · " + msg : "");
    }

    /* ───────── الطلبات ───────── */

    public synchronized JSONObject request(String method, String path, JSONObject params, JSONObject body, boolean retryLogin)
            throws Exception {
        if (deviceId == null || deviceId.length() == 0) throw new RuntimeException("device_id مطلوب");
        String ts = String.valueOf(System.currentTimeMillis());
        String aes = randAes();
        String ek = rsaEncrypt(aes);
        JSONObject src = body != null ? body : (params != null ? params : new JSONObject());
        String sign = makeSign(aes, signData(src, ts));
        JSONObject h = headers(ts, ek, sign);

        String url = baseUrl.replaceAll("/+$", "") + path;
        if (params != null && params.length() > 0) {
            StringBuilder q = new StringBuilder();
            for (Iterator<String> it = params.keys(); it.hasNext(); ) {
                String k = it.next();
                String v = String.valueOf(params.opt(k));
                if ("null".equals(v)) continue;
                q.append(q.length() == 0 ? "?" : "&").append(pyQuote(k)).append("=").append(pyQuote(v));
            }
            url += q.toString();
        }

        Resp r = http(method, url, body, h);
        JSONObject payload;
        try { payload = new JSONObject(r.text); } catch (Exception e) {
            payload = new JSONObject();
            try { payload.put("_raw", clean(r.text)); } catch (Exception ignored) { }
        }

        String dataStr = payload.optString("data", "");
        if (dataStr.length() > 1 && (dataStr.startsWith("{") || dataStr.startsWith("["))) {
            try {
                payload.put("data", dataStr.startsWith("{") ? new JSONObject(dataStr) : new JSONArray(dataStr));
            } catch (Exception ignored) { }
        } else if (dataStr.length() > 0) {
            String dec = decryptData(dataStr, aes);
            if (dec != null) {
                try { payload.put("data", dec.startsWith("{") ? new JSONObject(dec) : new JSONArray(dec)); }
                catch (Exception ignored) { }
            }
        }

        updateUsertype(r, payload);

        if (retryLogin && expired(r.code, payload)) {
            token = "";
            save();
            doLogin();
            return request(method, path, params, body, false);
        }
        if (r.code < 200 || r.code >= 300) throw new RuntimeException(describe(r));

        String code = payload.optString("code", "");
        if (code.length() > 0 && !"00000".equals(code) && !"0".equals(code)) {
            throw new RuntimeException("API " + code + " · " + payload.optString("msg", ""));
        }
        return payload;
    }

    public synchronized void doLogin() throws Exception {
        if (loggingIn) return;
        loggingIn = true;
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            body.put("phoneModel", PHONE_MODEL);
            body.put("phoneSystem", PHONE_SYSTEM);

            String ts = String.valueOf(System.currentTimeMillis());
            String aes = randAes();
            String ek = rsaEncrypt(aes);
            String sign = makeSign(aes, signData(body, ts));
            JSONObject h = headers(ts, ek, sign);
            h.put("content-type", "application/json; charset=UTF-8");

            String url = baseUrl.replaceAll("/+$", "") + "/user/app/email/login";
            Resp r = http("POST", url, body, h);

            JSONObject payload;
            try { payload = new JSONObject(r.text); } catch (Exception e) {
                throw new RuntimeException("رد غير مفهوم من الخدمة: " + clean(r.text));
            }
            if (r.code < 200 || r.code >= 300) throw new RuntimeException(describe(r));
            String code = payload.optString("code", "");
            if (code.length() > 0 && !"00000".equals(code) && !"0".equals(code)) {
                throw new RuntimeException("فشل تسجيل الدخول · " + payload.optString("msg", code));
            }
            JSONObject data = payload.optJSONObject("data");
            if (data == null || data.optString("token", "").length() == 0) {
                throw new RuntimeException("لم يُعِد الخادم توكنًا صالحًا");
            }
            token = data.optString("token");
            usertype = "";
            updateUsertype(r, payload);
            debugLogin = "usertype=" + usertype + " | headers=" + r.headers;
            save();
        } finally {
            loggingIn = false;
        }
    }

    public synchronized void ensureLogin() throws Exception {
        if (token != null && token.length() > 0) return;
        doLogin();
    }

    /* ───────── نقاط النهاية (مطابقة لملف الاتصالات) ───────── */

    public JSONObject home(int page, int size) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("page", page);
        p.put("navigationId", "1000000");
        p.put("size", size);
        return request("GET", "/savita/homePage/getHome", p, null, true);
    }

    public JSONObject searchContent(String kw, int size) throws Exception {
        ensureLogin();
        JSONObject body = new JSONObject();
        body.put("searchKeyWord", kw);
        body.put("size", size);
        return request("POST", "/aggregation/search/v2/searchContent", null, body, true);
    }

    public JSONObject suggest(String kw, int size) throws Exception {
        ensureLogin();
        JSONObject body = new JSONObject();
        body.put("searchKeyWord", kw);
        body.put("size", size);
        return request("POST", "/cms/app/search/v2/searchLenovo", null, body, true);
    }

    public JSONObject detail(String id, int category) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("id", id);
        p.put("category", category);
        p.put("origin", 5);
        return request("GET", "/savita/movieDrama/get", p, null, true);
    }

    public JSONObject preview(String contentId, String episodeId, int category, String definition) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("category", category);
        p.put("contentId", contentId);
        p.put("episodeId", episodeId);
        p.put("definition", definition == null || definition.length() == 0 ? "GROOT_LD" : definition);
        p.put("projection", "false");
        p.put("tryCode", "0");
        return request("GET", "/savita/media/previewInfo", p, null, true);
    }

    public JSONObject vipInfo() throws Exception {
        ensureLogin();
        return request("GET", "/user/vip/myVipInfo", null, null, true);
    }


    /* ───────── بقية نقاط النهاية (من ملف الاتصالات) ───────── */

    public JSONObject defaultPage(int size) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("size", size);
        return request("GET", "/aggregation/search/defaultPage", p, null, true);
    }

    public JSONObject searchAll(String kw) throws Exception {
        ensureLogin();
        JSONObject b = new JSONObject();
        b.put("searchKeyWord", kw);
        return request("POST", "/aggregation/search/searchAll", null, b, true);
    }

    public JSONObject searchTag(String kw) throws Exception {
        ensureLogin();
        JSONObject b = new JSONObject();
        b.put("searchKeyWord", kw);
        return request("POST", "/aggregation/search/searchTag", null, b, true);
    }

    public JSONObject downloadInfo(String contentId, String episodeId, String definition) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("contentId", contentId);
        p.put("episodeId", episodeId);
        p.put("definition", definition == null ? "" : definition);
        p.put("formats", "");
        p.put("download", "1");
        return request("GET", "/savita/media/downloadInfo", p, null, true);
    }

    public JSONObject commentNum(String contentId) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("contentId", contentId);
        return request("GET", "/dynamic/app/talk/getCommentNum", p, null, true);
    }

    public JSONObject tabConfig(String contentId, int category) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("category", category);
        p.put("contentId", contentId);
        return request("GET", "/dynamic/app/talk/getTabConfig", p, null, true);
    }

    public JSONObject history() throws Exception {
        ensureLogin();
        return request("POST", "/savita/behavior/app/findBatchWatchHistory", null, new JSONObject(), true);
    }

    public JSONObject addHistory(String contentId, String episodeId, int episodeNo,
                                 int progress, int totalDuration, int category) throws Exception {
        ensureLogin();
        JSONObject b = new JSONObject();
        b.put("category", category);
        b.put("contentEpisodeId", episodeId);
        b.put("contentId", contentId);
        b.put("episodeNo", episodeNo);
        b.put("playTime", progress);
        b.put("progress", progress);
        b.put("recordWatch", false);
        b.put("timestamp", System.currentTimeMillis());
        b.put("totalDuration", totalDuration);
        return request("POST", "/user/behavior/app/addShortWatchHistory", null, b, true);
    }

    public JSONObject unreadQty() throws Exception {
        ensureLogin();
        return request("GET", "/cms/app/homePage/unreadQty", null, null, true);
    }

    public JSONObject allVip(String userId) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("userId", userId);
        return request("GET", "/user/vip/allWithExpire", p, null, true);
    }

    public JSONObject userInfo() throws Exception {
        ensureLogin();
        return request("POST", "/savita/auth/userInfo", null, new JSONObject(), true);
    }

    public JSONObject popupInfo() throws Exception {
        ensureLogin();
        return request("GET", "/user/app/popup/info", null, null, true);
    }

    public JSONObject shortsDetail(String shortsId) throws Exception {
        ensureLogin();
        JSONObject p = new JSONObject();
        p.put("shortsId", shortsId);
        return request("GET", "/cms/app/shorts/getShortsDetail", p, null, true);
    }

    /* ───────── الإعدادات ───────── */

    public void save() {
        prefs.edit()
                .putString("base_url", baseUrl)
                .putString("email", email)
                .putString("password", password)
                .putString("device_id", deviceId)
                .putString("token", token)
                .putString("usertype", usertype)
                .putBoolean("debug", debug)
                .apply();
    }

    public JSONObject status() {
        JSONObject s = new JSONObject();
        try {
            s.put("app_version", APP_VERSION);
            s.put("logged_in", token != null && token.length() > 0);
            s.put("token_preview", token != null && token.length() > 0
                    ? token.substring(0, Math.min(16, token.length())) + "…" : "");
            s.put("usertype", usertype == null ? "" : usertype);
            s.put("device_id", deviceId);
            s.put("base_url", baseUrl);
            s.put("email", email);
            s.put("has_password", password != null && password.length() > 0);
            s.put("language", "ar");
            s.put("timezone", TIMEZONE);
            s.put("debug_raw", debug);
            s.put("mock", false);
            s.put("login_error", "");
            s.put("login_ok", token != null && token.length() > 0);
            s.put("debug_login", debug ? clean(debugLogin) : "");
        } catch (Exception ignored) { }
        return s;
    }

    public void setCredentials(String newEmail, String newPassword, String newBase, String newDevice) {
        boolean changed = false;
        if (newEmail != null && newEmail.length() > 0 && !newEmail.equals(email)) { email = newEmail; changed = true; }
        if (newPassword != null && newPassword.length() > 0 && !newPassword.equals(password)) { password = newPassword; changed = true; }
        if (newBase != null && newBase.length() > 0 && !newBase.equals(baseUrl)) { baseUrl = newBase; changed = true; }
        if (newDevice != null && newDevice.length() > 0 && !newDevice.equals(deviceId)) { deviceId = newDevice; changed = true; }
        if (changed) { token = ""; usertype = ""; }
        save();
    }

    public void setDebug(boolean d) { debug = d; save(); }
    public boolean isDebug() { return debug; }
    public String token() { return token; }
    public String usertype() { return usertype; }

    public void logout() {
        token = "";
        usertype = "";
        save();
    }
}
