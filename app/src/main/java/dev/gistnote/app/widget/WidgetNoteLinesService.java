package dev.gistnote.app.widget;

import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

import dev.gistnote.app.R;
import dev.gistnote.app.data.MarkdownTasks;
import dev.gistnote.app.data.NoteStore;

/** Supplies tappable task rows and wrapped prose blocks to both single and stack widgets. */
public final class WidgetNoteLinesService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new LinesFactory(getApplicationContext(), intent);
    }

    /** Android 12+ can send rows inline, avoiding a launcher service bind/loading view. */
    @TargetApi(Build.VERSION_CODES.S)
    static RemoteViews.RemoteCollectionItems inlineItems(Context context, int widgetId) {
        RenderData data = load(context, widgetId);
        RemoteViews.RemoteCollectionItems.Builder builder =
                new RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        .setViewTypeCount(2);
        for (Row row : data.rows) {
            builder.addItem(row.firstLine, rowView(context, data, row));
        }
        return builder.build();
    }

    private static final class LinesFactory implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private RenderData data = RenderData.empty();

        LinesFactory(Context context, Intent intent) {
            this.context = context;
            this.widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override public void onCreate() { }
        @Override public void onDataSetChanged() { data = load(context, widgetId); }
        @Override public void onDestroy() { data = RenderData.empty(); }
        @Override public int getCount() { return data.rows.size(); }

        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= data.rows.size()) return null;
            return rowView(context, data, data.rows.get(position));
        }

        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public long getItemId(int position) {
            if (position < 0 || position >= data.rows.size()) return position;
            return data.rows.get(position).firstLine;
        }
        @Override public boolean hasStableIds() { return true; }
    }

    private static RenderData load(Context context, int widgetId) {
        NoteStore store = new NoteStore(context);
        NoteStore.WidgetBinding appearance = store.getWidget(widgetId);
        String noteId = null;

        NoteStore.StackWidgetBinding stack = store.getStack(widgetId);
        if (stack != null && !stack.notes.isEmpty()) {
            int active = store.getStackActiveIndex(widgetId);
            if (active < 0 || active >= stack.notes.size()) active = 0;
            NoteStore.StackNote stackNote = stack.notes.get(active);
            noteId = stackNote.gistId;
            appearance = stack.appearanceFor(stackNote);
        } else if (appearance != null) {
            noteId = appearance.gistId;
        }

        int textColor = Color.WHITE;
        int mutedColor = 0x99FFFFFF;
        float textSize = 16f;
        if (appearance != null) {
            textColor = NoteWidgetProvider.textColor(appearance);
            mutedColor = NoteWidgetProvider.mutedColor(appearance);
            textSize = NoteWidgetProvider.textSizeSp(appearance.textSize);
        }

        List<Row> rows = new ArrayList<>();
        NoteStore.CachedNote note = noteId == null ? null : store.getNote(noteId);
        if (note != null && note.content != null) {
            StringBuilder prose = null;
            int proseStartLine = -1;
            for (MarkdownTasks.Line line : MarkdownTasks.parse(note.content)) {
                if (line.task) {
                    if (prose != null) {
                        rows.add(Row.prose(proseStartLine, prose.toString()));
                        prose = null;
                    }
                    rows.add(Row.task(line));
                    continue;
                }

                if (prose == null) {
                    prose = new StringBuilder();
                    proseStartLine = line.lineNumber;
                } else {
                    prose.append('\n');
                }
                String value = displayText(line.source);
                prose.append(value.isEmpty() ? "\u00A0" : value);
            }
            if (prose != null) rows.add(Row.prose(proseStartLine, prose.toString()));
        }
        return new RenderData(noteId, textColor, mutedColor, textSize, rows);
    }

    private static RemoteViews rowView(Context context, RenderData data, Row item) {
        if (item.task != null) {
            MarkdownTasks.Line line = item.task;
            RemoteViews row = new RemoteViews(context.getPackageName(),
                    R.layout.widget_note_task_row);
            row.setTextViewText(R.id.widget_line_check, line.checked ? "☑" : "☐");
            row.setTextViewText(R.id.widget_line_text, indent(line.indent) + line.text);
            row.setTextColor(R.id.widget_line_check, line.checked ? data.mutedColor : data.textColor);
            row.setTextColor(R.id.widget_line_text, line.checked ? data.mutedColor : data.textColor);
            row.setFloat(R.id.widget_line_text, "setTextSize", data.textSize);

            Intent fill = new Intent()
                    .putExtra(NoteWidgetProvider.EXTRA_GIST_ID, data.noteId)
                    .putExtra(NoteWidgetProvider.EXTRA_LINE_NUMBER, line.lineNumber);
            row.setOnClickFillInIntent(R.id.widget_line_root, fill);
            return row;
        }

        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_note_text_row);
        row.setTextViewText(R.id.widget_line_root, item.prose);
        row.setTextColor(R.id.widget_line_root, data.textColor);
        row.setFloat(R.id.widget_line_root, "setTextSize", data.textSize);
        return row;
    }

    private static String indent(int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append('\u00A0');
        return result.toString();
    }

    private static String displayText(String source) {
        if (source == null) return "";
        int prefix = 0;
        while (prefix < source.length() && source.charAt(prefix) == '#') prefix++;
        if (prefix > 0 && prefix < source.length() && source.charAt(prefix) == ' ') prefix++;
        return source.substring(prefix);
    }

    private static final class RenderData {
        final String noteId;
        final int textColor;
        final int mutedColor;
        final float textSize;
        final List<Row> rows;

        RenderData(String noteId, int textColor, int mutedColor, float textSize, List<Row> rows) {
            this.noteId = noteId;
            this.textColor = textColor;
            this.mutedColor = mutedColor;
            this.textSize = textSize;
            this.rows = rows;
        }

        static RenderData empty() {
            return new RenderData(null, Color.WHITE, 0x99FFFFFF, 16f, new ArrayList<>());
        }
    }

    private static final class Row {
        final int firstLine;
        final MarkdownTasks.Line task;
        final String prose;

        private Row(int firstLine, MarkdownTasks.Line task, String prose) {
            this.firstLine = firstLine;
            this.task = task;
            this.prose = prose;
        }

        static Row task(MarkdownTasks.Line line) {
            return new Row(line.lineNumber, line, null);
        }

        static Row prose(int firstLine, String prose) {
            return new Row(firstLine, null, prose);
        }
    }
}
