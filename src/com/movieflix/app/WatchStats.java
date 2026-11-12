package com.movieflix.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local watch history + user stats (minutes watched, titles finished).
 * Stored in SharedPreferences — no server required.
 */
public final class WatchStats {

    private static final String PREFS = "movieflix_stats";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_MINUTES = "total_minutes";
    private static final String KEY_FINISHED = "finished_count";
    private static final String KEY_SESSIONS = "sessions";

    private WatchStats() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Upsert continue-watching entry. progressMs / durationMs for bar. */
    public static void touch(Context c, String contentId, String episodeId, String title,
                             String poster, long progressMs, long durationMs) {
        if (contentId == null || contentId.length() == 0) return;
        try {
            SharedPreferences p = sp(c);
            JSONArray arr = new JSONArray(p.getString(KEY_HISTORY, "[]"));
            String key = contentId + "|" + (episodeId == null ? "" : episodeId);
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
            o.put("poster", poster == null ? "" : poster);
            o.put("progress", progressMs);
            o.put("duration", durationMs);
            o.put("ts", System.currentTimeMillis());
            // newest first
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < next.length() && out.length() < 40; i++) out.put(next.get(i));
            p.edit().putString(KEY_HISTORY, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void addWatchTime(Context c, long deltaMs) {
        if (deltaMs <= 0) return;
        SharedPreferences p = sp(c);
        long cur = p.getLong(KEY_MINUTES, 0);
        p.edit().putLong(KEY_MINUTES, cur + deltaMs).apply();
    }

    public static void markFinished(Context c) {
        SharedPreferences p = sp(c);
        p.edit().putInt(KEY_FINISHED, p.getInt(KEY_FINISHED, 0) + 1).apply();
    }

    public static void bumpSession(Context c) {
        SharedPreferences p = sp(c);
        p.edit().putInt(KEY_SESSIONS, p.getInt(KEY_SESSIONS, 0) + 1).apply();
    }

    public static List<JSONObject> history(Context c) {
        List<JSONObject> list = new ArrayList<JSONObject>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(o);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static long totalMinutes(Context c) {
        return sp(c).getLong(KEY_MINUTES, 0) / 60000L;
    }

    public static int finishedCount(Context c) {
        return sp(c).getInt(KEY_FINISHED, 0);
    }

    public static int sessions(Context c) {
        return sp(c).getInt(KEY_SESSIONS, 0);
    }

    public static void clearHistory(Context c) {
        sp(c).edit().putString(KEY_HISTORY, "[]").apply();
    }
}
