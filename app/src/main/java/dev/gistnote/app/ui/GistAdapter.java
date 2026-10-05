package dev.gistnote.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.NoteStore;

public final class GistAdapter extends BaseAdapter {
    public interface OnPinClickListener {
        void onPinClick(GistNote note);
    }

    public interface OnArchiveClickListener {
        void onArchiveClick(GistNote note);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final NoteStore noteStore;
    private OnPinClickListener pinClickListener;
    private OnArchiveClickListener archiveClickListener;
    private boolean grid;
    private List<GistNote> notes = new ArrayList<>();
    private boolean selectionMode;
    private final java.util.Set<String> selectedGistIds = new java.util.LinkedHashSet<>();

    public GistAdapter(Context context, boolean grid) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.noteStore = new NoteStore(context);
        this.grid = grid;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        if (!selectionMode) selectedGistIds.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void toggleSelection(String gistId) {
        if (selectedGistIds.contains(gistId)) {
            selectedGistIds.remove(gistId);
        } else {
            selectedGistIds.add(gistId);
        }
        notifyDataSetChanged();
    }

    public void selectNote(String gistId, boolean select) {
        if (select) selectedGistIds.add(gistId);
        else selectedGistIds.remove(gistId);
        notifyDataSetChanged();
    }

    public java.util.Set<String> getSelectedIds() {
        return new java.util.LinkedHashSet<>(selectedGistIds);
    }

    public List<GistNote> getSelectedNotes() {
        List<GistNote> selected = new ArrayList<>();
        for (GistNote note : notes) {
            if (selectedGistIds.contains(note.id)) selected.add(note);
        }
        return selected;
    }

    public void selectAll() {
        for (GistNote note : notes) selectedGistIds.add(note.id);
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedGistIds.clear();
        notifyDataSetChanged();
    }

    public void setOnPinClickListener(OnPinClickListener listener) {
        this.pinClickListener = listener;
    }

    public void setOnArchiveClickListener(OnArchiveClickListener listener) {
        this.archiveClickListener = listener;
    }

    public void setGrid(boolean grid) { this.grid = grid; notifyDataSetChanged(); }

    public void setNotes(List<GistNote> notes) {
        this.notes = notes == null ? new ArrayList<>() : new ArrayList<>(notes);
        notifyDataSetChanged();
    }

    public List<GistNote> getNotes() {
        return new ArrayList<>(notes);
    }

    @Override public int getCount() { return notes.size(); }
    @Override public GistNote getItem(int position) { return notes.get(position); }
    @Override public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int layout = grid ? R.layout.item_gist_grid : R.layout.item_gist;
        View view = convertView == null || (Integer) convertView.getTag() != layout
                ? inflater.inflate(layout, parent, false) : convertView;
        view.setTag(layout);
        if (grid && parent instanceof GridView) {
            GridView gridView = (GridView) parent;
            int available = gridView.getWidth()
                    - gridView.getPaddingLeft() - gridView.getPaddingRight();
            int columnWidth = (available - gridView.getHorizontalSpacing()) / 2;
            if (columnWidth > 0) {
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = columnWidth;
                params.height = columnWidth;
                view.setLayoutParams(params);
            }
        }
        GistNote note = getItem(position);

        boolean isSelected = selectionMode && selectedGistIds.contains(note.id);
        view.setBackgroundResource(isSelected ? R.drawable.home_card_bg_selected : R.drawable.home_card_bg);

        TextView title = view.findViewById(R.id.gist_title);
        title.setText(note.title);
        title.setVisibility(note.title == null || note.title.isEmpty() || note.title.equals(note.filename) ? View.INVISIBLE : View.VISIBLE);
        android.widget.ImageView privacy = view.findViewById(R.id.gist_privacy);
        boolean local = NoteStore.isLocalId(note.id);
        privacy.setImageResource(local ? R.drawable.ic_local_note
                : (note.isPublic ? R.drawable.ic_public_gist : R.drawable.ic_private_gist));
        privacy.setContentDescription(local ? "Local note"
                : (note.isPublic ? "Public gist" : "Secret gist"));
        privacy.setColorFilter(view.getResources().getColor(local ? R.color.home_muted
                : (note.isPublic ? R.color.home_public : R.color.home_private)));

        android.widget.ImageView pin = view.findViewById(R.id.gist_pin);
        android.widget.ImageView archive = view.findViewById(R.id.gist_archive);
        android.widget.ImageView selectCheckbox = view.findViewById(R.id.gist_select_checkbox);

        if (selectionMode) {
            if (pin != null) pin.setVisibility(View.GONE);
            if (archive != null) archive.setVisibility(View.GONE);
            if (selectCheckbox != null) {
                selectCheckbox.setVisibility(View.VISIBLE);
                selectCheckbox.setImageResource(isSelected
                        ? R.drawable.ic_check_circle_selected
                        : R.drawable.ic_check_circle_unselected);
            }
        } else {
            if (selectCheckbox != null) selectCheckbox.setVisibility(View.GONE);
            if (pin != null) {
                pin.setVisibility(View.VISIBLE);
                boolean isPinned = noteStore.isPinned(note.id);
                pin.setImageResource(isPinned ? R.drawable.ic_pin_filled : R.drawable.ic_pin);
                pin.setColorFilter(view.getResources().getColor(
                        isPinned ? R.color.home_text : R.color.home_muted));
                pin.setContentDescription(isPinned ? "Unpin note" : "Pin note");
                pin.setOnClickListener(v -> {
                    if (pinClickListener != null) {
                        pinClickListener.onPinClick(note);
                    }
                });
            }
            if (archive != null) {
                if (local) {
                    archive.setVisibility(View.VISIBLE);
                    boolean isArchived = noteStore.isArchived(note.id);
                    archive.setImageResource(isArchived ? R.drawable.ic_unarchive : R.drawable.ic_archive);
                    archive.setColorFilter(view.getResources().getColor(R.color.home_muted));
                    archive.setContentDescription(isArchived ? "Unarchive local note" : "Archive local note");
                    archive.setOnClickListener(v -> {
                        if (archiveClickListener != null) archiveClickListener.onArchiveClick(note);
                    });
                } else {
                    archive.setVisibility(View.GONE);
                    archive.setOnClickListener(null);
                }
            }
        }

        TextView date = view.findViewById(R.id.gist_date);
        date.setText(compactDate(note.updatedAt));
        TextView preview = view.findViewById(R.id.gist_preview);
        if (preview != null) preview.setText(preview(note.content));
        return view;
    }

    static String preview(String content) {
        if (content == null || content.isEmpty()) return "Empty note";
        String compact = content.replaceAll("(?m)^(\\s*)[-*] \\[ \\] ", "$1☐ ")
                .replaceAll("(?m)^(\\s*)[-*] \\[[xX]\\] ", "$1☑ ").trim();
        if (compact.isEmpty()) return "Empty note";
        return compact.length() > 600 ? compact.substring(0, 600) + "…" : compact;
    }

    static String compactDate(String value) {
        if (value == null || value.isEmpty()) return "not synced";
        try {
            java.text.SimpleDateFormat source = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ROOT);
            source.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = source.parse(value);
            return "Edited " + new java.text.SimpleDateFormat("MMM d · h:mm a", java.util.Locale.getDefault()).format(date);
        } catch (java.text.ParseException error) {
            return "Edited time unavailable";
        }
    }

    public void updatePreview(String gistId, String content) {
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(gistId)) notes.set(i, notes.get(i).withContent(content));
        }
        notifyDataSetChanged();
    }
}
