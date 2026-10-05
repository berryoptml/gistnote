package dev.gistnote.app.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.gistnote.app.R;
import dev.gistnote.app.data.GitHubApi;
import dev.gistnote.app.data.NoteStore;
import dev.gistnote.app.data.SyncEngine;
import dev.gistnote.app.security.TokenStore;
import dev.gistnote.app.ui.NoteActivity;

public final class StackNoteWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_OPEN_NOTE = "dev.gistnote.app.STACK_OPEN_NOTE";
    public static final String ACTION_SYNC_NOTE = "dev.gistnote.app.STACK_SYNC_NOTE";
    public static final String ACTION_NEXT_NOTE = "dev.gistnote.app.STACK_NEXT_NOTE";
    public static final String ACTION_PREV_NOTE = "dev.gistnote.app.STACK_PREV_NOTE";
    public static final String ACTION_SCROLL_UP = "dev.gistnote.app.STACK_SCROLL_UP";
    public static final String ACTION_SCROLL_DOWN = "dev.gistnote.app.STACK_SCROLL_DOWN";
    public static final String ACTION_FLIP_PAGE = "dev.gistnote.app.STACK_FLIP_PAGE";
    public static final String ACTION_APPEARANCE = "dev.gistnote.app.STACK_APPEARANCE";
    public static final String ACTION_DOUBLE_TAP_EDIT = "dev.gistnote.app.STACK_DOUBLE_TAP_EDIT";
    public static final String ACTION_CLICK_LEFT = "dev.gistnote.app.STACK_CLICK_LEFT";
    public static final String ACTION_CLICK_RIGHT = "dev.gistnote.app.STACK_CLICK_RIGHT";

    private static final android.os.Handler sTapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable sPendingClickRunnable = null;
    private static BroadcastReceiver.PendingResult sPendingAsyncResult = null;
    private static long sLastTapTime = 0L;
    private static int sLastTapWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_CLICK_LEFT.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                toggleTaskInActiveNote(context, widgetId, goAsync());
            }
            return;
        }
        if (ACTION_NEXT_NOTE.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                animateAndCycle(context, widgetId, 1, goAsync());
            }
            return;
        }
        if (ACTION_PREV_NOTE.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                animateAndCycle(context, widgetId, -1, goAsync());
            }
            return;
        }
        if (ACTION_FLIP_PAGE.equals(action)) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                scrollDown(context, widgetId, goAsync());
            }
            return;
        }
        if (ACTION_OPEN_NOTE.equals(action)) {
            openPreview(context, intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID), intent);
            return;
        }
        if (ACTION_SYNC_NOTE.equals(action)) {
            syncOne(context, intent, goAsync());
            return;
        }
        if (ACTION_APPEARANCE.equals(action)) {
            Intent appearance = new Intent(context, WidgetAppearanceActivity.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, intent.getIntExtra(
                            AppWidgetManager.EXTRA_APPWIDGET_ID,
                            AppWidgetManager.INVALID_APPWIDGET_ID))
                    .putExtra(NoteActivity.EXTRA_GIST_ID,
                            intent.getStringExtra(NoteActivity.EXTRA_GIST_ID));
            appearance.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            context.startActivity(appearance);
            return;
        }
        super.onReceive(context, intent);
    }

    private static void openPreview(Context context, int widgetId, Intent intent) {
        NoteStore store = new NoteStore(context);
        NoteStore.StackWidgetBinding binding = store.getStack(widgetId);
        String gistId = intent != null ? intent.getStringExtra(NoteActivity.EXTRA_GIST_ID) : null;
        String filename = intent != null ? intent.getStringExtra(NoteActivity.EXTRA_FILENAME) : null;
        String title = intent != null ? intent.getStringExtra(NoteActivity.EXTRA_TITLE) : null;
        if (binding != null && !binding.notes.isEmpty()) {
            int activeIndex = store.getStackActiveIndex(widgetId);
            if (activeIndex < 0 || activeIndex >= binding.notes.size()) activeIndex = 0;
            NoteStore.StackNote note = binding.notes.get(activeIndex);
            gistId = note.gistId;
            filename = note.filename;
            NoteStore.CachedNote cached = store.getNote(note.gistId);
            title = cached != null && cached.title != null && !cached.title.isEmpty()
                    ? cached.title : note.filename;
        }
        Intent open = new Intent(context, NoteActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(NoteActivity.EXTRA_WIDGET_ID, widgetId)
                .putExtra(NoteActivity.EXTRA_GIST_ID, gistId)
                .putExtra(NoteActivity.EXTRA_FILENAME, filename)
                .putExtra(NoteActivity.EXTRA_TITLE, title);
        context.startActivity(open);
    }

    private static void scrollUp(Context context, int widgetId,
                                BroadcastReceiver.PendingResult result) {
        try {
            Context appContext = context.getApplicationContext();
            NoteStore store = new NoteStore(appContext);
            store.scrollUpStack(widgetId);
            update(appContext, AppWidgetManager.getInstance(appContext), widgetId);
        } catch (Exception ignored) {
        } finally {
            if (result != null) {
                try { result.finish(); } catch (Exception ignored) {}
            }
        }
    }

    private static void scrollDown(Context context, int widgetId,
                                  BroadcastReceiver.PendingResult result) {
        try {
            Context appContext = context.getApplicationContext();
            NoteStore store = new NoteStore(appContext);
            NoteStore.StackWidgetBinding binding = store.getStack(widgetId);
            if (binding != null && !binding.notes.isEmpty()) {
                int activeIndex = store.getStackActiveIndex(widgetId);
                if (activeIndex < 0 || activeIndex >= binding.notes.size()) activeIndex = 0;
                NoteStore.StackNote activeNote = binding.notes.get(activeIndex);
                NoteStore.CachedNote note = store.getNote(activeNote.gistId);
                String raw = note != null && note.content != null ? note.content : "";
                String formatted = NoteWidgetProvider.widgetText(raw);
                int totalLines = formatted.split("\n", -1).length;
                store.scrollDownStack(widgetId, totalLines);
                update(appContext, AppWidgetManager.getInstance(appContext), widgetId);
            }
        } catch (Exception ignored) {
        } finally {
            if (result != null) {
                try { result.finish(); } catch (Exception ignored) {}
            }
        }
    }

    private static void toggleTaskInActiveNote(Context context, int widgetId,
                                              BroadcastReceiver.PendingResult result) {
        try {
            Context appContext = context.getApplicationContext();
            NoteStore store = new NoteStore(appContext);
            NoteStore.StackWidgetBinding binding = store.getStack(widgetId);
            if (binding == null) {
                NoteStore.WidgetBinding single = store.getWidget(widgetId);
                if (single != null) {
                    toggleTaskForGist(appContext, store, single.gistId);
                }
            } else if (!binding.notes.isEmpty()) {
                int activeIndex = store.getStackActiveIndex(widgetId);
                if (activeIndex < 0 || activeIndex >= binding.notes.size()) activeIndex = 0;
                NoteStore.StackNote activeNote = binding.notes.get(activeIndex);
                toggleTaskForGist(appContext, store, activeNote.gistId);
            }
        } catch (Exception ignored) {
        } finally {
            if (result != null) {
                try { result.finish(); } catch (Exception ignored) {}
            }
        }
    }

    private static void toggleTaskForGist(Context appContext, NoteStore store, String gistId) {
        if (gistId == null) return;
        NoteStore.CachedNote note = store.getNote(gistId);
        if (note != null && note.content != null) {
            List<dev.gistnote.app.data.MarkdownTasks.Line> lines = dev.gistnote.app.data.MarkdownTasks.parse(note.content);
            int firstUncheckedLine = -1;
            boolean hasTasks = false;
            for (dev.gistnote.app.data.MarkdownTasks.Line line : lines) {
                if (line.task) {
                    hasTasks = true;
                    if (!line.checked && firstUncheckedLine == -1) {
                        firstUncheckedLine = line.lineNumber;
                    }
                }
            }
            if (!hasTasks) return;

            if (firstUncheckedLine >= 0) {
                String updated = dev.gistnote.app.data.MarkdownTasks.toggle(note.content, firstUncheckedLine, true);
                store.putDraft(note, updated);
                NoteWidgetProvider.updateWidgetsForGist(appContext, gistId);
            } else {
                String updated = note.content;
                for (dev.gistnote.app.data.MarkdownTasks.Line line : lines) {
                    if (line.task && line.checked) {
                        updated = dev.gistnote.app.data.MarkdownTasks.toggle(updated, line.lineNumber, false);
                    }
                }
                store.putDraft(note, updated);
                NoteWidgetProvider.updateWidgetsForGist(appContext, gistId);
            }
        }
    }

    private static void animateAndCycle(Context context, int widgetId, int delta,
                                        BroadcastReceiver.PendingResult result) {
        new Thread(() -> {
            try {
                Context appContext = context.getApplicationContext();
                AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
                NoteStore store = new NoteStore(appContext);
                NoteStore.StackWidgetBinding binding = store.getStack(widgetId);
                if (binding == null || binding.notes.size() <= 1) {
                    if (result != null) result.finish();
                    return;
                }

                int total = binding.notes.size();
                int current = store.getStackActiveIndex(widgetId);
                int target = (current + delta) % total;
                if (target < 0) target += total;

                NoteStore.StackNote activeNote = binding.notes.get(current);
                NoteStore.WidgetBinding appearance = binding.appearanceFor(activeNote);
                int textColor = NoteWidgetProvider.textColor(appearance);
                int activeColor = NoteWidgetProvider.mutedColor(appearance);
                int mutedAlpha = Color.alpha(activeColor);
                int inactiveDotColor = Color.argb(Math.round(mutedAlpha * 0.45f), Color.red(textColor), Color.green(textColor), Color.blue(textColor));

                // Frame 1: Worm stretches to merge two dots (35% merge)
                Bitmap f1 = drawWormIndicatorsBitmap(appContext, total, current, 0.35f, current, target, inactiveDotColor, activeColor);
                if (f1 != null) {
                    RemoteViews p1 = new RemoteViews(appContext.getPackageName(), R.layout.widget_note);
                    p1.setImageViewBitmap(R.id.widget_indicators_image, f1);
                    manager.partiallyUpdateAppWidget(widgetId, p1);
                }
                Thread.sleep(35);

                // Frame 2: Worm contracts tail into destination (70% merge)
                Bitmap f2 = drawWormIndicatorsBitmap(appContext, total, current, 0.70f, current, target, inactiveDotColor, activeColor);
                if (f2 != null) {
                    RemoteViews p2 = new RemoteViews(appContext.getPackageName(), R.layout.widget_note);
                    p2.setImageViewBitmap(R.id.widget_indicators_image, f2);
                    manager.partiallyUpdateAppWidget(widgetId, p2);
                }
                Thread.sleep(35);

                // Frame 3: Settled at new note
                store.cycleStackNote(widgetId, delta);
                update(appContext, manager, widgetId, true);
            } catch (Exception ignored) {
            } finally {
                if (result != null) result.finish();
            }
        }, "gistnote-worm-anim").start();
    }

    public static void update(Context context, AppWidgetManager manager, int widgetId) {
        update(context, manager, widgetId, false);
    }

    private static void update(Context context, AppWidgetManager manager, int widgetId,
                               boolean preserveCollectionPosition) {
        NoteStore store = new NoteStore(context);
        NoteStore.StackWidgetBinding binding = store.getStack(widgetId);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_note);

        if (binding == null || binding.notes.isEmpty()) {
            views.setImageViewBitmap(R.id.widget_background,
                    NoteWidgetProvider.roundedBackground(context, NoteWidgetProvider.FROST_COLOR));
            views.setTextViewText(R.id.widget_title, "GistNote");
            views.setTextViewText(R.id.widget_content, "Choose notes in widget settings");
            views.setTextViewText(R.id.widget_status, "Not configured");
            views.setViewVisibility(R.id.widget_page_indicator, View.GONE);
            views.setViewVisibility(R.id.widget_nav_container, View.GONE);
            manager.updateAppWidget(widgetId, views);
            return;
        }

        int totalNotes = binding.notes.size();
        int activeIndex = store.getStackActiveIndex(widgetId);
        if (activeIndex < 0 || activeIndex >= totalNotes) {
            activeIndex = 0;
            store.setStackActiveIndex(widgetId, 0);
        }
        NoteStore.StackNote activeNote = binding.notes.get(activeIndex);
        NoteStore.WidgetBinding appearance = binding.appearanceFor(activeNote);
        NoteStore.CachedNote note = store.getNote(activeNote.gistId);

        int textColor = NoteWidgetProvider.textColor(appearance);
        int mutedColor = NoteWidgetProvider.mutedColor(appearance);
        int mutedAlpha = Color.alpha(mutedColor);

        int cardColor = NoteWidgetProvider.backgroundColorValue(appearance.color);
        views.setImageViewBitmap(R.id.widget_background,
                NoteWidgetProvider.roundedBackground(context, cardColor));

        // Title Bar
        String title = (note != null && note.title != null && !note.title.isEmpty())
                ? note.title : activeNote.filename;
        views.setTextViewText(R.id.widget_title, title);
        views.setTextColor(R.id.widget_title, textColor);

        // The body is a real scrollable collection; each task row handles its own click.
        if (NoteWidgetProvider.usesInlineCollections() || !preserveCollectionPosition) {
            NoteWidgetProvider.configureLineCollection(context, views, widgetId);
        }
        views.setViewVisibility(R.id.widget_page_indicator, View.GONE);

        // Left Bottom Bar: Dash-Dot Note Position Indicator with identical thickness
        if (totalNotes > 1) {
            views.setViewVisibility(R.id.widget_nav_container, View.VISIBLE);
            int dotColor = Color.argb(Math.round(mutedAlpha * 0.45f),
                    Color.red(textColor), Color.green(textColor), Color.blue(textColor));
            int activeColor = mutedColor;
            Bitmap indicatorBmp = drawWormIndicatorsBitmap(context, totalNotes, activeIndex, 1.0f, activeIndex, activeIndex, dotColor, activeColor);
            if (indicatorBmp != null) {
                views.setImageViewBitmap(R.id.widget_indicators_image, indicatorBmp);
            }
        } else {
            views.setViewVisibility(R.id.widget_nav_container, View.GONE);
        }

        // Right bottom: persistent sync state/action, then preview.
        NoteWidgetProvider.applySyncState(
                views, store, note, activeNote.gistId, mutedColor);
        NoteWidgetProvider.applyDimmedIconColor(
                views, R.id.widget_preview_icon, mutedColor);

        // Settings Button (at top right) - text color with 10% lesser opacity
        int buttonColor = textColor;
        int buttonAlpha = Math.round(Color.alpha(textColor) * 0.9f);
        views.setInt(R.id.widget_settings_button, "setColorFilter", buttonColor);
        views.setInt(R.id.widget_settings_button, "setImageAlpha", buttonAlpha);
        Intent settingsIntent = new Intent(context, WidgetAppearanceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(NoteActivity.EXTRA_GIST_ID, activeNote.gistId)
                .setData(Uri.parse("gistnote://appearance/" + widgetId + "/" + activeNote.gistId));
        PendingIntent settingsPending = PendingIntent.getActivity(context, widgetId + 500000, settingsIntent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_settings_button, settingsPending);

        // The left/right halves of the dots-and-dash indicator are the only note nav targets.
        Intent prevIntent = new Intent(context, StackNoteWidgetProvider.class)
                .setAction(ACTION_PREV_NOTE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("gistnote://prev/" + widgetId));
        PendingIntent prevPending = PendingIntent.getBroadcast(context, widgetId + 710000, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_indicator_prev, prevPending);

        Intent nextIntent = new Intent(context, StackNoteWidgetProvider.class)
                .setAction(ACTION_NEXT_NOTE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .setData(Uri.parse("gistnote://next/" + widgetId));
        PendingIntent nextPending = PendingIntent.getBroadcast(context, widgetId + 720000, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_indicator_next, nextPending);

        // Preview has one explicit button beside sync.
        Intent centerIntent = new Intent(context, NoteActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(NoteActivity.EXTRA_WIDGET_ID, widgetId)
                .putExtra(NoteActivity.EXTRA_GIST_ID, activeNote.gistId)
                .putExtra(NoteActivity.EXTRA_FILENAME, activeNote.filename)
                .putExtra(NoteActivity.EXTRA_TITLE, note == null ? activeNote.filename : note.title)
                .setData(Uri.parse("gistnote://preview/" + widgetId + "/" + activeNote.gistId));
        PendingIntent centerPending = PendingIntent.getActivity(context, widgetId + 600000, centerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_preview_icon, centerPending);

        Intent syncIntent = new Intent(context, StackNoteWidgetProvider.class)
                .setAction(ACTION_SYNC_NOTE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(NoteActivity.EXTRA_GIST_ID, activeNote.gistId)
                .putExtra(NoteActivity.EXTRA_FILENAME, activeNote.filename)
                .setData(Uri.parse("gistnote://sync/" + widgetId + "/" + activeNote.gistId));
        PendingIntent syncPending = PendingIntent.getBroadcast(context, widgetId + 810000, syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_tick_icon, syncPending);

        if (preserveCollectionPosition) manager.partiallyUpdateAppWidget(widgetId, views);
        else manager.updateAppWidget(widgetId, views);
        if (!NoteWidgetProvider.usesInlineCollections()) {
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_content_list);
        }
    }

    public static Bitmap drawWormIndicatorsBitmap(Context context, int total, int activeIndex,
                                                 float fraction, int fromIndex, int toIndex,
                                                 int dotColor, int activeColor) {
        if (total <= 1) return null;
        float density = context.getResources().getDisplayMetrics().density;
        float dotDiameter = 8.25f * density; // 1.5x thicker in height
        float radius = dotDiameter / 2f;
        float dashWidth = dotDiameter * 2f; // The dash is two dots wide
        float spacing = 6f * density;

        int height = Math.max(1, Math.round(dotDiameter + 2f * density));
        float totalWidth = (total - 1) * dotDiameter + dashWidth + (total - 1) * spacing;
        int width = Math.max(1, Math.round(totalWidth));

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(dotColor);
        inactivePaint.setStyle(Paint.Style.FILL);

        Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(activeColor);
        activePaint.setStyle(Paint.Style.FILL);

        float cy = height / 2f;
        float top = cy - radius;
        float bottom = cy + radius;

        float[] leftsFrom = new float[total];
        float cur = 0f;
        for (int k = 0; k < total; k++) {
            leftsFrom[k] = cur;
            cur += (k == fromIndex ? dashWidth : dotDiameter) + spacing;
        }

        float[] leftsTo = new float[total];
        cur = 0f;
        for (int k = 0; k < total; k++) {
            leftsTo[k] = cur;
            cur += (k == toIndex ? dashWidth : dotDiameter) + spacing;
        }

        if (fraction >= 1f || fromIndex == toIndex) {
            for (int k = 0; k < total; k++) {
                float left = leftsFrom[k];
                if (k == activeIndex) {
                    RectF pill = new RectF(left, top, left + dashWidth, bottom);
                    canvas.drawRoundRect(pill, radius, radius, activePaint);
                } else {
                    canvas.drawCircle(left + radius, cy, radius, inactivePaint);
                }
            }
            return bitmap;
        }

        for (int k = 0; k < total; k++) {
            if (k != fromIndex && k != toIndex) {
                float left = leftsFrom[k] * (1f - fraction) + leftsTo[k] * fraction;
                canvas.drawCircle(left + radius, cy, radius, inactivePaint);
            }
        }

        float L0 = leftsFrom[fromIndex];
        float R0 = L0 + dashWidth;
        float L1 = leftsTo[toIndex];
        float R1 = L1 + dashWidth;

        float currLeft, currRight;
        if (fromIndex < toIndex) {
            if (fraction <= 0.5f) {
                float f = fraction * 2f;
                currLeft = L0;
                currRight = R0 + (R1 - R0) * f;
            } else {
                float f = (fraction - 0.5f) * 2f;
                currLeft = L0 + (L1 - L0) * f;
                currRight = R1;
            }
        } else {
            if (fraction <= 0.5f) {
                float f = fraction * 2f;
                currLeft = L0 - (L0 - L1) * f;
                currRight = R0;
            } else {
                float f = (fraction - 0.5f) * 2f;
                currLeft = L1;
                currRight = R0 - (R0 - R1) * f;
            }
        }

        RectF worm = new RectF(currLeft, top, currRight, bottom);
        canvas.drawRoundRect(worm, radius, radius, activePaint);

        return bitmap;
    }

    public static void notifyForGist(Context context, String gistId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StackNoteWidgetProvider.class));
        NoteStore store = new NoteStore(context);
        for (int id : ids) {
            NoteStore.StackWidgetBinding binding = store.getStack(id);
            if (binding != null && binding.contains(gistId)) {
                int activeIndex = store.getStackActiveIndex(id);
                if (activeIndex < 0 || activeIndex >= binding.notes.size()) activeIndex = 0;
                NoteStore.StackNote active = binding.notes.get(activeIndex);
                if (!active.gistId.equals(gistId)) continue;
                NoteStore.WidgetBinding appearance = binding.appearanceFor(active);
                RemoteViews changes = new RemoteViews(context.getPackageName(), R.layout.widget_note);
                NoteWidgetProvider.applySyncState(changes, store, store.getNote(gistId), gistId,
                        NoteWidgetProvider.mutedColor(appearance));
                if (NoteWidgetProvider.usesInlineCollections()) {
                    NoteWidgetProvider.configureLineCollection(context, changes, id);
                }
                manager.partiallyUpdateAppWidget(id, changes);
                if (!NoteWidgetProvider.usesInlineCollections()) {
                    manager.notifyAppWidgetViewDataChanged(id, R.id.widget_content_list);
                }
            }
        }
    }

    public static void notifyAllChanged(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StackNoteWidgetProvider.class));
        for (int id : ids) update(context, manager, id);
    }

    private static void syncOne(Context context, Intent intent,
                                BroadcastReceiver.PendingResult result) {
        Context appContext = context.getApplicationContext();
        int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        String gistId = intent.getStringExtra(NoteActivity.EXTRA_GIST_ID);
        String filename = intent.getStringExtra(NoteActivity.EXTRA_FILENAME);
        if (NoteStore.isLocalId(gistId)) {
            Toast.makeText(appContext, "Local note is saved on this device",
                    Toast.LENGTH_SHORT).show();
            NoteWidgetProvider.updateWidgetsForGist(appContext, gistId);
            result.finish();
            return;
        }
        Toast.makeText(appContext, "Syncing note…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String token = new TokenStore(appContext).getToken();
                if (token.isEmpty()) throw new IllegalStateException("Missing GitHub token");
                NoteStore store = new NoteStore(appContext);
                SyncEngine engine = new SyncEngine(new GitHubApi(token), store);
                NoteStore.CachedNote note = store.getNote(gistId);
                if (note != null && note.dirty) engine.save(note, note.content);
                else engine.refresh(gistId, filename);
                store.setConflict(gistId, false);
                NoteWidgetProvider.updateWidgetsForGist(appContext, gistId);
            } catch (SyncEngine.ConflictException conflict) {
                new NoteStore(appContext).setConflict(gistId, true);
                NoteWidgetProvider.updateWidgetsForGist(appContext, gistId);
                new android.os.Handler(appContext.getMainLooper()).post(() ->
                        Toast.makeText(appContext, "Sync conflict · open preview to resolve",
                                Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                new android.os.Handler(appContext.getMainLooper()).post(() ->
                        Toast.makeText(appContext, "Could not sync this note", Toast.LENGTH_SHORT).show());
            } finally {
                result.finish();
            }
        }, "gistnote-stack-sync").start();
    }

    @Override public void onDeleted(Context context, int[] ids) {
        NoteStore store = new NoteStore(context);
        for (int id : ids) store.removeStack(id);
    }

    public static List<String> paginate(String content, int maxLines) {
        List<String> pages = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            pages.add("");
            return pages;
        }
        String[] lines = content.split("\n", -1);
        StringBuilder current = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (count >= maxLines && current.length() > 0) {
                pages.add(current.toString().trim());
                current = new StringBuilder();
                count = 0;
            }
            if (current.length() > 0) current.append('\n');
            current.append(line);
            count++;
        }
        if (current.length() > 0) {
            pages.add(current.toString().trim());
        }
        if (pages.isEmpty()) pages.add("");
        return pages;
    }
}
