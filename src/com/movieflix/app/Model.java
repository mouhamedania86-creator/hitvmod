package com.movieflix.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** نماذج البيانات + تحليل ردود الخدمة الحقيقية (مطابقة لملف الاتصالات). */
public class Model {

    public static class Item {
        public String id = "", title = "", poster = "", backdrop = "", desc = "";
        public String type = "", year = "", area = "", score = "", episodes = "";
        public String episodeNo = "";
        public int progress = 0, duration = 0;
        public int category = 1;
    }

    public static class Section {
        public String title = "";
        public List<Item> items = new ArrayList<Item>();
    }

    public static class Episode {
        public String id = "", index = "", title = "", duration = "";
    }

    public static class Link {
        public String label = "", url = "";
    }

    private static String s(JSONObject o, String k) {
        if (o == null || !o.has(k)) return "";
        Object v = o.opt(k);
        if (v == null || v == JSONObject.NULL) return "";
        String r = String.valueOf(v).trim();
        return "null".equals(r) ? "" : r;
    }

    /* ── الصفحة الرئيسية: أقسام + عناصر ── */
    public static List<Section> parseHome(JSONObject payload) {
        List<Section> out = new ArrayList<Section>();
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) return out;
        JSONArray mods = data.optJSONArray("recommendItems");
        if (mods == null) return out;
        for (int i = 0; i < mods.length(); i++) {
            JSONObject m = mods.optJSONObject(i);
            if (m == null) continue;
            JSONArray list = m.optJSONArray("recommendContentVOList");
            if (list == null || list.length() == 0) continue;
            Section sec = new Section();
            sec.title = s(m, "homeSectionName");
            if (sec.title.length() == 0) sec.title = s(m, "jumpTitle");
            for (int k = 0; k < list.length(); k++) {
                JSONObject it = list.optJSONObject(k);
                if (it == null) continue;
                Item item = new Item();
                item.id = s(it, "id");
                item.title = s(it, "title");
                if (item.title.length() == 0) item.title = s(it, "videoName");
                item.poster = s(it, "imageUrl");
                item.backdrop = item.poster;
                item.type = s(it, "contentType");
                item.category = "MOVIE".equals(item.type) ? 0 : 1;
                item.year = s(it, "releaseTime");
                item.score = Norm.normScore(s(it, "score"));
                item.episodes = s(it, "resourceNum");
                JSONObject dt = it.optJSONObject("dramaTypeVo");
                if (dt != null) item.desc = s(dt, "dramaName");
                if (item.id.length() > 0 && item.title.length() > 0) sec.items.add(item);
            }
            if (!sec.items.isEmpty()) out.add(sec);
        }
        return out;
    }

    /* ── نتائج البحث ── */
    public static List<Item> parseSearch(JSONObject payload) {
        List<Item> out = new ArrayList<Item>();
        if (payload == null) return out;
        Object data = payload.opt("data");
        JSONArray arr = null;
        if (data instanceof JSONArray) arr = (JSONArray) data;
        else if (data instanceof JSONObject) {
            JSONObject d = (JSONObject) data;
            String[] keys = {"searchResults", "list", "records", "items", "contentList", "results"};
            for (String k : keys) {
                JSONArray a = d.optJSONArray(k);
                if (a != null && a.length() > 0) { arr = a; break; }
            }
        }
        if (arr == null) return items(payload);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.id = s(o, "id");
            it.title = s(o, "name");
            if (it.title.length() == 0) it.title = s(o, "title");
            it.poster = s(o, "coverVerticalUrl");
            if (it.poster.length() == 0) it.poster = s(o, "cover");
            it.backdrop = s(o, "coverHorizontalUrl");
            if (it.backdrop.length() == 0) it.backdrop = it.poster;
            it.desc = s(o, "introduction");
            it.type = s(o, "subType");
            it.category = "MOVIE".equals(it.type) ? 0 : 1;
            it.year = s(o, "releaseTime");
            it.score = Norm.normScore(s(o, "doubanScore"));
            if (it.score.length() == 0) it.score = Norm.normScore(s(o, "score"));
            JSONArray areas = o.optJSONArray("areas");
            if (areas != null && areas.length() > 0) {
                JSONObject a0 = areas.optJSONObject(0);
                if (a0 != null) it.area = s(a0, "name");
            }
            if (it.id.length() > 0) out.add(it);
        }
        return out;
    }


    /* ── متابعة المشاهدة ── */
    public static List<Item> parseHistory(JSONObject payload) {
        List<Item> out = new ArrayList<Item>();
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) return out;
        JSONArray list = data.optJSONArray("historyList");
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.id = s(o, "contentId");
            it.title = s(o, "contentTitle");
            if (it.title.length() == 0) it.title = s(o, "seriesName");
            it.poster = s(o, "verticalUrl");
            it.backdrop = s(o, "horizontalUrl");
            it.desc = s(o, "introduction");
            it.type = s(o, "subType");
            it.year = s(o, "year");
            it.score = Norm.normScore(s(o, "score"));
            it.category = o.optInt("category", 1);
            it.episodeNo = o.optString("episodeNo", "");
            it.progress = o.optInt("progress", 0);
            it.duration = o.optInt("totalDuration", 0);
            if (it.id.length() > 0) out.add(it);
        }
        return out;
    }

    /* ── الأكثر بحثًا (defaultPage) ── */
    public static List<Item> parseDefaultPage(JSONObject payload) {
        List<Item> out = new ArrayList<Item>();
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) return out;
        JSONArray list = data.optJSONArray("searchLeaderboard");
        if (list == null) return out;
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            Item it = new Item();
            it.id = s(o, "id");
            it.title = s(o, "title");
            it.poster = s(o, "cover");
            it.backdrop = it.poster;
            it.type = s(o, "subType");
            it.category = "MOVIE".equals(it.type) ? 0 : 1;
            if (it.id.length() > 0) out.add(it);
        }
        return out;
    }

    /* ── نتائج الوسوم ── */
    public static List<Item> parseTag(JSONObject payload) {
        List<Item> out = new ArrayList<Item>();
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) return out;
        JSONArray list = data.optJSONArray("searchContentItemList");
        if (list == null) return out;
        JSONObject wrap = new JSONObject();
        try { wrap.put("data", list); } catch (Exception ignored) { }
        return parseSearch(wrap);
    }

    /* ── عدد التعليقات ── */
    public static String parseCommentNum(JSONObject payload) {
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data == null) return "";
        return data.optString("commented", "");
    }

    /* ── تحليل عام (للردود المشفّرة) ── */
    public static List<Item> items(JSONObject payload) {
        List<Item> out = new ArrayList<Item>();
        for (JSONObject raw : Norm.extractItems(payload)) {
            JSONObject n = Norm.item(raw);
            Item it = new Item();
            it.id = n.optString("id", "");
            it.title = n.optString("title", "—");
            it.poster = n.optString("poster", "");
            it.backdrop = n.optString("backdrop", "");
            it.desc = n.optString("description", "");
            it.type = n.optString("category", "");
            it.year = n.optString("year", "");
            it.area = n.optString("area", "");
            it.score = n.optString("score", "");
            it.episodes = n.optString("episodes", "");
            out.add(it);
        }
        return out;
    }

    public static Item item(JSONObject payload, String fallbackId) {
        List<Item> list = items(payload);
        if (!list.isEmpty()) {
            Item it = list.get(0);
            if (it.id.length() == 0) it.id = fallbackId;
            return it;
        }
        JSONObject data = payload == null ? null : payload.optJSONObject("data");
        if (data != null) {
            JSONObject n = Norm.item(data);
            Item it = new Item();
            it.id = n.optString("id", fallbackId);
            it.title = n.optString("title", "—");
            it.poster = n.optString("poster", "");
            it.backdrop = n.optString("backdrop", "");
            it.desc = n.optString("description", "");
            it.type = n.optString("category", "");
            it.year = n.optString("year", "");
            it.score = n.optString("score", "");
            return it;
        }
        Item empty = new Item();
        empty.id = fallbackId;
        empty.title = "—";
        return empty;
    }

    public static List<Episode> episodes(JSONObject payload) {
        List<Episode> out = new ArrayList<Episode>();
        JSONArray a = Norm.episodes(payload);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Episode e = new Episode();
            e.id = o.optString("id", "");
            e.index = o.optString("index", String.valueOf(i + 1));
            e.title = o.optString("title", "");
            e.duration = o.optString("duration", "");
            out.add(e);
        }
        return out;
    }

    public static List<Link> links(JSONObject payload) {
        List<Link> out = new ArrayList<Link>();
        JSONArray a = Norm.streams(payload);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            Link l = new Link();
            l.label = o.optString("label", "—");
            l.url = o.optString("url", "");
            if (l.url.length() > 0) out.add(l);
        }
        return out;
    }
}
