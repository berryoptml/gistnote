package dev.gistnote.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

public final class GistNote {
    public final String id;
    public final String title;
    public final String filename;
    public final String content;
    public final String updatedAt;
    public final String version;
    public final boolean isPublic;
    public final boolean truncated;
    public final String rawUrl;

    public GistNote(String id, String title, String filename, String content,
                    String updatedAt, String version, boolean isPublic,
                    boolean truncated, String rawUrl) {
        this.id = id;
        this.title = title;
        this.filename = filename;
        this.content = content;
        this.updatedAt = updatedAt;
        this.version = version;
        this.isPublic = isPublic;
        this.truncated = truncated;
        this.rawUrl = rawUrl;
    }

    public static GistNote fromJson(JSONObject json, String preferredFilename) throws JSONException {
        JSONObject files = json.getJSONObject("files");
        String chosen = chooseFilename(files, preferredFilename);
        JSONObject file = files.getJSONObject(chosen);
        String title = json.optString("description", "").trim();
        if (title.isEmpty()) title = chosen;

        String version = "";
        JSONArray history = json.optJSONArray("history");
        if (history != null && history.length() > 0) {
            version = history.getJSONObject(0).optString("version", "");
        }

        return new GistNote(
                json.getString("id"),
                title,
                chosen,
                file.optString("content", ""),
                json.optString("updated_at", ""),
                version,
                json.optBoolean("public", false),
                file.optBoolean("truncated", false),
                file.optString("raw_url", "")
        );
    }

    private static String chooseFilename(JSONObject files, String preferred) throws JSONException {
        if (preferred != null && !preferred.isEmpty() && files.has(preferred)) return preferred;
        String first = null;
        Iterator<String> names = files.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (first == null) first = name;
            String lowercase = name.toLowerCase(Locale.ROOT);
            if (lowercase.endsWith(".md") || lowercase.endsWith(".markdown")) {
                return name;
            }
        }
        if (first == null) throw new JSONException("Gist contains no files");
        return first;
    }

    public GistNote withContent(String fullContent) {
        return new GistNote(id, title, filename, fullContent, updatedAt, version,
                isPublic, false, rawUrl);
    }
}
