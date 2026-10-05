package dev.gistnote.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.text.SimpleDateFormat;

public final class NoteStore {
    private static final String PREFS = "note_cache";
    private static final String NOTE_PREFIX = "note.";
    private static final String LOCAL_LIST = "cached_local_note_list";
    private static final String LOCAL_ID_PREFIX = "local-";
    private static final String WIDGET_PREFIX = "widget.";
    private static final String STACK_PREFIX = "stack.";
    private static final String CONFLICT_PREFIX = "conflict.";
    private final SharedPreferences preferences;

    public NoteStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void putRemote(GistNote note) {
        if (note == null) return;
        if (isLocalId(note.id)) {
            upsertLocalNote(note);
            return;
        }
        writeNote(new CachedNote(note.id, note.title, note.filename, note.content,
                note.updatedAt, note.version, false, System.currentTimeMillis()));
        setConflict(note.id, false);
        updateGistContent(note.id, note.content);
    }

    public boolean hasConflict(String gistId) {
        return gistId != null && preferences.getBoolean(CONFLICT_PREFIX + gistId, false);
    }

    public void setConflict(String gistId, boolean conflict) {
        if (gistId == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        if (conflict) editor.putBoolean(CONFLICT_PREFIX + gistId, true);
        else editor.remove(CONFLICT_PREFIX + gistId);
        editor.apply();
    }

    public void putDraft(CachedNote base, String content) {
        if (base == null) return;
        boolean local = isLocalId(base.gistId);
        String updatedAt = local ? nowIso() : base.updatedAt;
        writeNote(new CachedNote(base.gistId, base.title, base.filename, content,
                updatedAt, base.version, !local, base.syncedAt));
        updateGistContent(base.gistId, content);
    }

    public CachedNote getNote(String gistId) {
        String raw = preferences.getString(NOTE_PREFIX + gistId, null);
        if (raw != null) {
            try {
                JSONObject json = new JSONObject(raw);
                CachedNote cached = new CachedNote(
                        gistId,
                        json.optString("title", "Untitled note"),
                        json.optString("filename", "note.md"),
                        json.optString("content", ""),
                        json.optString("updatedAt", ""),
                        json.optString("version", ""),
                        json.optBoolean("dirty", false),
                        json.optLong("syncedAt", 0L));
                return cached;
            } catch (Exception ignored) { }
        }
        // Fallback: check the indexed local/remote note lists.
        List<GistNote> list = getAllCachedNotes();
        if (list != null) {
            for (GistNote g : list) {
                if (g.id.equals(gistId)) {
                    CachedNote note = new CachedNote(
                            gistId,
                            g.title,
                            g.filename,
                            g.content != null ? g.content : "",
                            g.updatedAt,
                            g.version,
                            false,
                            System.currentTimeMillis());
                    writeNote(note);
                    return note;
                }
            }
        }
        return null;
    }

    private void writeNote(CachedNote note) {
        try {
            JSONObject json = new JSONObject()
                    .put("title", note.title)
                    .put("filename", note.filename)
                    .put("content", note.content)
                    .put("updatedAt", note.updatedAt)
                    .put("version", note.version)
                    .put("dirty", note.dirty)
                    .put("syncedAt", note.syncedAt);
            preferences.edit().putString(NOTE_PREFIX + note.gistId, json.toString()).apply();
        } catch (Exception ignored) { }
    }

    public void updateGistContent(String gistId, String content) {
        if (gistId == null) return;
        boolean local = isLocalId(gistId);
        List<GistNote> gists = local ? getLocalNotes() : getCachedGists();
        if (gists == null) return;
        boolean changed = false;
        for (int i = 0; i < gists.size(); i++) {
            GistNote g = gists.get(i);
            if (g.id.equals(gistId)) {
                GistNote updated = g.withContent(content);
                if (local) {
                    updated = new GistNote(g.id, g.title, g.filename, content,
                            nowIso(), "", false, false, "");
                }
                gists.set(i, updated);
                changed = true;
                break;
            }
        }
        if (changed) {
            if (local) saveLocalNotes(gists);
            else saveGists(gists);
        }
    }

    public static boolean isLocalId(String noteId) {
        return noteId != null && noteId.startsWith(LOCAL_ID_PREFIX);
    }

    public GistNote createLocalNote(String title, String filename, String content) {
        String safeTitle = title == null || title.trim().isEmpty() ? "Untitled note" : title.trim();
        String safeFilename = filename == null || filename.trim().isEmpty() ? "note.md" : filename.trim();
        String safeContent = content == null ? "" : content;
        GistNote note = new GistNote(LOCAL_ID_PREFIX + UUID.randomUUID(), safeTitle,
                safeFilename, safeContent, nowIso(), "", false, false, "");
        upsertLocalNote(note);
        return note;
    }

    public void upsertLocalNote(GistNote note) {
        if (note == null || !isLocalId(note.id)) return;
        List<GistNote> notes = getLocalNotes();
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.remove(i);
                break;
            }
        }
        notes.add(0, note);
        saveLocalNotes(notes);
        writeNote(new CachedNote(note.id, note.title, note.filename,
                note.content == null ? "" : note.content, note.updatedAt, "", false, 0L));
    }

    public List<GistNote> getLocalNotes() {
        return readNotes(LOCAL_LIST);
    }

    public List<GistNote> getAllCachedNotes() {
        List<GistNote> combined = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (GistNote note : getLocalNotes()) {
            combined.add(note);
            seen.add(note.id);
        }
        List<GistNote> remote = getCachedGists();
        if (remote != null) {
            for (GistNote note : remote) {
                if (seen.add(note.id)) combined.add(note);
            }
        }
        return combined;
    }

    private void saveLocalNotes(List<GistNote> notes) {
        saveNoteList(LOCAL_LIST, notes);
    }

    public void saveGists(List<GistNote> notes) {
        if (notes == null) return;
        try {
            JSONArray array = new JSONArray();
            for (GistNote note : notes) {
                if (isLocalId(note.id)) continue;
                JSONObject obj = new JSONObject()
                        .put("id", note.id)
                        .put("title", note.title)
                        .put("filename", note.filename)
                        .put("content", note.content != null ? note.content : "")
                        .put("updatedAt", note.updatedAt)
                        .put("version", note.version)
                        .put("isPublic", note.isPublic)
                        .put("truncated", note.truncated)
                        .put("rawUrl", note.rawUrl);
                array.put(obj);

                if (note.content != null && !note.content.isEmpty()) {
                    CachedNote existing = getNote(note.id);
                    if (existing == null || !existing.dirty) {
                        writeNote(new CachedNote(note.id, note.title, note.filename, note.content,
                                note.updatedAt, note.version, false,
                                existing != null && existing.syncedAt > 0L ? existing.syncedAt : System.currentTimeMillis()));
                    }
                }
            }
            preferences.edit().putString("cached_gist_list", array.toString()).apply();
        } catch (Exception ignored) { }
    }

    public List<GistNote> getCachedGists() {
        String raw = preferences.getString("cached_gist_list", null);
        if (raw == null) return null;
        return readNotes("cached_gist_list");
    }

    private List<GistNote> readNotes(String key) {
        String raw = preferences.getString(key, null);
        if (raw == null) return new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            List<GistNote> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new GistNote(
                        obj.getString("id"),
                        obj.optString("title", "Untitled"),
                        obj.optString("filename", "note.md"),
                        obj.optString("content", ""),
                        obj.optString("updatedAt", ""),
                        obj.optString("version", ""),
                        obj.optBoolean("isPublic", false),
                        obj.optBoolean("truncated", false),
                        obj.optString("rawUrl", "")
                ));
            }
            return list;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void saveNoteList(String key, List<GistNote> notes) {
        if (notes == null) return;
        try {
            JSONArray array = new JSONArray();
            for (GistNote note : notes) {
                array.put(new JSONObject()
                        .put("id", note.id)
                        .put("title", note.title)
                        .put("filename", note.filename)
                        .put("content", note.content == null ? "" : note.content)
                        .put("updatedAt", note.updatedAt)
                        .put("version", note.version)
                        .put("isPublic", note.isPublic)
                        .put("truncated", note.truncated)
                        .put("rawUrl", note.rawUrl));
            }
            preferences.edit().putString(key, array.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static String nowIso() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    public void prependGist(GistNote note) {
        List<GistNote> gists = getCachedGists();
        if (gists == null) gists = new ArrayList<>();
        for (int i = 0; i < gists.size(); i++) {
            if (gists.get(i).id.equals(note.id)) {
                gists.remove(i);
                break;
            }
        }
        gists.add(0, note);
        saveGists(gists);
    }

    public boolean isPinned(String gistId) {
        if (gistId == null) return false;
        return preferences.getBoolean("pin." + gistId, false);
    }

    public void setPinned(String gistId, boolean pinned) {
        if (gistId == null) return;
        preferences.edit().putBoolean("pin." + gistId, pinned).apply();
    }

    public boolean togglePinned(String gistId) {
        boolean next = !isPinned(gistId);
        setPinned(gistId, next);
        return next;
    }

    public boolean isArchived(String noteId) {
        return noteId != null && preferences.getBoolean("archive." + noteId, false);
    }

    public void setArchived(String noteId, boolean archived) {
        if (noteId == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        if (archived) editor.putBoolean("archive." + noteId, true);
        else editor.remove("archive." + noteId);
        editor.apply();
    }

    public boolean toggleArchived(String noteId) {
        boolean next = !isArchived(noteId);
        setArchived(noteId, next);
        return next;
    }

    public void bindWidget(int widgetId, WidgetBinding binding) {
        try {
            JSONObject json = new JSONObject()
                    .put("gistId", binding.gistId)
                    .put("filename", binding.filename)
                    .put("color", binding.color)
                    .put("textColor", binding.textColor)
                    .put("textSize", binding.textSize)
                    .put("alignment", binding.alignment);
            preferences.edit().putString(WIDGET_PREFIX + widgetId, json.toString()).apply();
        } catch (Exception ignored) { }
    }

    public WidgetBinding getWidget(int widgetId) {
        String raw = preferences.getString(WIDGET_PREFIX + widgetId, null);
        if (raw == null) return null;
        try {
            JSONObject json = new JSONObject(raw);
            return new WidgetBinding(
                    json.getString("gistId"),
                    json.optString("filename", "note.md"),
                    json.optString("color", "Yellow"),
                    json.optString("textColor", "Auto"),
                    json.optString("textSize", "Medium"),
                    json.optString("alignment", "start"));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void removeWidget(int widgetId) {
        preferences.edit().remove(WIDGET_PREFIX + widgetId).apply();
    }

    public void bindStack(int widgetId, StackWidgetBinding binding) {
        try {
            JSONArray notes = new JSONArray();
            for (StackNote note : binding.notes) {
                JSONObject item = new JSONObject().put("gistId", note.gistId)
                        .put("filename", note.filename);
                if (note.color != null) item.put("color", note.color);
                if (note.textColor != null) item.put("textColor", note.textColor);
                if (note.textSize != null) item.put("textSize", note.textSize);
                if (note.alignment != null) item.put("alignment", note.alignment);
                notes.put(item);
            }
            JSONObject json = new JSONObject()
                    .put("notes", notes)
                    .put("color", binding.color)
                    .put("textColor", binding.textColor)
                    .put("textSize", binding.textSize);
            preferences.edit().putString(STACK_PREFIX + widgetId, json.toString()).apply();
        } catch (Exception ignored) { }
    }

    public StackWidgetBinding getStack(int widgetId) {
        String raw = preferences.getString(STACK_PREFIX + widgetId, null);
        if (raw == null) return null;
        try {
            JSONObject json = new JSONObject(raw);
            JSONArray values = json.optJSONArray("notes");
            List<StackNote> notes = new ArrayList<>();
            if (values != null) {
                for (int i = 0; i < values.length(); i++) {
                    JSONObject note = values.getJSONObject(i);
                    notes.add(new StackNote(note.getString("gistId"),
                            note.optString("filename", "note.md"),
                            note.optString("color", null), note.optString("textColor", null),
                            note.optString("textSize", null), note.optString("alignment", null)));
                }
            }
            return new StackWidgetBinding(notes,
                    json.optString("color", "Transparent"),
                    json.optString("textColor", "White"),
                    json.optString("textSize", "Medium"));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void removeStack(int widgetId) {
        preferences.edit().remove(STACK_PREFIX + widgetId).apply();
    }

    public List<Integer> stackWidgetIds() {
        List<Integer> ids = new ArrayList<>();
        for (String key : preferences.getAll().keySet()) {
            if (!key.startsWith(STACK_PREFIX)) continue;
            try { ids.add(Integer.parseInt(key.substring(STACK_PREFIX.length()))); }
            catch (NumberFormatException ignored) { }
        }
        Collections.sort(ids);
        return ids;
    }

    private static final String STACK_ACTIVE_PREFIX = "stack_active.";
    private static final String STACK_PAGE_PREFIX = "stack_page.";

    public int getStackActiveIndex(int widgetId) {
        return preferences.getInt(STACK_ACTIVE_PREFIX + widgetId, 0);
    }

    public void setStackActiveIndex(int widgetId, int index) {
        preferences.edit().putInt(STACK_ACTIVE_PREFIX + widgetId, Math.max(0, index)).apply();
    }

    private static final String STACK_SCROLL_PREFIX = "stack_scroll_";

    public int getStackLineOffset(int widgetId) {
        return preferences.getInt(STACK_SCROLL_PREFIX + widgetId, 0);
    }

    public void setStackLineOffset(int widgetId, int offset) {
        preferences.edit().putInt(STACK_SCROLL_PREFIX + widgetId, Math.max(0, offset)).apply();
    }

    public void scrollUpStack(int widgetId) {
        int current = getStackLineOffset(widgetId);
        int next = Math.max(0, current - 1);
        setStackLineOffset(widgetId, next);
    }

    public void scrollDownStack(int widgetId, int totalLines) {
        if (totalLines <= 0) {
            setStackLineOffset(widgetId, 0);
            return;
        }
        int current = getStackLineOffset(widgetId);
        int next = Math.min(Math.max(0, totalLines - 1), current + 1);
        setStackLineOffset(widgetId, next);
    }

    public void cycleStackNote(int widgetId, int delta) {
        StackWidgetBinding stack = getStack(widgetId);
        if (stack == null || stack.notes.isEmpty()) return;
        int total = stack.notes.size();
        int current = getStackActiveIndex(widgetId);
        int next = (current + delta) % total;
        if (next < 0) next += total;
        setStackActiveIndex(widgetId, next);
        setStackLineOffset(widgetId, 0);
    }

    public List<Integer> singleWidgetIds() {
        List<Integer> ids = new ArrayList<>();
        for (String key : preferences.getAll().keySet()) {
            if (!key.startsWith(WIDGET_PREFIX)) continue;
            try { ids.add(Integer.parseInt(key.substring(WIDGET_PREFIX.length()))); }
            catch (NumberFormatException ignored) { }
        }
        Collections.sort(ids);
        return ids;
    }

    private static final String PENDING_STACK_NOTES = "pending_stack_notes";

    public void setPendingStackNotes(List<String> gistIds) {
        if (gistIds == null || gistIds.isEmpty()) {
            clearPendingStackNotes();
            return;
        }
        preferences.edit().putString(PENDING_STACK_NOTES, new org.json.JSONArray(gistIds).toString()).apply();
    }

    public List<String> getPendingStackNotes() {
        String raw = preferences.getString(PENDING_STACK_NOTES, null);
        if (raw == null) return new ArrayList<>();
        List<String> list = new ArrayList<>();
        try {
            org.json.JSONArray array = new org.json.JSONArray(raw);
            for (int i = 0; i < array.length(); i++) list.add(array.getString(i));
        } catch (Exception ignored) { }
        return list;
    }

    public void clearPendingStackNotes() {
        preferences.edit().remove(PENDING_STACK_NOTES).apply();
    }

    public boolean hasPendingStackNotes() {
        return preferences.contains(PENDING_STACK_NOTES);
    }

    public static final class CachedNote {
        public final String gistId;
        public final String title;
        public final String filename;
        public final String content;
        public final String updatedAt;
        public final String version;
        public final boolean dirty;
        public final long syncedAt;

        public CachedNote(String gistId, String title, String filename, String content,
                          String updatedAt, String version, boolean dirty) {
            this(gistId, title, filename, content, updatedAt, version, dirty, 0L);
        }

        public CachedNote(String gistId, String title, String filename, String content,
                          String updatedAt, String version, boolean dirty, long syncedAt) {
            this.gistId = gistId;
            this.title = title;
            this.filename = filename;
            this.content = content;
            this.updatedAt = updatedAt;
            this.version = version;
            this.dirty = dirty;
            this.syncedAt = syncedAt;
        }
    }

    public static final class WidgetBinding {
        public final String gistId;
        public final String filename;
        public final String color;
        public final String textColor;
        public final String textSize;
        public final String alignment;

        public WidgetBinding(String gistId, String filename, String color, String textSize) {
            this(gistId, filename, color, "Auto", textSize);
        }

        public WidgetBinding(String gistId, String filename, String color,
                             String textColor, String textSize) {
            this(gistId, filename, color, textColor, textSize, "start");
        }

        public WidgetBinding(String gistId, String filename, String color,
                             String textColor, String textSize, String alignment) {
            this.gistId = gistId;
            this.filename = filename;
            this.color = color;
            this.textColor = textColor;
            this.textSize = textSize;
            this.alignment = alignment == null ? "start" : alignment;
        }
    }

    public static final class StackNote {
        public final String gistId;
        public final String filename;
        public final String color;
        public final String textColor;
        public final String textSize;
        public final String alignment;

        public StackNote(String gistId, String filename) {
            this(gistId, filename, null, null, null);
        }

        public StackNote(String gistId, String filename, String color,
                         String textColor, String textSize) {
            this(gistId, filename, color, textColor, textSize, null);
        }

        public StackNote(String gistId, String filename, String color,
                         String textColor, String textSize, String alignment) {
            this.gistId = gistId;
            this.filename = filename;
            this.color = color;
            this.textColor = textColor;
            this.textSize = textSize;
            this.alignment = alignment;
        }

        public StackNote withAppearance(WidgetBinding appearance) {
            return new StackNote(gistId, filename, appearance.color,
                    appearance.textColor, appearance.textSize, appearance.alignment);
        }
    }

    public static final class StackWidgetBinding {
        public final List<StackNote> notes;
        public final String color;
        public final String textColor;
        public final String textSize;

        public StackWidgetBinding(List<StackNote> notes, String color,
                                  String textColor, String textSize) {
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
            this.color = color;
            this.textColor = textColor;
            this.textSize = textSize;
        }

        public WidgetBinding appearanceFor(StackNote note) {
            return new WidgetBinding(note.gistId, note.filename,
                    note.color == null ? color : note.color,
                    note.textColor == null ? textColor : note.textColor,
                    note.textSize == null ? textSize : note.textSize,
                    note.alignment == null ? "start" : note.alignment);
        }

        public StackWidgetBinding withAppearance(String gistId, WidgetBinding appearance) {
            List<StackNote> updated = new ArrayList<>();
            for (StackNote note : notes) {
                updated.add(note.gistId.equals(gistId) ? note.withAppearance(appearance) : note);
            }
            return new StackWidgetBinding(updated, color, textColor, textSize);
        }

        public StackWidgetBinding withAdded(GistNote note) {
            if (contains(note.id)) return this;
            List<StackNote> updated = new ArrayList<>(notes);
            updated.add(new StackNote(note.id, note.filename));
            return new StackWidgetBinding(updated, color, textColor, textSize);
        }

        public StackWidgetBinding withNotes(List<StackNote> newNotes) {
            return new StackWidgetBinding(newNotes, color, textColor, textSize);
        }

        public boolean contains(String gistId) {
            for (StackNote note : notes) if (note.gistId.equals(gistId)) return true;
            return false;
        }
    }
}
