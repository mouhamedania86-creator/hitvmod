package com.movieflix.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline downloads store — saves media files under app private storage
 * and keeps a small JSON index in SharedPreferences.
 */
public final class OfflineStore {

    private static final String PREFS = "movieflix_offline";
    private static final String KEY_INDEX = "index";

    private OfflineStore() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "downloads");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static List<JSONObject> list(Context c) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_INDEX, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String path = o.optString("path", "");
                if (path.length() > 0 && new File(path).exists()) out.add(o);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean isDownloaded(Context c, String contentId, String episodeId) {
        String key = contentId + "|" + (episodeId == null ? "" : episodeId);
        for (JSONObject o : list(c)) {
            if (key.equals(o.optString("key"))) return true;
        }
        return false;
    }

    public static String localPath(Context c, String contentId, String episodeId) {
        String key = contentId + "|" + (episodeId == null ? "" : episodeId);
        for (JSONObject o : list(c)) {
            if (key.equals(o.optString("key"))) return o.optString("path", "");
        }
        return "";
    }

    public static void add(Context c, String contentId, String episodeId, String title, String path, String poster) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_INDEX, "[]"));
            String key = contentId + "|" + (episodeId == null ? "" : episodeId);
            // dedupe
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && !key.equals(o.optString("key"))) next.put(o);
            }
            JSONObject o = new JSONObject();
            o.put("key", key);
            o.put("contentId", contentId);
            o.put("episodeId", episodeId == null ? "" : episodeId);
            o.put("title", title == null ? "" : title);
            o.put("path", path);
            o.put("poster", poster == null ? "" : poster);
            o.put("ts", System.currentTimeMillis());
            next.put(o);
            sp.edit().putString(KEY_INDEX, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void remove(Context c, String contentId, String episodeId) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_INDEX, "[]"));
            String key = contentId + "|" + (episodeId == null ? "" : episodeId);
            JSONArray next = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (key.equals(o.optString("key"))) {
                    String path = o.optString("path", "");
                    if (path.length() > 0) new File(path).delete();
                } else {
                    next.put(o);
                }
            }
            sp.edit().putString(KEY_INDEX, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Download a direct http(s) media url into private storage. Returns absolute path or null. */
    public static String downloadUrl(Context c, String url, String contentId, String episodeId, String title, String poster) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            String safe = (contentId + "_" + (episodeId == null ? "0" : episodeId)).replaceAll("[^a-zA-Z0-9._-]", "_");
            File dest = new File(dir(c), safe + ".mp4");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.connect();
            int code = conn.getResponseCode();
            if (code >= 400) return null;
            in = conn.getInputStream();
            out = new FileOutputStream(dest);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            add(c, contentId, episodeId, title, dest.getAbsolutePath(), poster);
            return dest.getAbsolutePath();
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }
}
