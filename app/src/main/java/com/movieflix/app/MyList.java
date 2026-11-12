package com.movieflix.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Netflix-style My List — local favorites. */
public final class MyList {
    private static final String PREFS = "movieflix_mylist";
    private static final String KEY = "items";

    private MyList() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean contains(Context c, String id) {
        for (JSONObject o : list(c)) if (id.equals(o.optString("id"))) return true;
        return false;
    }

    public static void toggle(Context c, Model.Item item) {
        if (item == null || item.id == null) return;
        try {
            SharedPreferences p = sp(c);
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONArray next = new JSONArray();
            boolean found = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (item.id.equals(o.optString("id"))) { found = true; continue; }
                next.put(o);
            }
            if (!found) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("title", item.title == null ? "" : item.title);
                o.put("poster", item.poster == null ? "" : item.poster);
                o.put("type", item.type == null ? "" : item.type);
                o.put("ts", System.currentTimeMillis());
                next.put(o);
            }
            p.edit().putString(KEY, next.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<JSONObject> list(Context c) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        try {
            JSONArray arr = new JSONArray(sp(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(o);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
