package com.movieflix.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** طبقة تطبيع: تستخرج بيانات نظيفة من ردود الخدمة مهما اختلف شكلها. */
public class Norm {

    static final String[] TITLE = {"title", "name", "contentName", "albumName", "videoName", "showName",
            "programName", "resourceName", "cName", "episodeName"};
    static final String[] POSTER = {"posterUrl", "poster", "coverUrl", "cover", "verticalImg", "vPic",
            "img", "image", "imageUrl", "pic", "picUrl", "thumbnail", "albumImg"};
    static final String[] BACKDROP = {"backdropUrl", "backdrop", "horizontalImg", "hPic", "bigPic",
            "bannerUrl", "banner", "wideImg", "landscapeImg"};
    static final String[] DESC = {"description", "desc", "summary", "brief", "introduction",
            "contentDesc", "intro", "albumDesc", "remark"};
    static final String[] ID = {"contentId", "albumId", "id", "cid", "videoId", "contentID", "resourceId"};
    static final String[] EPID = {"episodeId", "eid", "playId", "videoId", "id"};
    static final String[] CAT = {"category", "categoryName", "contentType", "type", "catName"};
    static final String[] SCORE = {"score", "rating", "mark", "imdb", "doubanScore", "grade"};
    static final String[] YEAR = {"year", "releaseYear", "publishYear", "releaseDate", "publishTime"};
    static final String[] AREA = {"area", "country", "region", "areaName", "countryName"};
    static final String[] EPCOUNT = {"episodeCount", "totalEpisode", "episodesNum", "num", "total",
            "updateInfo", "episodeNum"};

    static final String[] LIST_KEYS = {"list", "records", "rows", "items", "results", "data", "contentList",
            "albumList", "videoList", "episodeList", "episodes", "result", "searchList", "recommendList",
            "modules", "sections"};

    static final String[] NESTS = {"content", "album", "detail", "info", "baseInfo", "contentInfo", "albumInfo"};

    static final String[] URL_KEYS = {"url", "playurl", "m3u8url", "mp4url", "downloadurl", "src", "path",
            "playaddr", "address", "fileurl", "videourl", "hlsurl"};
    static final String[] MEDIA_EXT = {".m3u8", ".mp4", ".mkv", ".flv", ".ts", ".mov", ".m4v"};

    /* ───────── أدوات ───────── */

    static JSONObject flatten(JSONObject o) {
        JSONObject out = new JSONObject();
        try {
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String k = it.next();
                out.put(k, o.opt(k));
            }
            for (String nest : NESTS) {
                JSONObject inner = o.optJSONObject(nest);
                if (inner == null) continue;
                for (Iterator<String> it = inner.keys(); it.hasNext(); ) {
                    String k = it.next();
                    if (!out.has(k)) out.put(k, inner.opt(k));
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    static String pick(JSONObject o, String[] keys) {
        if (o == null) return null;
        for (String k : keys) {
            if (!o.has(k)) continue;
            Object v = o.opt(k);
            if (v == null || v == JSONObject.NULL) continue;
            String s;
            if (v instanceof String) s = ((String) v).trim();
            else if (v instanceof Number || v instanceof Boolean) s = String.valueOf(v);
            else continue;
            if (s.length() > 0) return s;
        }
        return null;
    }

    static boolean hasTitle(JSONObject o) {
        return pick(flatten(o), TITLE) != null;
    }

    static void findLists(Object o, int depth, List<List<JSONObject>> acc) {
        if (depth > 6 || o == null) return;
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            List<JSONObject> objs = new ArrayList<JSONObject>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject jo = a.optJSONObject(i);
                if (jo != null) objs.add(jo);
            }
            if (!objs.isEmpty()) {
                boolean any = false;
                for (JSONObject jo : objs) if (hasTitle(jo)) { any = true; break; }
                if (any) acc.add(objs);
            }
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                if (v instanceof JSONObject || v instanceof JSONArray) findLists(v, depth + 1, acc);
            }
        } else if (o instanceof JSONObject) {
            JSONObject jo = (JSONObject) o;
            for (Iterator<String> it = jo.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = jo.opt(k);
                if (v instanceof JSONObject || v instanceof JSONArray) findLists(v, depth + 1, acc);
            }
        }
    }

    static List<JSONObject> largest(List<List<JSONObject>> lists) {
        List<JSONObject> best = null;
        for (List<JSONObject> l : lists) if (best == null || l.size() > best.size()) best = l;
        return best;
    }

    static List<JSONObject> extractItems(JSONObject payload) {
        if (payload == null) return new ArrayList<JSONObject>();
        Object data = payload.opt("data");
        if (data instanceof JSONArray) {
            List<JSONObject> out = new ArrayList<JSONObject>();
            JSONArray a = (JSONArray) data;
            for (int i = 0; i < a.length(); i++) {
                JSONObject jo = a.optJSONObject(i);
                if (jo != null) out.add(jo);
            }
            if (!out.isEmpty()) return out;
        }
        if (data instanceof JSONObject) {
            JSONObject d = (JSONObject) data;
            for (String k : LIST_KEYS) {
                JSONArray a = d.optJSONArray(k);
                if (a == null || a.length() == 0) continue;
                List<JSONObject> out = new ArrayList<JSONObject>();
                for (int i = 0; i < a.length(); i++) {
                    JSONObject jo = a.optJSONObject(i);
                    if (jo != null) out.add(jo);
                }
                if (!out.isEmpty()) return out;
            }
        }
        List<List<JSONObject>> acc = new ArrayList<List<JSONObject>>();
        findLists(data, 0, acc);
        if (acc.isEmpty()) findLists(payload, 0, acc);
        List<JSONObject> best = largest(acc);
        return best != null ? best : new ArrayList<JSONObject>();
    }

    static JSONObject item(JSONObject raw) {
        JSONObject out = new JSONObject();
        JSONObject d = flatten(raw);
        try {
            out.put("id", nz(pick(d, ID)));
            out.put("title", nz(pick(d, TITLE), "—"));
            out.put("poster", nz(pick(d, POSTER)));
            String bd = pick(d, BACKDROP);
            out.put("backdrop", bd != null ? bd : nz(pick(d, POSTER)));
            out.put("description", nz(pick(d, DESC)));
            out.put("category", nz(pick(d, CAT)));
            String y = pick(d, YEAR);
            out.put("year", y != null && y.length() >= 4 ? y.substring(0, 4) : nz(y));
            out.put("area", nz(pick(d, AREA)));
            out.put("score", normScore(pick(d, SCORE)));
            out.put("episodes", nz(pick(d, EPCOUNT)));
        } catch (Exception ignored) { }
        return out;
    }

    static String normScore(String s) {
        if (s == null) return "";
        try {
            double f = Double.parseDouble(s.replaceAll("[^0-9.]", ""));
            if (f > 10) f = f / 10.0;
            return String.format(java.util.Locale.US, "%.1f", f);
        } catch (Exception e) {
            return "";
        }
    }

    static String nz(String s) { return s == null ? "" : s; }
    static String nz(String s, String def) { return s == null || s.length() == 0 ? def : s; }

    static JSONArray episodes(JSONObject payload) {
        JSONArray out = new JSONArray();
        if (payload == null) return out;
        List<JSONObject> src = null;
        Object data = payload.opt("data");
        if (data instanceof JSONObject) {
            JSONObject d = (JSONObject) data;
            String[] keys = {"episodeList", "episodes", "videoList", "list", "records", "items"};
            for (String k : keys) {
                JSONArray a = d.optJSONArray(k);
                if (a != null && a.length() > 0) {
                    src = new ArrayList<JSONObject>();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject jo = a.optJSONObject(i);
                        if (jo != null) src.add(jo);
                    }
                    break;
                }
            }
        }
        if (src == null) {
            List<List<JSONObject>> acc = new ArrayList<List<JSONObject>>();
            findLists(data, 0, acc);
            if (acc.isEmpty()) findLists(payload, 0, acc);
            src = largest(acc);
        }
        if (src == null) return out;
        for (int i = 0; i < src.size(); i++) {
            JSONObject raw = src.get(i);
            JSONObject d = flatten(raw);
            JSONObject e = new JSONObject();
            try {
                e.put("id", nz(pick(d, EPID)));
                String idx = pick(d, new String[]{"index", "sort", "episodeNo", "episode", "no", "order", "num"});
                e.put("index", idx != null ? idx : String.valueOf(i + 1));
                String title = pick(d, new String[]{"title", "name", "episodeName", "videoName"});
                e.put("title", title != null ? title : String.valueOf(i + 1));
                e.put("duration", nz(pick(d, new String[]{"duration", "timeLength", "length", "runtime"})));
                e.put("poster", nz(pick(d, POSTER)));
            } catch (Exception ignored) { }
            out.put(e);
        }
        return out;
    }

    static boolean isMediaUrl(String key, String url) {
        String path = url.toLowerCase();
        int q = path.indexOf('?');
        if (q > 0) path = path.substring(0, q);
        for (String ext : MEDIA_EXT) if (path.endsWith(ext)) return true;
        String k = key.toLowerCase();
        for (String u : URL_KEYS) if (u.equals(k)) return true;
        return false;
    }

    static void walkUrls(Object o, String hint, int depth, JSONArray out, List<String> seen) {
        if (depth > 8 || o == null) return;
        if (o instanceof JSONObject) {
            JSONObject jo = (JSONObject) o;
            for (Iterator<String> it = jo.keys(); it.hasNext(); ) {
                String k = it.next();
                Object v = jo.opt(k);
                if (v instanceof String) {
                    String s = ((String) v).trim();
                    if (s.startsWith("http://") || s.startsWith("https://")) {
                        if (!isMediaUrl(k, s) || seen.contains(s)) continue;
                        seen.add(s);
                        String label = pick(jo, new String[]{"definition", "quality", "name", "resolution", "label"});
                        if (label == null) label = hint != null && hint.length() > 0 ? hint : k;
                        JSONObject e = new JSONObject();
                        try {
                            e.put("label", label);
                            e.put("key", k);
                            e.put("url", s);
                            e.put("size", "");
                        } catch (Exception ignored) { }
                        out.put(e);
                    }
                } else if (v instanceof JSONObject || v instanceof JSONArray) {
                    walkUrls(v, k, depth + 1, out, seen);
                }
            }
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                if (v instanceof JSONObject || v instanceof JSONArray) walkUrls(v, hint, depth + 1, out, seen);
            }
        }
    }

    static JSONArray streams(JSONObject payload) {
        JSONArray out = new JSONArray();
        walkUrls(payload, "", 0, out, new ArrayList<String>());
        return out;
    }
}
