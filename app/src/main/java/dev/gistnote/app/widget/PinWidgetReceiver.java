package dev.gistnote.app.widget;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.gistnote.app.data.NoteStore;

public final class PinWidgetReceiver extends BroadcastReceiver {
    public static final String ACTION_PINNED = "dev.gistnote.app.WIDGET_PINNED";
    public static final String ACTION_STACK_PINNED = "dev.gistnote.app.STACK_WIDGET_PINNED";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        int widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;

        NoteStore store = new NoteStore(context);

        if (ACTION_PINNED.equals(action)) {
            String gistId = intent.getStringExtra("gist_id");
            String filename = intent.getStringExtra("filename");
            if (gistId == null) return;
            store.bindWidget(widgetId, new NoteStore.WidgetBinding(
                    gistId, filename == null ? "note.md" : filename,
                    "Transparent", "White", "Medium"));
            NoteWidgetProvider.update(context, AppWidgetManager.getInstance(context), widgetId);
        } else if (ACTION_STACK_PINNED.equals(action)) {
            java.util.List<String> gistIds = intent.getStringArrayListExtra("gist_ids");
            if (gistIds == null || gistIds.isEmpty()) {
                gistIds = store.getPendingStackNotes();
            }
            if (gistIds == null || gistIds.isEmpty()) return;

            java.util.List<NoteStore.StackNote> stackNotes = new java.util.ArrayList<>();
            for (String id : gistIds) {
                NoteStore.CachedNote note = store.getNote(id);
                String filename = (note != null && note.filename != null) ? note.filename : "note.md";
                stackNotes.add(new NoteStore.StackNote(id, filename));
            }
            store.bindStack(widgetId, new NoteStore.StackWidgetBinding(
                    stackNotes, "Transparent", "White", "Medium"));
            store.clearPendingStackNotes();
            StackNoteWidgetProvider.update(context, AppWidgetManager.getInstance(context), widgetId);
        }
    }
}
