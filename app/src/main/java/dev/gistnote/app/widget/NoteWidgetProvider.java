package dev.gistnote.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.Toast;
import android.view.Gravity;

import java.util.List;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.MarkdownTasks;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.data.SyncEngine;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.ui.NoteActivity;

public final class NoteWidgetProvider extends AppWidgetProvider {
    public static final int FROST_COLOR = 0xA88E8D9F;
    public static final String ACTION_REFRESH_WIDGET = "dev.gistnote.app.REFRESH_WIDGET";
    public static final String ACTION_TOGGLE_EXACT_TASK = "dev.gistnote.app.TOGGLE_EXACT_TASK";
    public static final String ACTION_DOUBLE_TAP_EDIT = "dev.gistnote.app.DOUBLE_TAP_EDIT";
    public static final String EXTRA_LINE_NUMBER = "dev.gistnote.app.LINE_NUMBER";
    public static final String EXTRA_GIST_ID = "dev.gistnote.app.GIST_ID";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DOUBLE_TAP_EDIT.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            android.content.SharedPreferences prefs = context.getSharedPreferences("widget_taps", Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            long lastTap = prefs.getLong("tap_" + widgetId, 0L);
            if (now - lastTap < 500 && now >= lastTap) {
                prefs.edit().putLong("tap_" + widgetId, 0L).apply();
                Intent open = new Intent(context, NoteActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        .putExtra(NoteActivity.EXTRA_WIDGET_ID, widgetId)
                        .putExtra(NoteActivity.EXTRA_GIST_ID,
                                intent.getStringExtra(NoteActivity.EXTRA_GIST_ID))
                        .putExtra(NoteActivity.EXTRA_FILENAME,
                                intent.getStringExtra(NoteActivity.EXTRA_FILENAME))
                        .putExtra(NoteActivity.EXTRA_TITLE,
                                intent.getStringExtra(NoteActivity.EXTRA_TITLE));
                context.startActivity(open);
            } else {
                prefs.edit().putLong("tap_" + widgetId, now).apply();
            }
            return;
        }
        if (ACTION_REFRESH_WIDGET.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;
            refreshOne(context, widgetId, goAsync());
            return;
        }
        if (ACTION_TOGGLE_EXACT_TASK.equals(action)) {
            toggleExactTask(context,
                    intent.getStringExtra(EXTRA_GIST_ID),
                    intent.getIntExtra(EXTRA_LINE_NUMBER, -1));
            return;
        }
        super.onReceive(context, intent);
    }

    private static void toggleExactTask(Context context, String gistId, int lineNumber) {
        if (gistId == null || lineNumber < 0) return;
        Context appContext = context.getApplicationContext();
        NoteStore store = new NoteStore(appContext);
        NoteStore.CachedNote note = store.getNote(gistId);
        if (note != null && note.content != null) {
            List<MarkdownTasks.Line> lines = MarkdownTasks.parse(note.content);
            for (MarkdownTasks.Line line : lines) {
                if (line.lineNumber == lineNumber && line.task) {
                    store.putDraft(note, MarkdownTasks.toggle(
                            note.content, line.lineNumber, !line.checked));
                    updateWidgetsForGist(appContext, gistId);
                    return;
                }
            }
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        NoteStore store = new NoteStore(context);
        for (int id : ids) store.removeWidget(id);
    }

    public static void update(Context context, AppWidgetManager manager, int widgetId) {
        NoteStore store = new NoteStore(context);
        NoteStore.WidgetBinding binding = store.getWidget(widgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);
        if (binding == null) {
            views.setImageViewBitmap(R.id.widget_background,
                    roundedBackground(context, FROST_COLOR));
            views.setTextViewText(R.id.widget_title, "GistNote");
            views.setTextViewText(R.id.widget_content, "Choose a gist in widget settings");
            views.setTextViewText(R.id.widget_status, "Not configured");
            views.setViewVisibility(R.id.widget_page_indicator, android.view.View.GONE);
            views.setViewVisibility(R.id.widget_nav_container, android.view.View.GONE);
            manager.updateAppWidget(widgetId, views);
            return;
        }

        NoteStore.CachedNote note = store.getNote(binding.gistId);
        views.setImageViewBitmap(R.id.widget_background,
                roundedBackground(context, backgroundColorValue(binding.color)));
        int textColor = textColor(binding);
        int mutedColor = mutedColor(binding);

        // Compact Title Bar
        String title = (note != null && note.title != null && !note.title.isEmpty()) ? note.title : binding.filename;
        views.setTextViewText(R.id.widget_title, title);
        views.setTextColor(R.id.widget_title, textColor);
        views.setViewVisibility(R.id.widget_page_indicator, android.view.View.GONE);

        configureLineCollection(context, views, widgetId);

        // Bottom Bar: Left Nav Container (hidden for single note)
        views.setViewVisibility(R.id.widget_nav_container, android.view.View.GONE);

        // Bottom-right controls: sync state/action, then preview.
        applySyncState(views, store, note, binding.gistId, mutedColor);
        applyDimmedIconColor(views, R.id.widget_preview_icon, mutedColor);

        // Settings Button (at top right) - text color with 10% lesser opacity
        int buttonColor = textColor;
        int buttonAlpha = Math.round(Color.alpha(textColor) * 0.9f);
        views.setInt(R.id.widget_settings_button, "setColorFilter", buttonColor);
        views.setInt(R.id.widget_settings_button, "setImageAlpha", buttonAlpha);
        Intent settings = new Intent(context, WidgetAppearanceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("gistnote://appearance/" + widgetId));
        views.setOnClickPendingIntent(R.id.widget_settings_button,
                PendingIntent.getActivity(context, widgetId + 100000, settings,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Explicit preview button; it never overlaps the checklist.
        Intent centerIntent = new Intent(context, NoteActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(NoteActivity.EXTRA_WIDGET_ID, widgetId)
                .putExtra(NoteActivity.EXTRA_GIST_ID, binding.gistId)
                .putExtra(NoteActivity.EXTRA_FILENAME, binding.filename)
                .putExtra(NoteActivity.EXTRA_TITLE, note == null ? binding.filename : note.title)
                .setData(Uri.parse("gistnote://preview/" + widgetId + "/" + binding.gistId));
        PendingIntent centerPending = PendingIntent.getActivity(context, widgetId + 600000, centerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_preview_icon, centerPending);

        Intent syncIntent = new Intent(context, NoteWidgetProvider.class)
                .setAction(ACTION_REFRESH_WIDGET)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("gistnote://sync/" + widgetId));
        PendingIntent syncPending = PendingIntent.getBroadcast(context, widgetId + 700000, syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_tick_icon, syncPending);

        manager.updateAppWidget(widgetId, views);
        if (!usesInlineCollections()) {
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_content_list);
        }
    }

    static void configureLineCollection(Context context, RemoteViews views, int widgetId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setRemoteAdapter(R.id.widget_content_list,
                    WidgetNoteLinesService.inlineItems(context, widgetId));
        } else {
            Intent service = new Intent(context, WidgetNoteLinesService.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData(Uri.parse("gistnote://lines/" + widgetId));
            views.setRemoteAdapter(R.id.widget_content_list, service);
        }

        Intent rowClick = new Intent(context, NoteWidgetProvider.class)
                .setAction(ACTION_TOGGLE_EXACT_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("gistnote://toggle-line/" + widgetId));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent template = PendingIntent.getBroadcast(
                context, widgetId + 900000, rowClick, flags);
        views.setPendingIntentTemplate(R.id.widget_content_list, template);
    }

    static boolean usesInlineCollections() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    private static void refreshOne(Context context, int widgetId,
                                   BroadcastReceiver.PendingResult pendingResult) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        NoteStore initialStore = new NoteStore(appContext);
        NoteStore.WidgetBinding initialBinding = initialStore.getWidget(widgetId);
        if (initialBinding != null && NoteStore.isLocalId(initialBinding.gistId)) {
            Toast.makeText(appContext, "Local note is saved on this device",
                    Toast.LENGTH_SHORT).show();
            updateWidgetsForGist(appContext, initialBinding.gistId);
            pendingResult.finish();
            return;
        }
        Toast.makeText(appContext, "Syncing note…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String gistId = null;
            try {
                NoteStore store = new NoteStore(appContext);
                NoteStore.WidgetBinding binding = store.getWidget(widgetId);
                if (binding == null) return;
                gistId = binding.gistId;
                String token = new TokenStore(appContext).getToken();
                if (token.isEmpty()) throw new IllegalStateException("Add a GitHub token in the app");
                SyncEngine syncEngine = new SyncEngine(new GitHubApi(token), store);
                NoteStore.CachedNote note = store.getNote(binding.gistId);
                if (note != null && note.dirty) {
                    syncEngine.save(note, note.content);
                } else {
                    syncEngine.refresh(binding.gistId, binding.filename);
                }
                store.setConflict(binding.gistId, false);
                updateWidgetsForGist(appContext, binding.gistId);
            } catch (SyncEngine.ConflictException conflict) {
                if (gistId != null) new NoteStore(appContext).setConflict(gistId, true);
                if (gistId != null) updateWidgetsForGist(appContext, gistId);
                new android.os.Handler(appContext.getMainLooper()).post(() ->
                        Toast.makeText(appContext, "Sync conflict · open preview to resolve",
                                Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                new android.os.Handler(appContext.getMainLooper()).post(() ->
                        Toast.makeText(appContext, "Could not sync this note",
                                Toast.LENGTH_SHORT).show());
            } finally {
                pendingResult.finish();
            }
        }, "gistnote-widget-sync").start();
    }

    public static void updateWidgetsForGist(Context context, String gistId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.content.ComponentName provider = new android.content.ComponentName(
                context, NoteWidgetProvider.class);
        NoteStore store = new NoteStore(context);
        for (int id : manager.getAppWidgetIds(provider)) {
            NoteStore.WidgetBinding binding = store.getWidget(id);
            if (binding != null && binding.gistId.equals(gistId)) {
                NoteStore.CachedNote note = store.getNote(gistId);
                RemoteViews changes = new RemoteViews(context.getPackageName(), R.layout.widget_note);
                applySyncState(changes, store, note, gistId, mutedColor(binding));
                if (usesInlineCollections()) configureLineCollection(context, changes, id);
                manager.partiallyUpdateAppWidget(id, changes);
                if (!usesInlineCollections()) {
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widget_content_list);
                }
            }
        }
        StackNoteWidgetProvider.notifyForGist(context, gistId);
    }

    static void applySyncState(RemoteViews views, NoteStore store,
                               NoteStore.CachedNote note, String gistId, int tint) {
        if (NoteStore.isLocalId(gistId)) {
            views.setImageViewResource(R.id.widget_tick_icon, R.drawable.ic_tick_single);
            applyDimmedIconColor(views, R.id.widget_tick_icon, tint);
            views.setCharSequence(R.id.widget_tick_icon, "setContentDescription",
                    "Local note saved on device");
            return;
        }
        boolean dirty = note != null && note.dirty;
        boolean conflict = store.hasConflict(gistId);
        views.setImageViewResource(R.id.widget_tick_icon, conflict
                ? R.drawable.ic_sync_conflict
                : (dirty ? R.drawable.ic_tick_single : R.drawable.ic_tick_double));
        applyDimmedIconColor(views, R.id.widget_tick_icon, tint);
        views.setCharSequence(R.id.widget_tick_icon, "setContentDescription", conflict
                ? "Sync conflict" : (dirty ? "Saved on device; sync now" : "Synced; check now"));
    }

    static void applyDimmedIconColor(RemoteViews views, int viewId, int color) {
        int opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        views.setInt(viewId, "setColorFilter", opaque);
        views.setInt(viewId, "setImageAlpha", Color.alpha(color));
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.content.ComponentName provider = new android.content.ComponentName(
                context, NoteWidgetProvider.class);
        for (int id : manager.getAppWidgetIds(provider)) update(context, manager, id);
        StackNoteWidgetProvider.notifyAllChanged(context);
    }

    public static String widgetText(String markdown) {
        StringBuilder result = new StringBuilder();
        List<MarkdownTasks.Line> lines = MarkdownTasks.parse(markdown);
        for (MarkdownTasks.Line line : lines) {
            if (result.length() > 0) result.append('\n');
            if (line.task) {
                for (int i = 0; i < line.indent; i++) result.append(' ');
                result.append(line.checked ? "☑ " : "☐ ").append(line.text);
            } else {
                String text = line.text;
                while (text.startsWith("#")) text = text.substring(1);
                result.append(text.startsWith(" ") ? text.substring(1) : text);
            }
        }
        return result.toString();
    }

    public static int backgroundResource(String color) {
        if ("Transparent".equals(color)) return R.drawable.note_transparent;
        if ("Black".equals(color)) return R.drawable.note_black;
        if ("White".equals(color)) return R.drawable.note_white;
        if ("Amber".equals(color)) return R.drawable.note_amber;
        if ("Coral".equals(color)) return R.drawable.note_coral;
        if ("Purple".equals(color)) return R.drawable.note_purple;
        if ("Green".equals(color)) return R.drawable.note_green;
        if ("Peach".equals(color)) return R.drawable.note_peach;
        if ("Mint".equals(color)) return R.drawable.note_mint;
        if ("Blue".equals(color)) return R.drawable.note_blue;
        if ("Charcoal".equals(color)) return R.drawable.note_charcoal;
        return R.drawable.note_yellow;
    }

    public static float textSizeSp(String size) {
        try {
            float numeric = Float.parseFloat(size);
            return Math.max(12f, Math.min(28f, numeric));
        } catch (NumberFormatException ignored) { }
        if ("Small".equals(size)) return 13f;
        if ("Large".equals(size)) return 19f;
        return 16f;
    }

    public static int gravity(String alignment) {
        if ("center".equals(alignment)) return Gravity.CENTER;
        if ("end".equals(alignment)) return Gravity.END;
        return Gravity.START;
    }

    public static int textColor(NoteStore.WidgetBinding binding) {
        if (binding.textColor != null && binding.textColor.startsWith("#")) {
            try { return Color.parseColor(binding.textColor); } catch (IllegalArgumentException ignored) { }
        }
        if ("Black".equals(binding.textColor)) return Color.BLACK;
        if ("White".equals(binding.textColor)) return Color.WHITE;
        if ("Brown".equals(binding.textColor)) return Color.rgb(105, 56, 18);
        if ("Amber".equals(binding.textColor)) return Color.rgb(255, 173, 41);
        if ("Coral".equals(binding.textColor)) return Color.rgb(242, 95, 69);
        if ("Purple".equals(binding.textColor)) return Color.rgb(185, 128, 208);
        if ("Blue".equals(binding.textColor)) return Color.rgb(21, 95, 155);
        if ("Green".equals(binding.textColor)) return Color.rgb(46, 107, 58);
        return "Charcoal".equals(binding.color) ? Color.WHITE : Color.rgb(36, 29, 24);
    }

    public static int mutedColor(NoteStore.WidgetBinding binding) {
        int text = textColor(binding);
        // Base text color with increased transparency (10% more transparent) for a refined muted look
        int alpha = Math.round(Color.alpha(text) * 0.38f);
        return Color.argb(alpha, Color.red(text), Color.green(text), Color.blue(text));
    }

    public static int backgroundColorValue(String name) {
        if (name != null && name.startsWith("#")) {
            try { return Color.parseColor(name); } catch (IllegalArgumentException ignored) { }
        }
        if ("Transparent".equals(name)) return Color.TRANSPARENT;
        if ("Frost".equals(name)) return FROST_COLOR;
        if ("Black".equals(name)) return Color.BLACK;
        if ("White".equals(name)) return Color.WHITE;
        if ("Amber".equals(name)) return 0xFFFFAD29;
        if ("Coral".equals(name)) return 0xFFF25F45;
        if ("Purple".equals(name)) return 0xFFB980D0;
        if ("Blue".equals(name)) return 0xFF64AFE2;
        if ("Green".equals(name)) return 0xFF88C28E;
        if ("Peach".equals(name)) return 0xFFFFDDC8;
        if ("Mint".equals(name)) return 0xFFD7F0DB;
        if ("Charcoal".equals(name)) return 0xFF343238;
        return FROST_COLOR;
    }

    public static Bitmap roundedBackground(Context context, int color) {
        int width = 540;
        int height = 360;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        // Border radius reduced to 60% of previous 38f (38f * 0.60 = 22.8f)
        float radius = 22.8f;
        RectF bounds = new RectF(0, 0, width, height);
        boolean frost = (color & 0xFFFFFF) == (FROST_COLOR & 0xFFFFFF);
        int alpha = Color.alpha(color);
        int blur = 36;
        try {
            blur = context.getSharedPreferences("widget_style", Context.MODE_PRIVATE).getInt("panel_blur", 36);
        } catch (Exception ignored) { }
        if (frost && alpha > 0) {
            // Diffuse gradient simulation of frosted glass merging into wallpaper with blur factor
            float blurFactor = Math.max(0f, Math.min(100f, blur)) / 100f;
            int topAlpha = Math.round(alpha * (0.75f + 0.25f * blurFactor));
            int botAlpha = Math.round(alpha * (0.85f + 0.15f * blurFactor));
            paint.setShader(new android.graphics.LinearGradient(0, 0, width, height,
                    Color.argb(topAlpha, 158, 156, 172),
                    Color.argb(botAlpha, 118, 120, 138),
                    android.graphics.Shader.TileMode.CLAMP));
        }
        canvas.drawRoundRect(bounds, radius, radius, paint);
        return bitmap;
    }
}
