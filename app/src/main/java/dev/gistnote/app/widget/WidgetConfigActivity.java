package dev.gistnote.app.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GistNote;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.ui.GistAdapter;
import dev.gistnote.app.ui.SystemBars;

public final class WidgetConfigActivity extends Activity {
    private static final String[] COLORS = {"Transparent", "Yellow", "Peach", "Mint", "Blue", "Charcoal"};
    private static final String[] TEXT_COLORS = {"Auto", "Black", "White", "Brown", "Blue", "Green"};
    private static final String[] SIZES = {"Small", "Medium", "Large"};
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private GistNote selected;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);
        SystemBars.apply(findViewById(R.id.widget_config_root));
        widgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        TextView status = findViewById(R.id.widget_config_status);
        ListView list = findViewById(R.id.widget_gist_list);
        Spinner color = findViewById(R.id.widget_color);
        Spinner textColor = findViewById(R.id.widget_text_color);
        Spinner size = findViewById(R.id.widget_text_size);
        Button add = findViewById(R.id.add_widget_button);
        GistAdapter adapter = new GistAdapter(this, false);
        list.setAdapter(adapter);
        color.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, COLORS));
        textColor.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TEXT_COLORS));
        size.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SIZES));

        NoteStore store = new NoteStore(this);
        NoteStore.WidgetBinding existing = store.getWidget(widgetId);
        if (existing != null) {
            // Reconfiguring an existing widget is an appearance edit; preserve its
            // selected gist and avoid forcing a network-backed gist picker.
            startActivity(new Intent(this, WidgetAppearanceActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            finish();
            return;
        } else {
            color.setSelection(indexOf(COLORS, "Transparent"));
            textColor.setSelection(indexOf(TEXT_COLORS, "White"));
            size.setSelection(1);
        }

        list.setOnItemClickListener((parent, view, position, id) -> {
            selected = adapter.getItem(position);
            status.setText("Selected: " + selected.title);
            list.setItemChecked(position, true);
        });
        add.setOnClickListener(v -> {
            if (selected == null) {
                status.setText("Choose a note first.");
                return;
            }
            if (selected.isPublic) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.public_widget_title)
                        .setMessage(R.string.public_widget_warning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.add_public_widget,
                                (dialog, which) -> saveBinding(store, color, textColor, size))
                        .show();
                return;
            }
            saveBinding(store, color, textColor, size);
        });

        List<GistNote> cached = store.getAllCachedNotes();
        adapter.setNotes(cached);
        status.setText(cached.isEmpty() ? "Create a note in GistNote first."
                : "Select one note for this widget.");

        if (!new TokenStore(this).hasToken()) return;
        executor.execute(() -> {
            try {
                List<GistNote> notes = new GitHubApi(new TokenStore(this).getToken()).listGists();
                store.saveGists(notes);
                runOnUiThread(() -> {
                    List<GistNote> available = store.getAllCachedNotes();
                    adapter.setNotes(available);
                    status.setText(available.isEmpty() ? "No notes found." : "Select one note for this widget.");
                    if (existing != null) {
                        for (int i = 0; i < available.size(); i++) {
                            if (available.get(i).id.equals(existing.gistId)) {
                                selected = available.get(i);
                                list.setItemChecked(i, true);
                                status.setText("Selected: " + selected.title);
                                break;
                            }
                        }
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> status.setText(error.getMessage()));
            }
        });
    }

    private void saveBinding(NoteStore store, Spinner color, Spinner textColor, Spinner size) {
        if (selected == null) return;
        NoteStore.WidgetBinding binding = new NoteStore.WidgetBinding(
                selected.id, selected.filename,
                COLORS[color.getSelectedItemPosition()],
                TEXT_COLORS[textColor.getSelectedItemPosition()],
                SIZES[size.getSelectedItemPosition()]);
        store.bindWidget(widgetId, binding);
        NoteStore.CachedNote cached = store.getNote(selected.id);
        if (cached == null && !selected.content.isEmpty()) store.putRemote(selected);
        NoteWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId);
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
