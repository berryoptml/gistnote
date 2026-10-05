package dev.gistnote.app.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.ui.SystemBars;

public final class StackWidgetConfigActivity extends Activity {
    private static final String[] COLORS = {
            "Transparent", "Yellow", "Peach", "Mint", "Blue", "Charcoal"};
    private static final String[] TEXT_COLORS = {
            "Auto", "Black", "White", "Brown", "Blue", "Green"};
    private static final String[] SIZES = {"Small", "Medium", "Large"};
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<GistNote> notes = new ArrayList<>();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private ListView list;
    private TextView status;
    private Spinner color;
    private Spinner textColor;
    private Spinner size;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_stack_widget_config);
        SystemBars.apply(findViewById(R.id.stack_config_root));
        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        status = findViewById(R.id.stack_config_status);
        list = findViewById(R.id.stack_gist_list);
        color = findViewById(R.id.stack_color);
        textColor = findViewById(R.id.stack_text_color);
        size = findViewById(R.id.stack_text_size);
        Button save = findViewById(R.id.save_stack_button);
        color.setAdapter(adapter(COLORS));
        textColor.setAdapter(adapter(TEXT_COLORS));
        size.setAdapter(adapter(SIZES));

        NoteStore store = new NoteStore(this);
        if (store.hasPendingStackNotes()) {
            List<String> pendingIds = store.getPendingStackNotes();
            if (!pendingIds.isEmpty()) {
                List<NoteStore.StackNote> stackNotes = new ArrayList<>();
                for (String gistId : pendingIds) {
                    NoteStore.CachedNote note = store.getNote(gistId);
                    String filename = (note != null && note.filename != null) ? note.filename : "note.md";
                    stackNotes.add(new NoteStore.StackNote(gistId, filename));
                }
                store.bindStack(widgetId, new NoteStore.StackWidgetBinding(
                        stackNotes, "Transparent", "White", "Medium"));
                store.clearPendingStackNotes();
                StackNoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
                Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
                setResult(RESULT_OK, result);
                finish();
                return;
            }
        }
        NoteStore.StackWidgetBinding existing = store.getStack(widgetId);
        if (existing == null) {
            color.setSelection(indexOf(COLORS, "Transparent"));
            textColor.setSelection(indexOf(TEXT_COLORS, "White"));
            size.setSelection(indexOf(SIZES, "Medium"));
        } else {
            color.setSelection(indexOf(COLORS, existing.color));
            textColor.setSelection(indexOf(TEXT_COLORS, existing.textColor));
            size.setSelection(indexOf(SIZES, existing.textSize));
        }
        list.setOnItemClickListener((parent, view, position, id) -> showSelectedCount());
        save.setOnClickListener(v -> validateAndSave(store));

        load(existing);
    }

    private ArrayAdapter<String> adapter(String[] values) {
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    }

    private void load(NoteStore.StackWidgetBinding existing) {
        NoteStore store = new NoteStore(this);
        showNotes(store.getAllCachedNotes(), existing);
        if (!new TokenStore(this).hasToken()) return;
        executor.execute(() -> {
            try {
                List<GistNote> loaded = new GitHubApi(new TokenStore(this).getToken()).listGists();
                store.saveGists(loaded);
                runOnUiThread(() -> {
                    showNotes(store.getAllCachedNotes(), existing);
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void showNotes(List<GistNote> loaded, NoteStore.StackWidgetBinding existing) {
        notes.clear();
        if (loaded != null) notes.addAll(loaded);
        List<String> labels = new ArrayList<>();
        for (GistNote note : notes) {
            labels.add(note.title + (NoteStore.isLocalId(note.id) ? " · Local" : ""));
        }
        list.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, labels));
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        if (existing != null) {
            Set<String> selected = new HashSet<>();
            for (NoteStore.StackNote note : existing.notes) selected.add(note.gistId);
            for (int i = 0; i < notes.size(); i++) {
                if (selected.contains(notes.get(i).id)) list.setItemChecked(i, true);
            }
        }
        showSelectedCount();
    }

    private void showSelectedCount() {
        int count = selectedNotes().size();
        status.setText(count == 0 ? "Select one or more notes."
                : count + " note" + (count == 1 ? "" : "s") + " selected");
    }

    private List<GistNote> selectedNotes() {
        List<GistNote> selected = new ArrayList<>();
        SparseBooleanArray checked = list.getCheckedItemPositions();
        for (int i = 0; i < notes.size(); i++) if (checked.get(i)) selected.add(notes.get(i));
        return selected;
    }

    private void validateAndSave(NoteStore store) {
        List<GistNote> selected = selectedNotes();
        if (selected.size() < 1) {
            status.setText("Select at least one note for a stack.");
            return;
        }
        boolean containsPublic = false;
        for (GistNote note : selected) if (note.isPublic) containsPublic = true;
        if (containsPublic) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.public_widget_title)
                    .setMessage(R.string.public_widget_warning)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.add_public_widget,
                            (dialog, which) -> save(store, selected))
                    .show();
            return;
        }
        save(store, selected);
    }

    private void save(NoteStore store, List<GistNote> selected) {
        List<NoteStore.StackNote> bindings = new ArrayList<>();
        for (GistNote note : selected) {
            bindings.add(new NoteStore.StackNote(note.id, note.filename));
            if (!note.content.isEmpty()) store.putRemote(note);
        }
        store.bindStack(widgetId, new NoteStore.StackWidgetBinding(bindings,
                COLORS[color.getSelectedItemPosition()],
                TEXT_COLORS[textColor.getSelectedItemPosition()],
                SIZES[size.getSelectedItemPosition()]));
        StackNoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private static int indexOf(String[] values, String target) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(target)) return i;
        return 0;
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
